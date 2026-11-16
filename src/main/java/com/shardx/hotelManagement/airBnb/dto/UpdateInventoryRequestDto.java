package com.shardx.hotelManagement.airBnb.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Applies to every night from startDate to endDate, both inclusive. */
@Data
public class UpdateInventoryRequestDto {

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotNull
    @DecimalMin("0.50")
    @DecimalMax("5.00")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal surgeFactor;

    @NotNull
    private Boolean closed;

    @JsonIgnore
    @AssertTrue(message = "endDate must not be before startDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
