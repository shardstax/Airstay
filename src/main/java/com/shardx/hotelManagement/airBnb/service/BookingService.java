package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.dto.BookingDto;
import com.shardx.hotelManagement.airBnb.dto.BookingRequest;
import com.shardx.hotelManagement.airBnb.dto.HotelReportDto;
import com.shardx.hotelManagement.airBnb.entity.enums.BookingStatus;
import com.stripe.model.Event;

import java.time.LocalDate;
import java.util.List;

public interface BookingService {

    /** The booking, and whether it is an earlier booking returned again because the Idempotency-Key was repeated. */
    record BookingInitResult(BookingDto booking, boolean replayed) {
    }

    /**
     * Holds rooms for a stay. With an idempotency key (may be null) a repeated request returns the booking the first
     * one created instead of booking again.
     */
    BookingInitResult initialiseBooking(BookingRequest bookingRequest, String idempotencyKey);

    BookingDto addGuests(Long bookingId, List<Long> guestIdList);

    String initiatePayments(Long bookingId);

    void capturePayment(Event event);

    void cancelBooking(Long bookingId);

    BookingStatus getBookingStatus(Long bookingId);

    List<BookingDto> getAllBookingsByHotelId(Long hotelId);

    HotelReportDto getHotelReport(Long hotelId, LocalDate startDate, LocalDate endDate);

    List<BookingDto> getMyBookings();

    /** Releases the held rooms of an unpaid booking whose hold has lapsed. No-op otherwise. */
    void expireBooking(Long bookingId);
}
