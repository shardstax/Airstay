package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.dto.BookingDto;
import com.shardx.hotelManagement.airBnb.dto.BookingRequest;
import com.shardx.hotelManagement.airBnb.dto.HotelReportDto;
import com.shardx.hotelManagement.airBnb.entity.*;
import com.shardx.hotelManagement.airBnb.entity.enums.BookingStatus;
import com.shardx.hotelManagement.airBnb.exception.BadRequestException;
import com.shardx.hotelManagement.airBnb.exception.ConflictException;
import com.shardx.hotelManagement.airBnb.exception.ResourceNotFoundException;
import com.shardx.hotelManagement.airBnb.exception.UnAuthorisedException;
import com.shardx.hotelManagement.airBnb.repository.*;
import com.shardx.hotelManagement.airBnb.strategy.PricingService;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.shardx.hotelManagement.airBnb.util.AppUtils.getCurrentUser;

/**
 * Booking lifecycle. Every state change row-locks the booking first, and every inventory change
 * row-locks the affected nights, so concurrent requests, Stripe webhooks and the expiry job
 * cannot interleave.
 *
 * Dates: checkInDate is the first night, checkOutDate is the departure day (not a night).
 * A stay therefore occupies inventory nights [checkInDate, checkOutDate - 1].
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final GuestRepository guestRepository;
    private final ModelMapper modelMapper;
    private final BookingRepository bookingRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final InventoryRepository inventoryRepository;
    private final CheckoutService checkoutService;
    private final PricingService pricingService;
    private final Clock clock;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Value("${airstay.booking.reservation-hold-minutes}")
    private long reservationHoldMinutes;

    @Value("${airstay.booking.payment-hold-minutes}")
    private long paymentHoldMinutes;

    @Value("${airstay.booking.stripe-session-minutes}")
    private long stripeSessionMinutes;

    @Value("${airstay.booking.max-nights}")
    private long maxNights;

    @Override
    @Transactional
    public BookingInitResult initialiseBooking(BookingRequest bookingRequest, String idempotencyKey) {
        User user = getCurrentUser();
        IdempotencyKey claimedKey = null;
        if (idempotencyKey != null) {
            String requestFingerprint = fingerprint(bookingRequest);
            if (idempotencyKeyRepository.claim(user.getId(), idempotencyKey, requestFingerprint, now()) == 0) {
                return replay(user, idempotencyKey, requestFingerprint);
            }
            claimedKey = idempotencyKeyRepository.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotency key vanished right after it was claimed"));
        }

        BookingDto booking = createBooking(bookingRequest);

        if (claimedKey != null) {
            // Same transaction as the booking: the key and the booking are saved together or not at all. If creating
            // the booking failed above (sold out, ...), this transaction rolls back and the key is free to be retried.
            claimedKey.setBookingId(booking.getId());
        }
        return new BookingInitResult(booking, false);
    }

    private BookingDto createBooking(BookingRequest bookingRequest) {
        log.info("Initialising booking for hotel: {}, room: {}, stay {} to {}", bookingRequest.getHotelId(),
                bookingRequest.getRoomId(), bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate());

        long nights = ChronoUnit.DAYS.between(bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate());
        if (nights > maxNights) {
            throw new BadRequestException("A booking can be at most " + maxNights + " nights");
        }

        Hotel hotel = hotelRepository.findById(bookingRequest.getHotelId()).orElseThrow(() ->
                new ResourceNotFoundException("Hotel not found with id: " + bookingRequest.getHotelId()));
        if (!Boolean.TRUE.equals(hotel.getActive())) {
            throw new ConflictException("Hotel is not accepting bookings");
        }
        Room room = roomRepository.findById(bookingRequest.getRoomId()).orElseThrow(() ->
                new ResourceNotFoundException("Room not found with id: " + bookingRequest.getRoomId()));
        if (!room.getHotel().getId().equals(hotel.getId())) {
            throw new BadRequestException("Room " + room.getId() + " does not belong to hotel " + hotel.getId());
        }

        LocalDate lastNight = bookingRequest.getCheckOutDate().minusDays(1);
        int roomsCount = bookingRequest.getRoomsCount();

        List<Inventory> inventoryList = inventoryRepository.findAndLockAvailableInventory(room.getId(),
                bookingRequest.getCheckInDate(), lastNight, roomsCount);
        if (inventoryList.size() != nights) {
            throw new ConflictException("Room is not available for the selected dates");
        }

        int reservedNights = inventoryRepository.reserveRooms(room.getId(), bookingRequest.getCheckInDate(),
                lastNight, roomsCount);
        if (reservedNights != nights) {
            // Cannot happen while the rows are locked; fail loudly and roll back if it ever does.
            throw new IllegalStateException("Reserved " + reservedNights + " of " + nights + " nights");
        }

        BigDecimal priceForOneRoom = pricingService.calculateTotalPrice(inventoryList);
        BigDecimal totalPrice = priceForOneRoom.multiply(BigDecimal.valueOf(roomsCount));

        Booking booking = Booking.builder()
                .bookingStatus(BookingStatus.RESERVED)
                .hotel(hotel)
                .room(room)
                .checkInDate(bookingRequest.getCheckInDate())
                .checkOutDate(bookingRequest.getCheckOutDate())
                .user(getCurrentUser())
                .roomsCount(roomsCount)
                .amount(totalPrice)
                .expiresAt(now().plusMinutes(reservationHoldMinutes))
                .build();

        booking = bookingRepository.save(booking);
        return toDto(booking);
    }

    @Override
    @Transactional
    public BookingDto addGuests(Long bookingId, List<Long> guestIdList) {
        log.info("Adding guests for booking with id: {}", bookingId);
        if (guestIdList == null || guestIdList.isEmpty()) {
            throw new BadRequestException("At least one guest id is required");
        }

        Booking booking = lockOwnedBooking(bookingId);
        ensureHoldIsActive(booking);
        if (booking.getBookingStatus() != BookingStatus.RESERVED) {
            throw new ConflictException("Booking is not under reserved state, cannot add guests");
        }

        User user = getCurrentUser();
        Set<Long> uniqueGuestIds = new LinkedHashSet<>(guestIdList);
        int maxGuests = booking.getRoom().getCapacity() * booking.getRoomsCount();
        if (booking.getGuests().size() + uniqueGuestIds.size() > maxGuests) {
            throw new BadRequestException("This booking allows at most " + maxGuests + " guests");
        }

        for (Long guestId : uniqueGuestIds) {
            Guest guest = guestRepository.findById(guestId)
                    .orElseThrow(() -> new ResourceNotFoundException("Guest not found with id: " + guestId));
            if (!user.equals(guest.getUser())) {
                throw new UnAuthorisedException("Guest " + guestId + " does not belong to this user");
            }
            booking.getGuests().add(guest);
        }

        booking.setBookingStatus(BookingStatus.GUESTS_ADDED);
        booking = bookingRepository.save(booking);
        return toDto(booking);
    }

    @Override
    @Transactional
    public String initiatePayments(Long bookingId) {
        Booking booking = lockOwnedBooking(bookingId);
        ensureHoldIsActive(booking);

        // A retry (double click, page refresh) reuses the open Stripe session instead of creating another one.
        if (booking.getBookingStatus() == BookingStatus.PAYMENTS_PENDING && booking.getPaymentSessionId() != null) {
            String openSessionUrl = checkoutService.getOpenSessionUrl(booking.getPaymentSessionId());
            if (openSessionUrl != null) {
                return openSessionUrl;
            }
            throw new ConflictException("Payment session is no longer open");
        }
        if (booking.getBookingStatus() != BookingStatus.GUESTS_ADDED) {
            throw new ConflictException("Add guests to the booking before starting payment");
        }

        // The Stripe session closes before our hold does, so a guest can never pay for rooms we released.
        Instant sessionExpiresAt = clock.instant().plus(Duration.ofMinutes(stripeSessionMinutes));
        String statusUrl = frontendUrl + "/payments/" + bookingId + "/status";
        CheckoutService.CheckoutSession session = checkoutService.createCheckoutSession(
                booking, getCurrentUser().getEmail(), statusUrl, statusUrl, sessionExpiresAt);

        booking.setPaymentSessionId(session.id());
        booking.setBookingStatus(BookingStatus.PAYMENTS_PENDING);
        booking.setExpiresAt(now().plusMinutes(paymentHoldMinutes));
        bookingRepository.save(booking);
        return session.url();
    }

    /**
     * Stripe webhook handler. Stripe delivers at least once, so every branch is idempotent:
     * the booking row is locked and only a PAYMENTS_PENDING booking can move to CONFIRMED.
     */
    @Override
    @Transactional
    public void capturePayment(Event event) {
        switch (event.getType()) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" ->
                    handleSessionPaid(extractSession(event));
            case "checkout.session.expired", "checkout.session.async_payment_failed" ->
                    handleSessionClosedUnpaid(extractSession(event));
            default -> log.debug("Ignoring Stripe event type: {}", event.getType());
        }
    }

    private void handleSessionPaid(Session session) {
        if (!"paid".equals(session.getPaymentStatus())) {
            log.info("Session {} completed but payment status is {}; waiting for the async result",
                    session.getId(), session.getPaymentStatus());
            return;
        }
        Booking booking = bookingRepository.findByPaymentSessionIdForUpdate(session.getId()).orElse(null);
        if (booking == null) {
            log.warn("Paid Stripe session {} does not match any booking", session.getId());
            return;
        }

        switch (booking.getBookingStatus()) {
            case CONFIRMED -> log.info("Booking {} already confirmed; duplicate webhook ignored", booking.getId());
            case PAYMENTS_PENDING -> {
                lockNights(booking);
                int confirmedNights = inventoryRepository.confirmBooking(booking.getRoom().getId(),
                        booking.getCheckInDate(), booking.getLastNight(), booking.getRoomsCount());
                if (confirmedNights != nights(booking)) {
                    throw new IllegalStateException("Confirmed " + confirmedNights + " of " + nights(booking)
                            + " nights for booking " + booking.getId());
                }
                booking.setBookingStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);
                log.info("Successfully confirmed the booking for Booking ID: {}", booking.getId());
            }
            default -> {
                // Money arrived for a booking that no longer holds rooms (expired or cancelled): give it back.
                log.warn("Payment received for booking {} in status {}; refunding",
                        booking.getId(), booking.getBookingStatus());
                refundAfterCommit(booking);
            }
        }
    }

    private void handleSessionClosedUnpaid(Session session) {
        Booking booking = bookingRepository.findByPaymentSessionIdForUpdate(session.getId()).orElse(null);
        if (booking != null && booking.getBookingStatus() == BookingStatus.PAYMENTS_PENDING) {
            log.info("Payment session {} closed unpaid; releasing booking {}", session.getId(), booking.getId());
            releaseHold(booking);
        }
    }

    @Override
    @Transactional
    public void cancelBooking(Long bookingId) {
        Booking booking = lockOwnedBooking(bookingId);
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Only confirmed bookings can be cancelled");
        }
        if (!LocalDate.now(clock).isBefore(booking.getCheckInDate())) {
            throw new ConflictException("Bookings can only be cancelled before the check-in date");
        }

        lockNights(booking);
        int releasedNights = inventoryRepository.releaseBookedRooms(booking.getRoom().getId(),
                booking.getCheckInDate(), booking.getLastNight(), booking.getRoomsCount());
        if (releasedNights != nights(booking)) {
            throw new IllegalStateException("Released " + releasedNights + " of " + nights(booking)
                    + " nights for booking " + booking.getId());
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        refundAfterCommit(booking);
    }

    @Override
    @Transactional
    public void expireBooking(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || !booking.getBookingStatus().isHold()) {
            return; // confirmed or already released in the meantime
        }
        if (booking.getExpiresAt() == null || booking.getExpiresAt().isAfter(now())) {
            return; // hold was extended (payment started) after the expiry job picked it up
        }
        releaseHold(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingStatus getBookingStatus(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(
                () -> new ResourceNotFoundException("Booking not found with id: " + bookingId)
        );
        User user = getCurrentUser();
        if (!user.equals(booking.getUser())) {
            throw new UnAuthorisedException("Booking does not belong to this user with id: " + user.getId());
        }
        return booking.getBookingStatus();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingDto> getAllBookingsByHotelId(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(() ->
                new ResourceNotFoundException("Hotel not found with ID: " + hotelId));
        User user = getCurrentUser();
        log.info("Getting all booking for the hotel with ID: {}", hotelId);
        if (!user.equals(hotel.getOwner())) {
            throw new AccessDeniedException("You are not the owner of hotel with id: " + hotelId);
        }

        return bookingRepository.findByHotel(hotel).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HotelReportDto getHotelReport(Long hotelId, LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException("endDate must not be before startDate");
        }
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(() ->
                new ResourceNotFoundException("Hotel not found with ID: " + hotelId));
        User user = getCurrentUser();
        log.info("Generating report for hotel with ID: {}", hotelId);
        if (!user.equals(hotel.getOwner())) {
            throw new AccessDeniedException("You are not the owner of hotel with id: " + hotelId);
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Booking> confirmedBookings = bookingRepository
                .findByHotelAndCreatedAtBetween(hotel, startDateTime, endDateTime).stream()
                .filter(booking -> booking.getBookingStatus() == BookingStatus.CONFIRMED)
                .toList();

        long totalConfirmedBookings = confirmedBookings.size();
        BigDecimal totalRevenue = confirmedBookings.stream()
                .map(Booking::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgRevenue = totalConfirmedBookings == 0 ? BigDecimal.ZERO :
                totalRevenue.divide(BigDecimal.valueOf(totalConfirmedBookings), 2, RoundingMode.HALF_UP);

        return new HotelReportDto(totalConfirmedBookings, totalRevenue, avgRevenue);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingDto> getMyBookings() {
        return bookingRepository.findByUser(getCurrentUser()).stream()
                .map(this::toDto)
                .toList();
    }

    // ---------------------------------------------------------------------------------------------

    /** Gives held rooms back to inventory and marks the booking EXPIRED. Caller holds the booking lock. */
    private void releaseHold(Booking booking) {
        lockNights(booking);
        int releasedNights = inventoryRepository.releaseReservation(booking.getRoom().getId(),
                booking.getCheckInDate(), booking.getLastNight(), booking.getRoomsCount());
        if (releasedNights != nights(booking)) {
            log.error("Released {} of {} held nights for booking {}", releasedNights, nights(booking), booking.getId());
        }
        booking.setBookingStatus(BookingStatus.EXPIRED);
        bookingRepository.save(booking);
        log.info("Booking {} expired and its held rooms were released", booking.getId());
    }

    /**
     * Refunds only after the cancellation is committed, so we never refund money for a booking that
     * is still active in our database. If Stripe fails here the error is logged for a manual retry;
     * Phase 4 replaces this with a transactional outbox and automatic retries.
     */
    private void refundAfterCommit(Booking booking) {
        String sessionId = booking.getPaymentSessionId();
        Long bookingId = booking.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    checkoutService.refund(sessionId, bookingId);
                } catch (RuntimeException e) {
                    log.error("REFUND_FAILED for booking {}; needs a manual retry", bookingId, e);
                }
            }
        });
    }

    /**
     * The key was already used by this user: return the booking it created (as it is now), or refuse if this is not
     * the same request. By the time we get here any concurrent first attempt has committed (the claim waits for it).
     */
    private BookingInitResult replay(User user, String idempotencyKey, String requestFingerprint) {
        IdempotencyKey existing = idempotencyKeyRepository.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey)
                .orElseThrow(() -> new ConflictException("Idempotency-Key was just released, please retry"));
        if (!existing.getRequestHash().equals(requestFingerprint)) {
            throw new ConflictException("This Idempotency-Key was already used with a different request");
        }
        if (existing.getBookingId() == null) {
            throw new ConflictException("A request with this Idempotency-Key is still being processed");
        }
        Booking booking = bookingRepository.findById(existing.getBookingId()).orElseThrow(() ->
                new ResourceNotFoundException("Booking not found with id: " + existing.getBookingId()));
        return new BookingInitResult(toDto(booking), true);
    }

    /** SHA-256 of everything that makes one booking request different from another. */
    static String fingerprint(BookingRequest request) {
        String canonical = request.getHotelId() + "|" + request.getRoomId() + "|" + request.getCheckInDate()
                + "|" + request.getCheckOutDate() + "|" + request.getRoomsCount();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private Booking lockOwnedBooking(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElseThrow(
                () -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        User user = getCurrentUser();
        if (!user.equals(booking.getUser())) {
            throw new UnAuthorisedException("Booking does not belong to this user with id: " + user.getId());
        }
        return booking;
    }

    private void ensureHoldIsActive(Booking booking) {
        boolean lapsed = booking.getExpiresAt() != null && booking.getExpiresAt().isBefore(now());
        if (booking.getBookingStatus() == BookingStatus.EXPIRED || (booking.getBookingStatus().isHold() && lapsed)) {
            throw new ConflictException("Booking hold has expired, please start a new booking");
        }
    }

    private void lockNights(Booking booking) {
        inventoryRepository.lockInventoryForRoom(booking.getRoom().getId(),
                booking.getCheckInDate(), booking.getLastNight());
    }

    private static long nights(Booking booking) {
        return ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private BookingDto toDto(Booking booking) {
        return modelMapper.map(booking, BookingDto.class);
    }

    private static Session extractSession(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = deserializer.getObject().orElse(null);
        if (stripeObject == null) {
            // The webhook's API version differs from the SDK's; fall back to best-effort parsing.
            try {
                stripeObject = deserializer.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                throw new IllegalStateException("Cannot read Stripe event " + event.getId(), e);
            }
        }
        if (!(stripeObject instanceof Session session)) {
            throw new IllegalStateException("Stripe event " + event.getId() + " does not contain a checkout session");
        }
        return session;
    }
}
