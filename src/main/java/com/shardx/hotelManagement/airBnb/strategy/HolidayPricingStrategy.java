package com.shardx.hotelManagement.airBnb.strategy;

import com.shardx.hotelManagement.airBnb.entity.Inventory;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** Nights that fall on a configured holiday cost 25% more. */
@RequiredArgsConstructor
public class HolidayPricingStrategy implements PricingStrategy {

    private static final BigDecimal HOLIDAY_MULTIPLIER = new BigDecimal("1.25");

    private final PricingStrategy wrapped;
    private final Set<LocalDate> holidays;

    @Override
    public BigDecimal calculatePrice(Inventory inventory) {
        BigDecimal price = wrapped.calculatePrice(inventory);
        if (holidays.contains(inventory.getDate())) {
            price = price.multiply(HOLIDAY_MULTIPLIER);
        }
        return price;
    }
}
