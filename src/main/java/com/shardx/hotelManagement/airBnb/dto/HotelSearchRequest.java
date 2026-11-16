package com.shardx.hotelManagement.airBnb.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Bound from query parameters, e.g. GET /hotels/search?city=Pune&startDate=2026-12-20&endDate=2026-12-22 */
@Data
public class HotelSearchRequest {

    @NotBlank
    private String city;

    /** Check-in date (first night). */
    @NotNull
    @FutureOrPresent
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    /** Check-out date (departure day, not a night of the stay). */
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @NotNull
    @Min(1)
    @Max(10)
    private Integer roomsCount = 1;

    @NotNull
    @Min(0)
    private Integer page = 0;

    @NotNull
    @Min(1)
    @Max(50)
    private Integer size = 10;

    @JsonIgnore
    @AssertTrue(message = "endDate (check-out) must be after startDate (check-in)")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || endDate.isAfter(startDate);
    }
}
