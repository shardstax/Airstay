package com.shardx.hotelManagement.airBnb.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Bound from query parameters. startDate is check-in, endDate is check-out. */
@Data
public class HotelInfoRequestDto {

    @NotNull
    @FutureOrPresent
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @NotNull
    @Min(1)
    @Max(10)
    private Long roomsCount = 1L;

    @JsonIgnore
    @AssertTrue(message = "endDate (check-out) must be after startDate (check-in)")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || endDate.isAfter(startDate);
    }
}
