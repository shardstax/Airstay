package com.shardx.hotelManagement.airBnb.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BookingRequest {

    @NotNull
    private Long hotelId;

    @NotNull
    private Long roomId;

    /** First night of the stay. */
    @NotNull
    @FutureOrPresent
    private LocalDate checkInDate;

    /** Departure day; the guest does not stay this night. */
    @NotNull
    private LocalDate checkOutDate;

    @NotNull
    @Min(1)
    @Max(10)
    private Integer roomsCount;

    @JsonIgnore
    @AssertTrue(message = "checkOutDate must be after checkInDate")
    public boolean isDateRangeValid() {
        return checkInDate == null || checkOutDate == null || checkOutDate.isAfter(checkInDate);
    }
}
