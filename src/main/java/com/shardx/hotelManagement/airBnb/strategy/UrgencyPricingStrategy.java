package com.shardx.hotelManagement.airBnb.strategy;

import com.shardx.hotelManagement.airBnb.entity.Inventory;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Nights within the next 7 days cost 15% more (last-minute demand). */
@RequiredArgsConstructor
public class UrgencyPricingStrategy implements PricingStrategy {

    private static final BigDecimal URGENCY_MULTIPLIER = new BigDecimal("1.15");
    private static final int URGENCY_WINDOW_DAYS = 7;

    private final PricingStrategy wrapped;
    private final LocalDate today;

    @Override
    public BigDecimal calculatePrice(Inventory inventory) {
        BigDecimal price = wrapped.calculatePrice(inventory);
        LocalDate night = inventory.getDate();
        if (!night.isBefore(today) && night.isBefore(today.plusDays(URGENCY_WINDOW_DAYS))) {
            price = price.multiply(URGENCY_MULTIPLIER);
        }
        return price;
    }
}
