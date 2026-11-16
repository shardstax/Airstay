package com.shardx.hotelManagement.airBnb.strategy;

import com.shardx.hotelManagement.airBnb.entity.Inventory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the pricing chain (decorator pattern): base price, then surge, occupancy, urgency and
 * holiday rules each wrap the previous result. New rules plug in without touching existing ones.
 */
@Service
public class PricingService {

    private final Set<LocalDate> holidays;
    private final Clock clock;

    public PricingService(@Value("${airstay.pricing.holidays:}") String holidays, Clock clock) {
        this.holidays = Arrays.stream(holidays.split(","))
                .map(String::trim)
                .filter(date -> !date.isEmpty())
                .map(LocalDate::parse)
                .collect(Collectors.toUnmodifiableSet());
        this.clock = clock;
    }

    /** Price of one room for the inventory's night, rounded to 2 decimals. */
    public BigDecimal calculateDynamicPricing(Inventory inventory) {
        PricingStrategy pricingStrategy = new BasePricingStrategy();
        pricingStrategy = new SurgePricingStrategy(pricingStrategy);
        pricingStrategy = new OccupancyPricingStrategy(pricingStrategy);
        pricingStrategy = new UrgencyPricingStrategy(pricingStrategy, LocalDate.now(clock));
        pricingStrategy = new HolidayPricingStrategy(pricingStrategy, holidays);
        return pricingStrategy.calculatePrice(inventory).setScale(2, RoundingMode.HALF_UP);
    }

    /** Price of one room for every night in the list. */
    public BigDecimal calculateTotalPrice(List<Inventory> inventoryList) {
        return inventoryList.stream()
                .map(this::calculateDynamicPricing)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
