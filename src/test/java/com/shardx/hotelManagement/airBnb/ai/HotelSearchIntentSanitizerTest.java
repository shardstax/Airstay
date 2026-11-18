package com.shardx.hotelManagement.airBnb.ai;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HotelSearchIntentSanitizerTest {

    private final HotelSearchIntentSanitizer sanitizer = new HotelSearchIntentSanitizer(
            Clock.fixed(Instant.parse("2026-11-18T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void canonicalizesCommonCityNamesWithoutTreatingTheModelAsTruth() {
        AiHotelSearchInterpretation result = sanitizer.sanitize(new AiHotelSearchIntent(
                "  Bombay ", LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 21), 2, 2,
                Set.of(Amenity.BREAKFAST, Amenity.SPA), new BigDecimal("12000"), BudgetScope.TOTAL_STAY));

        assertThat(result.intent().city()).isEqualTo("Mumbai");
        assertThat(result.missingFields()).isEmpty();
        assertThat(result.unsupportedFilters()).containsExactlyInAnyOrder("guestsPerRoom", "amenities", "budgetInr");
        assertThat(result.readyForBasicSearch()).isTrue();
    }

    @Test
    void rejectsPastOrInvalidModelValuesAndAsksForTheRequiredFields() {
        AiHotelSearchInterpretation result = sanitizer.sanitize(new AiHotelSearchIntent(
                "Pune", LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 17), 99, 0,
                null, new BigDecimal("-1"), BudgetScope.TOTAL_STAY));

        assertThat(result.intent().checkIn()).isNull();
        assertThat(result.intent().checkOut()).isNull();
        assertThat(result.intent().rooms()).isNull();
        assertThat(result.intent().budgetInr()).isNull();
        assertThat(result.missingFields()).containsExactly("checkIn", "checkOut", "rooms");
        assertThat(result.readyForBasicSearch()).isFalse();
    }

    @Test
    void requiresTheBudgetBasisBeforeCallingAResponseComplete() {
        AiHotelSearchInterpretation result = sanitizer.sanitize(new AiHotelSearchIntent(
                "Bangalore", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22), 1, null,
                Set.of(), new BigDecimal("4000"), null));

        assertThat(result.intent().city()).isEqualTo("Bengaluru");
        assertThat(result.missingFields()).containsExactly("budgetScope");
        assertThat(result.unsupportedFilters()).containsExactly("budgetInr");
        assertThat(result.readyForBasicSearch()).isFalse();
    }
}
