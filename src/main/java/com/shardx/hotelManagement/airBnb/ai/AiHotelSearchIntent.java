package com.shardx.hotelManagement.airBnb.ai;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Strict data-only model output. It is never treated as inventory, pricing, or a booking decision.
 * Null means the guest did not state the field clearly enough.
 */
@Schema(description = "Structured filters extracted from a guest's message; not a hotel result")
public record AiHotelSearchIntent(
        String city,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer rooms,
        Integer guestsPerRoom,
        Set<Amenity> amenities,
        BigDecimal budgetInr,
        BudgetScope budgetScope
) {
}
