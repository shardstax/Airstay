package com.shardx.hotelManagement.airBnb.pricing;

import com.shardx.hotelManagement.airBnb.service.PricingUpdateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The price refresh reads a hotel's nights in one query and writes with set-based statements. These tests pin down what
 * must NOT change: prices follow the pricing rules, the hotel's "from" price is its cheapest room, and a refresh only
 * writes the rows whose price really changed (an hourly run in which nothing changed writes nothing).
 */
class PricingRefreshIT extends PricingTestBase {

    @Autowired
    private PricingUpdateService pricingUpdateService;

    @Test
    void aRefreshAppliesChangesAndWritesOnlyTheRowsWhosePriceChanged() {
        Hotel hotel = activeHotelWithTwoRoomTypes();
        LocalDate today = LocalDate.now();
        LocalDate surgeNight = today.plusDays(30);
        LocalDate busyNight = today.plusDays(40);

        // The owner raises the standard room's surge factor on one night, and the suite is almost sold out on another.
        jdbc.update("UPDATE inventory SET surge_factor = 3.00 WHERE room_id = ? AND date = ?",
                hotel.standardRoomId(), surgeNight);
        jdbc.update("UPDATE inventory SET booked_count = 9 WHERE room_id = ? AND date = ?",
                hotel.suiteRoomId(), busyNight);
        Map<Long, Timestamp> inventoryBefore = updatedAtOfInventory(hotel.id());
        Map<Long, Timestamp> fromPricesBefore = updatedAtOfFromPrices(hotel.id());

        pricingUpdateService.updateHotelPrices(hotel.id());

        // standard room: 2000 x 3.00; the cheapest room on that night is now the suite (2500)
        assertThat(nightPrice(hotel.standardRoomId(), surgeNight)).isEqualByComparingTo("6000.00");
        assertThat(fromPrice(hotel.id(), surgeNight)).isEqualByComparingTo("2500.00");
        // suite: 9 of 10 rooms booked is above 80%, so +20%; the standard room is still the cheapest
        assertThat(nightPrice(hotel.suiteRoomId(), busyNight)).isEqualByComparingTo("3000.00");
        assertThat(fromPrice(hotel.id(), busyNight)).isEqualByComparingTo("2000.00");

        // Only the two changed nights and the one changed "from" price were written; everything else is untouched.
        Map<Long, Timestamp> inventoryAfter = updatedAtOfInventory(hotel.id());
        long inventoryRowsWritten = inventoryBefore.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(inventoryAfter.get(entry.getKey()))).count();
        assertThat(inventoryRowsWritten).as("inventory rows written by the refresh").isEqualTo(2);
        Map<Long, Timestamp> fromPricesAfter = updatedAtOfFromPrices(hotel.id());
        long fromPricesWritten = fromPricesBefore.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(fromPricesAfter.get(entry.getKey()))).count();
        assertThat(fromPricesWritten).as("'from' prices written by the refresh").isEqualTo(1);

        // A second refresh with nothing new to do writes nothing at all.
        pricingUpdateService.updateHotelPrices(hotel.id());
        assertThat(updatedAtOfInventory(hotel.id())).isEqualTo(inventoryAfter);
        assertThat(updatedAtOfFromPrices(hotel.id())).isEqualTo(fromPricesAfter);
    }

    @Test
    void theScheduledRefreshFixesEveryHotelAndSkipsHotelsWithoutInventory() {
        Hotel first = activeHotelWithTwoRoomTypes();
        Hotel second = activeHotelWithTwoRoomTypes();
        // A hotel that was never activated has no inventory: the refresh must simply skip it.
        String manager = newManagerToken();
        post("/admin/hotels", manager, Map.of("name", "Not live", "city", "Draft-" + UUID.randomUUID()));
        LocalDate night = LocalDate.now().plusDays(50);
        jdbc.update("UPDATE inventory SET price = 1.00 WHERE room_id = ? AND date = ?", first.standardRoomId(), night);
        jdbc.update("UPDATE inventory SET price = 1.00 WHERE room_id = ? AND date = ?", second.suiteRoomId(), night);

        pricingUpdateService.updatePrices();

        assertThat(nightPrice(first.standardRoomId(), night)).isEqualByComparingTo("2000.00");
        assertThat(nightPrice(second.suiteRoomId(), night)).isEqualByComparingTo("2500.00");
        assertThat(fromPrice(first.id(), night)).isEqualByComparingTo("2000.00");
    }

    private Map<Long, Timestamp> updatedAtOfInventory(long hotelId) {
        return updatedAt("SELECT id, updated_at FROM inventory WHERE hotel_id = ?", hotelId);
    }

    private Map<Long, Timestamp> updatedAtOfFromPrices(long hotelId) {
        return updatedAt("SELECT id, updated_at FROM hotel_min_price WHERE hotel_id = ?", hotelId);
    }

    private Map<Long, Timestamp> updatedAt(String sql, long hotelId) {
        Map<Long, Timestamp> result = new HashMap<>();
        jdbc.query(sql, rs -> {
            result.put(rs.getLong("id"), rs.getTimestamp("updated_at"));
        }, hotelId);
        return result;
    }
}
