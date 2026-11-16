package com.shardx.hotelManagement.airBnb.strategy;

import com.shardx.hotelManagement.airBnb.entity.Inventory;
import com.shardx.hotelManagement.airBnb.entity.Room;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final LocalDate FAR_FUTURE = TODAY.plusDays(60);
    private static final LocalDate HOLIDAY = LocalDate.of(2026, 12, 25);
    private static final BigDecimal BASE_PRICE = new BigDecimal("1000.00");

    private final PricingService pricingService = pricingServiceOn(TODAY);

    @Test
    void basePriceAppliesWhenNoRuleMatches() {
        BigDecimal price = pricingService.calculateDynamicPricing(night(FAR_FUTURE, "1.00", 0, 10));

        assertThat(price).isEqualByComparingTo("1000.00");
        assertThat(price.scale()).isEqualTo(2);
    }

    @Test
    void surgeFactorMultipliesTheBasePrice() {
        assertThat(pricingService.calculateDynamicPricing(night(FAR_FUTURE, "1.50", 0, 10)))
                .isEqualByComparingTo("1500.00");
    }

    @ParameterizedTest(name = "{0} of 10 booked -> {1}")
    @CsvSource({
            "8, 1000.00",  // exactly 80%: no surcharge
            "9, 1200.00",  // above 80%: +20%
            "10, 1200.00"
    })
    void occupancyAboveEightyPercentAddsTwentyPercent(int booked, String expected) {
        assertThat(pricingService.calculateDynamicPricing(night(FAR_FUTURE, "1.00", booked, 10)))
                .isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "night in {0} days -> {1}")
    @CsvSource({
            "0, 1150.00",  // tonight
            "6, 1150.00",  // last day of the 7-day window
            "7, 1000.00",  // outside the window
            "-1, 1000.00"  // past nights are never urgent
    })
    void nightsWithinSevenDaysAddFifteenPercent(int daysAhead, String expected) {
        assertThat(pricingService.calculateDynamicPricing(night(TODAY.plusDays(daysAhead), "1.00", 0, 10)))
                .isEqualByComparingTo(expected);
    }

    @Test
    void configuredHolidayAddsTwentyFivePercent() {
        assertThat(pricingService.calculateDynamicPricing(night(HOLIDAY, "1.00", 0, 10)))
                .isEqualByComparingTo("1250.00");
    }

    @Test
    void holidayRuleIsOffWhenNoHolidaysAreConfigured() {
        PricingService noHolidays = new PricingService("", fixedClock(TODAY));

        assertThat(noHolidays.calculateDynamicPricing(night(HOLIDAY, "1.00", 0, 10)))
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void allRulesStackInOrder() {
        // Three days before a holiday, so urgency and holiday both apply.
        PricingService service = pricingServiceOn(HOLIDAY.minusDays(3));

        // 1000 x 1.5 surge x 1.2 occupancy x 1.15 urgency x 1.25 holiday = 2587.50
        assertThat(service.calculateDynamicPricing(night(HOLIDAY, "1.50", 9, 10)))
                .isEqualByComparingTo("2587.50");
    }

    @Test
    void totalPriceIsTheSumOfEveryNight() {
        List<Inventory> stay = List.of(
                night(FAR_FUTURE, "1.00", 0, 10),          // 1000.00
                night(FAR_FUTURE.plusDays(1), "1.50", 0, 10), // 1500.00
                night(HOLIDAY, "1.00", 0, 10)              // 1250.00
        );

        assertThat(pricingService.calculateTotalPrice(stay)).isEqualByComparingTo("3750.00");
    }

    private static PricingService pricingServiceOn(LocalDate today) {
        return new PricingService(HOLIDAY + ", 2027-01-01", fixedClock(today));
    }

    private static Clock fixedClock(LocalDate today) {
        return Clock.fixed(Instant.parse(today + "T10:00:00Z"), ZoneOffset.UTC);
    }

    private static Inventory night(LocalDate date, String surgeFactor, int booked, int total) {
        Room room = new Room();
        room.setBasePrice(BASE_PRICE);
        return Inventory.builder()
                .room(room)
                .date(date)
                .surgeFactor(new BigDecimal(surgeFactor))
                .bookedCount(booked)
                .reservedCount(0)
                .totalCount(total)
                .closed(false)
                .build();
    }
}
