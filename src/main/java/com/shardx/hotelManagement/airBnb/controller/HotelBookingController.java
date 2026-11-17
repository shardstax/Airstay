package com.shardx.hotelManagement.airBnb.controller;

import com.shardx.hotelManagement.airBnb.dto.*;
import com.shardx.hotelManagement.airBnb.ratelimit.RateLimitProperties;
import com.shardx.hotelManagement.airBnb.ratelimit.RateLimiter;
import com.shardx.hotelManagement.airBnb.service.BookingService;
import com.shardx.hotelManagement.airBnb.util.AppUtils;
import com.shardx.hotelManagement.airBnb.exception.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bookings")
public class HotelBookingController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,100}");

    private final BookingService bookingService;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimits;

    @PostMapping("/init")
    @Operation(summary = "Hold rooms for a stay (the hold lasts a few minutes until payment starts)", tags = {"Booking Flow"})
    @ApiResponse(responseCode = "201", description = "Rooms are held")
    public ResponseEntity<BookingDto> initialiseBooking(
            @Valid @RequestBody BookingRequest bookingRequest,
            @Parameter(description = "Optional. Repeat the same value when you retry the same request (timeout, lost "
                    + "response) and you get the original booking back instead of a second one. 8 to 100 characters: "
                    + "letters, digits and . _ : -")
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {
        String validKey = validated(idempotencyKey);
        // Checked before touching the database: a script hammering one room must not queue up on its inventory locks.
        rateLimiter.check("booking-init-user", String.valueOf(AppUtils.getCurrentUser().getId()),
                rateLimits.getBookingInitPerUser());

        BookingService.BookingInitResult result = bookingService.initialiseBooking(bookingRequest, validKey);

        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
        if (result.replayed()) {
            response.header(IDEMPOTENT_REPLAYED_HEADER, "true"); // lets the client tell a replay from a new booking
        }
        return response.body(result.booking());
    }

    private static String validated(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new BadRequestException(
                    "Idempotency-Key must be 8 to 100 characters: letters, digits and . _ : -");
        }
        return idempotencyKey;
    }

    @PostMapping("/{bookingId}/addGuests")
    @Operation(summary = "Add your saved guests to the booking", tags = {"Booking Flow"})
    public ResponseEntity<BookingDto> addGuests(@PathVariable Long bookingId,
                                                @RequestBody List<Long> guestIdList) {
        return ResponseEntity.ok(bookingService.addGuests(bookingId, guestIdList));
    }

    @PostMapping("/{bookingId}/payments")
    @Operation(summary = "Start (or resume) the Stripe checkout for the booking", tags = {"Booking Flow"})
    public ResponseEntity<BookingPaymentInitResponseDto> initiatePayment(@PathVariable Long bookingId) {
        String sessionUrl = bookingService.initiatePayments(bookingId);
        return ResponseEntity.ok(new BookingPaymentInitResponseDto(sessionUrl));
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel a confirmed booking before check-in and refund it", tags = {"Booking Flow"})
    @ApiResponse(responseCode = "204", description = "Cancelled and refund started")
    public ResponseEntity<Void> cancelBooking(@PathVariable Long bookingId) {
        bookingService.cancelBooking(bookingId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{bookingId}/status")
    @Operation(summary = "Check the status of the booking", tags = {"Booking Flow"})
    public ResponseEntity<BookingStatusResponseDto> getBookingStatus(@PathVariable Long bookingId) {
        return ResponseEntity.ok(new BookingStatusResponseDto(bookingService.getBookingStatus(bookingId)));
    }
}
