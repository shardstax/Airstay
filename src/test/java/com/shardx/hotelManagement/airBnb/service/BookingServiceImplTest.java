package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.config.MapperConfig;
import com.shardx.hotelManagement.airBnb.dto.BookingDto;
import com.shardx.hotelManagement.airBnb.dto.BookingRequest;
import com.shardx.hotelManagement.airBnb.entity.*;
import com.shardx.hotelManagement.airBnb.entity.enums.BookingStatus;
import com.shardx.hotelManagement.airBnb.entity.enums.Role;
import com.shardx.hotelManagement.airBnb.exception.BadRequestException;
import com.shardx.hotelManagement.airBnb.exception.ConflictException;
import com.shardx.hotelManagement.airBnb.exception.UnAuthorisedException;
import com.shardx.hotelManagement.airBnb.repository.*;
import com.shardx.hotelManagement.airBnb.strategy.PricingService;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the booking state machine. Repositories and Stripe are mocked; pricing and
 * mapping are real. The clock is fixed at 2026-06-01 10:00 UTC.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 7, 1);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 7, 3); // 2 nights: Jul 1 and Jul 2
    private static final LocalDate LAST_NIGHT = LocalDate.of(2026, 7, 2);
    private static final long ROOM_ID = 20L;
    private static final long BOOKING_ID = 99L;
    private static final String SESSION_ID = "cs_test_1";

    @Mock private GuestRepository guestRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private HotelRepository hotelRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private CheckoutService checkoutService;

    private BookingServiceImpl bookingService;
    private User user;
    private Hotel hotel;
    private Room room;

    @BeforeEach
    void setUp() {
        bookingService = new BookingServiceImpl(guestRepository, new MapperConfig().modelMapper(), bookingRepository,
                hotelRepository, roomRepository, inventoryRepository, checkoutService,
                new PricingService("", CLOCK), CLOCK);
        ReflectionTestUtils.setField(bookingService, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(bookingService, "reservationHoldMinutes", 10L);
        ReflectionTestUtils.setField(bookingService, "paymentHoldMinutes", 35L);
        ReflectionTestUtils.setField(bookingService, "stripeSessionMinutes", 31L);
        ReflectionTestUtils.setField(bookingService, "maxNights", 30L);

        user = user(1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));

        hotel = new Hotel();
        hotel.setId(10L);
        hotel.setName("Sea View");
        hotel.setCity("Goa");
        hotel.setActive(true);

        room = new Room();
        room.setId(ROOM_ID);
        room.setHotel(hotel);
        room.setType("Deluxe");
        room.setBasePrice(new BigDecimal("1000.00"));
        room.setCapacity(2);
        room.setTotalCount(5);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Nested
    class InitialiseBooking {

        @Test
        void holdsEveryNightOfTheStayAndPricesIt() {
            when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel));
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(inventoryRepository.findAndLockAvailableInventory(ROOM_ID, CHECK_IN, LAST_NIGHT, 2))
                    .thenReturn(List.of(night(CHECK_IN), night(LAST_NIGHT)));
            when(inventoryRepository.reserveRooms(ROOM_ID, CHECK_IN, LAST_NIGHT, 2)).thenReturn(2);
            when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
                Booking saved = invocation.getArgument(0);
                saved.setId(BOOKING_ID);
                return saved;
            });

            BookingDto result = bookingService.initialiseBooking(request(2));

            // 2 nights x 1000 x 2 rooms; the check-out day is not charged
            assertThat(result.getAmount()).isEqualByComparingTo("4000.00");
            assertThat(result.getBookingStatus()).isEqualTo(BookingStatus.RESERVED);
            assertThat(result.getExpiresAt()).isEqualTo(NOW.plusMinutes(10));
            assertThat(result.getHotelId()).isEqualTo(10L);
            assertThat(result.getRoomId()).isEqualTo(ROOM_ID);
        }

        @Test
        void rejectsTheStayWhenAnyNightIsSoldOut() {
            when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel));
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(inventoryRepository.findAndLockAvailableInventory(ROOM_ID, CHECK_IN, LAST_NIGHT, 2))
                    .thenReturn(List.of(night(CHECK_IN))); // only 1 of 2 nights free

            assertThatThrownBy(() -> bookingService.initialiseBooking(request(2)))
                    .isInstanceOf(ConflictException.class);
            verify(inventoryRepository, never()).reserveRooms(anyLong(), any(), any(), anyInt());
            verify(bookingRepository, never()).save(any());
        }

        @Test
        void rejectsARoomFromAnotherHotel() {
            Hotel otherHotel = new Hotel();
            otherHotel.setId(11L);
            room.setHotel(otherHotel);
            when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel));
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> bookingService.initialiseBooking(request(1)))
                    .isInstanceOf(BadRequestException.class);
            verifyNoInteractions(inventoryRepository);
        }

        @Test
        void rejectsAnInactiveHotel() {
            hotel.setActive(false);
            when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel));

            assertThatThrownBy(() -> bookingService.initialiseBooking(request(1)))
                    .isInstanceOf(ConflictException.class);
            verifyNoInteractions(inventoryRepository);
        }
    }

    @Nested
    class AddGuests {

        @Test
        void rejectsAGuestThatBelongsToAnotherUser() {
            Booking booking = booking(BookingStatus.RESERVED, NOW.plusMinutes(5));
            when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
            Guest strangersGuest = new Guest();
            strangersGuest.setId(5L);
            strangersGuest.setUser(user(2L));
            when(guestRepository.findById(5L)).thenReturn(Optional.of(strangersGuest));

            assertThatThrownBy(() -> bookingService.addGuests(BOOKING_ID, List.of(5L)))
                    .isInstanceOf(UnAuthorisedException.class);
            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.RESERVED);
        }

        @Test
        void rejectsALapsedHold() {
            Booking booking = booking(BookingStatus.RESERVED, NOW.minusMinutes(1));
            when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.addGuests(BOOKING_ID, List.of(5L)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        void rejectsMoreGuestsThanTheRoomsHold() {
            Booking booking = booking(BookingStatus.RESERVED, NOW.plusMinutes(5));
            booking.setRoomsCount(1); // capacity 2 x 1 room
            when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.addGuests(BOOKING_ID, List.of(1L, 2L, 3L)))
                    .isInstanceOf(BadRequestException.class);
            verifyNoInteractions(guestRepository);
        }
    }

    @Nested
    class InitiatePayments {

        @Test
        void requiresGuestsBeforePayment() {
            when(bookingRepository.findByIdForUpdate(BOOKING_ID))
                    .thenReturn(Optional.of(booking(BookingStatus.RESERVED, NOW.plusMinutes(5))));

            assertThatThrownBy(() -> bookingService.initiatePayments(BOOKING_ID))
                    .isInstanceOf(ConflictException.class);
            verifyNoInteractions(checkoutService);
        }

        @Test
        void startsCheckoutThatClosesBeforeTheExtendedHold() {
            Booking booking = booking(BookingStatus.GUESTS_ADDED, NOW.plusMinutes(5));
            booking.setPaymentSessionId(null);
            when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
            Instant sessionExpiry = CLOCK.instant().plus(Duration.ofMinutes(31));
            when(checkoutService.createCheckoutSession(eq(booking), eq(user.getEmail()), anyString(), anyString(),
                    eq(sessionExpiry))).thenReturn(new CheckoutService.CheckoutSession(SESSION_ID, "https://pay.test"));

            String url = bookingService.initiatePayments(BOOKING_ID);

            assertThat(url).isEqualTo("https://pay.test");
            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PAYMENTS_PENDING);
            assertThat(booking.getPaymentSessionId()).isEqualTo(SESSION_ID);
            assertThat(booking.getExpiresAt()).isEqualTo(NOW.plusMinutes(35));
        }

        @Test
        void reusesTheOpenSessionOnRetry() {
            when(bookingRepository.findByIdForUpdate(BOOKING_ID))
                    .thenReturn(Optional.of(booking(BookingStatus.PAYMENTS_PENDING, NOW.plusMinutes(20))));
            when(checkoutService.getOpenSessionUrl(SESSION_ID)).thenReturn("https://pay.test");

            assertThat(bookingService.initiatePayments(BOOKING_ID)).isEqualTo("https://pay.test");
            verify(checkoutService, never()).createCheckoutSession(any(), any(), any(), any(), any());
        }
    }

    @Nested
    class StripeWebhook {

        @Test
        void confirmsAPendingBookingAndMovesHeldRoomsToBooked() {
            Booking booking = booking(BookingStatus.PAYMENTS_PENDING, NOW.plusMinutes(20));
            when(bookingRepository.findByPaymentSessionIdForUpdate(SESSION_ID)).thenReturn(Optional.of(booking));
            when(inventoryRepository.confirmBooking(ROOM_ID, CHECK_IN, LAST_NIGHT, 2)).thenReturn(2);

            bookingService.capturePayment(event("checkout.session.completed", "paid"));

            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void ignoresARepeatedEventForAConfirmedBooking() {
            Booking booking = booking(BookingStatus.CONFIRMED, NOW.plusMinutes(20));
            when(bookingRepository.findByPaymentSessionIdForUpdate(SESSION_ID)).thenReturn(Optional.of(booking));

            bookingService.capturePayment(event("checkout.session.completed", "paid"));

            verify(inventoryRepository, never()).confirmBooking(anyLong(), any(), any(), anyInt());
            verify(bookingRepository, never()).save(any());
            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void waitsWhenTheSessionIsCompleteButNotYetPaid() {
            bookingService.capturePayment(event("checkout.session.completed", "unpaid"));

            verifyNoInteractions(bookingRepository, inventoryRepository);
        }

        @Test
        void refundsPaymentForAnExpiredBookingOnlyAfterCommit() {
            TransactionSynchronizationManager.initSynchronization();
            Booking booking = booking(BookingStatus.EXPIRED, NOW.minusMinutes(1));
            when(bookingRepository.findByPaymentSessionIdForUpdate(SESSION_ID)).thenReturn(Optional.of(booking));

            bookingService.capturePayment(event("checkout.session.completed", "paid"));

            verify(checkoutService, never()).refund(any(), any()); // nothing before commit
            runAfterCommitCallbacks();
            verify(checkoutService).refund(SESSION_ID, BOOKING_ID);
            verify(inventoryRepository, never()).confirmBooking(anyLong(), any(), any(), anyInt());
        }

        @Test
        void releasesHeldRoomsWhenTheSessionExpiresUnpaid() {
            Booking booking = booking(BookingStatus.PAYMENTS_PENDING, NOW.plusMinutes(4));
            when(bookingRepository.findByPaymentSessionIdForUpdate(SESSION_ID)).thenReturn(Optional.of(booking));
            when(inventoryRepository.releaseReservation(ROOM_ID, CHECK_IN, LAST_NIGHT, 2)).thenReturn(2);

            bookingService.capturePayment(event("checkout.session.expired", "unpaid"));

            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.EXPIRED);
        }
    }

    @Nested
    class Expiry {

        @Test
        void releasesALapsedHold() {
            Booking booking = booking(BookingStatus.RESERVED, NOW.minusMinutes(1));
            when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
            when(inventoryRepository.releaseReservation(ROOM_ID, CHECK_IN, LAST_NIGHT, 2)).thenReturn(2);

            bookingService.expireBooking(BOOKING_ID);

            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.EXPIRED);
        }

        @Test
        void skipsAHoldThatWasExtendedWhenPaymentStarted() {
            Booking booking = booking(BookingStatus.PAYMENTS_PENDING, NOW.plusMinutes(30));
            when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));

            bookingService.expireBooking(BOOKING_ID);

            verify(inventoryRepository, never()).releaseReservation(anyLong(), any(), any(), anyInt());
            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PAYMENTS_PENDING);
        }

        @Test
        void neverTouchesAConfirmedBooking() {
            when(bookingRepository.findByIdForUpdate(BOOKING_ID))
                    .thenReturn(Optional.of(booking(BookingStatus.CONFIRMED, NOW.minusMinutes(60))));

            bookingService.expireBooking(BOOKING_ID);

            verifyNoInteractions(inventoryRepository);
        }
    }

    @Nested
    class Cancellation {

        @Test
        void releasesBookedRoomsAndRefundsAfterCommit() {
            TransactionSynchronizationManager.initSynchronization();
            Booking booking = booking(BookingStatus.CONFIRMED, null);
            when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
            when(inventoryRepository.releaseBookedRooms(ROOM_ID, CHECK_IN, LAST_NIGHT, 2)).thenReturn(2);

            bookingService.cancelBooking(BOOKING_ID);

            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
            verify(checkoutService, never()).refund(any(), any());
            runAfterCommitCallbacks();
            verify(checkoutService).refund(SESSION_ID, BOOKING_ID);
        }

        @Test
        void isRefusedOnOrAfterTheCheckInDate() {
            Booking booking = booking(BookingStatus.CONFIRMED, null);
            booking.setCheckInDate(LocalDate.now(CLOCK));
            when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.cancelBooking(BOOKING_ID))
                    .isInstanceOf(ConflictException.class);
            verifyNoInteractions(inventoryRepository, checkoutService);
        }

        @Test
        void isRefusedForSomeoneElsesBooking() {
            Booking booking = booking(BookingStatus.CONFIRMED, null);
            booking.setUser(user(2L));
            when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.cancelBooking(BOOKING_ID))
                    .isInstanceOf(UnAuthorisedException.class);
            verifyNoInteractions(inventoryRepository, checkoutService);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@example.com");
        u.setRoles(Set.of(Role.GUEST));
        return u;
    }

    private static BookingRequest request(int roomsCount) {
        BookingRequest request = new BookingRequest();
        request.setHotelId(10L);
        request.setRoomId(ROOM_ID);
        request.setCheckInDate(CHECK_IN);
        request.setCheckOutDate(CHECK_OUT);
        request.setRoomsCount(roomsCount);
        return request;
    }

    private Booking booking(BookingStatus status, LocalDateTime expiresAt) {
        return Booking.builder()
                .id(BOOKING_ID)
                .hotel(hotel)
                .room(room)
                .user(user)
                .roomsCount(2)
                .checkInDate(CHECK_IN)
                .checkOutDate(CHECK_OUT)
                .bookingStatus(status)
                .amount(new BigDecimal("4000.00"))
                .paymentSessionId(SESSION_ID)
                .expiresAt(expiresAt)
                .build();
    }

    private Inventory night(LocalDate date) {
        return Inventory.builder()
                .hotel(hotel)
                .room(room)
                .date(date)
                .surgeFactor(BigDecimal.ONE)
                .bookedCount(0)
                .reservedCount(0)
                .totalCount(5)
                .price(room.getBasePrice())
                .city("Goa")
                .closed(false)
                .build();
    }

    private static Event event(String type, String paymentStatus) {
        Session session = mock(Session.class);
        lenient().when(session.getId()).thenReturn(SESSION_ID);
        lenient().when(session.getPaymentStatus()).thenReturn(paymentStatus);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(session));
        Event event = mock(Event.class);
        when(event.getType()).thenReturn(type);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }

    private static void runAfterCommitCallbacks() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }
}
