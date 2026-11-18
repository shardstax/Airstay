package com.shardx.hotelManagement.airBnb.ai;

import com.shardx.hotelManagement.airBnb.repository.HotelRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Business validation after model parsing. Model output is data, never authority. */
@Component
public class HotelSearchIntentSanitizer {

    private static final Map<String, String> CITY_ALIASES = Map.ofEntries(
            Map.entry("bangalore", "Bengaluru"),
            Map.entry("bengaluru", "Bengaluru"),
            Map.entry("bombay", "Mumbai"),
            Map.entry("mumbai", "Mumbai"),
            Map.entry("pune", "Pune"),
            Map.entry("hyderabad", "Hyderabad"),
            Map.entry("delhi", "Delhi"),
            Map.entry("new delhi", "Delhi"),
            Map.entry("nagpur", "Nagpur")
    );

    private final Clock clock;
    private final HotelRepository hotels;
    private final long maxNights;

    public HotelSearchIntentSanitizer(Clock clock, HotelRepository hotels,
                                     @Value("${airstay.booking.max-nights}") long maxNights) {
        this.clock = clock;
        this.hotels = hotels;
        this.maxNights = maxNights;
    }

    public AiHotelSearchInterpretation sanitize(AiHotelSearchIntent raw) {
        String city = canonicalCity(raw.city());
        LocalDate checkIn = usableCheckIn(raw.checkIn());
        LocalDate checkOut = usableCheckOut(checkIn, raw.checkOut());
        Integer rooms = inRange(raw.rooms(), 1, 10) ? raw.rooms() : null;
        Integer guestsPerRoom = inRange(raw.guestsPerRoom(), 1, 10) ? raw.guestsPerRoom() : null;
        Set<Amenity> amenities = raw.amenities() == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(raw.amenities()));
        BigDecimal budget = usableBudget(raw.budgetInr());
        BudgetScope budgetScope = budget == null ? null : raw.budgetScope();

        AiHotelSearchIntent intent = new AiHotelSearchIntent(city, checkIn, checkOut, rooms, guestsPerRoom,
                amenities, budget, budgetScope);
        List<String> missing = new ArrayList<>();
        if (city == null) missing.add("city");
        if (checkIn == null) missing.add("checkIn");
        if (checkOut == null) missing.add("checkOut");
        if (rooms == null) missing.add("rooms");
        if (budget != null && budgetScope == null) missing.add("budgetScope");

        List<String> unsupported = new ArrayList<>();
        if (guestsPerRoom != null) unsupported.add("guestsPerRoom");
        if (!amenities.isEmpty()) unsupported.add("amenities");
        if (budget != null) unsupported.add("budgetInr");

        return new AiHotelSearchInterpretation(intent, List.copyOf(missing), List.copyOf(unsupported), missing.isEmpty());
    }

    private String canonicalCity(String rawCity) {
        if (rawCity == null || rawCity.isBlank() || rawCity.length() > 100) return null;
        String normalized = rawCity.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String name = CITY_ALIASES.getOrDefault(normalized, rawCity.trim());
        List<String> labels = hotels.findActiveCityLabels(name.toLowerCase(Locale.ROOT));
        if (labels.size() == 1) return labels.getFirst();
        return labels.isEmpty() ? name : null;
    }

    private LocalDate usableCheckIn(LocalDate date) {
        return date != null && !date.isBefore(LocalDate.now(clock)) ? date : null;
    }

    private LocalDate usableCheckOut(LocalDate checkIn, LocalDate date) {
        return checkIn != null && date != null && date.isAfter(checkIn) && !date.isAfter(checkIn.plusDays(maxNights))
                ? date : null;
    }

    private boolean inRange(Integer value, int min, int max) {
        return value != null && value >= min && value <= max;
    }

    private BigDecimal usableBudget(BigDecimal budget) {
        return budget != null && budget.compareTo(BigDecimal.ZERO) > 0 && budget.scale() <= 2 ? budget : null;
    }
}
