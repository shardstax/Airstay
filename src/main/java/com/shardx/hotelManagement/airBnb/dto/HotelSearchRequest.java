package com.shardx.hotelManagement.airBnb.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Parameter(description = "Rooms needed", required = false, schema = @Schema(defaultValue = "1"))
    @Min(1)
    @Max(10)
    private Integer roomsCount = 1;

    @Parameter(description = "Zero-based page number", required = false, schema = @Schema(defaultValue = "0"))
    @Min(0)
    private Integer page = 0;

    @Parameter(description = "Page size (max 50)", required = false, schema = @Schema(defaultValue = "10"))
    @Min(1)
    @Max(50)
    private Integer size = 10;

    // Empty or missing values fall back to the defaults instead of failing.
    public Integer getRoomsCount() {
        return roomsCount == null ? 1 : roomsCount;
    }

    public Integer getPage() {
        return page == null ? 0 : page;
    }

    public Integer getSize() {
        return size == null ? 10 : size;
    }

    @JsonIgnore
    @AssertTrue(message = "endDate (check-out) must be after startDate (check-in)")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || endDate.isAfter(startDate);
    }
}
