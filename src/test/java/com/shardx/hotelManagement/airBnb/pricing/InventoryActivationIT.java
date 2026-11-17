package com.shardx.hotelManagement.airBnb.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Activating a hotel creates a year of inventory for each room type in ONE statement per room. These tests pin down what
 * that statement must produce: every night exactly once, with the room's values and nothing booked, priced by the rules.
 */
class InventoryActivationIT extends PricingTestBase {

    @Test
    void activationCreatesEveryNightOfTheYearOnceForEveryRoomType() {
        Hotel hotel = activeHotelWithTwoRoomTypes();
        LocalDate today = LocalDate.now();
        long nights = ChronoUnit.DAYS.between(today, today.plusYears(1)) + 1;

        for (long roomId : new long[]{hotel.standardRoomId(), hotel.suiteRoomId()}) {
            assertThat(count("SELECT count(*) FROM inventory WHERE room_id = ?", roomId)).isEqualTo(nights);
            assertThat(count("SELECT count(DISTINCT date) FROM inventory WHERE room_id = ?", roomId)).isEqualTo(nights);
            assertThat(jdbc.queryForObject("SELECT min(date) FROM inventory WHERE room_id = ?", LocalDate.class, roomId))
                    .isEqualTo(today);
            assertThat(jdbc.queryForObject("SELECT max(date) FROM inventory WHERE room_id = ?", LocalDate.class, roomId))
                    .isEqualTo(today.plusYears(1));
        }
    }

    @Test
    void everyCreatedNightHasTheRoomsValuesAndNothingBooked() {
        Hotel hotel = activeHotelWithTwoRoomTypes();

        Map<String, Object> night = jdbc.queryForMap("""
                SELECT hotel_id, room_id, city, total_count, booked_count, reserved_count, surge_factor, closed,
                       price, created_at IS NOT NULL AS has_created_at, updated_at IS NOT NULL AS has_updated_at
                FROM inventory WHERE room_id = ? AND date = ?""", hotel.suiteRoomId(), LocalDate.now().plusDays(60));

        assertThat(night.get("hotel_id")).isEqualTo(hotel.id());
        assertThat(night.get("city")).isEqualTo(hotel.city());
        assertThat(night.get("total_count")).isEqualTo(10);
        assertThat(night.get("booked_count")).isEqualTo(0);
        assertThat(night.get("reserved_count")).isEqualTo(0);
        assertThat((BigDecimal) night.get("surge_factor")).isEqualByComparingTo("1.00");
        assertThat(night.get("closed")).isEqualTo(false);
        assertThat((BigDecimal) night.get("price")).isEqualByComparingTo("2500.00");
        assertThat(night.get("has_created_at")).isEqualTo(true);
        assertThat(night.get("has_updated_at")).isEqualTo(true);
    }

    @Test
    void activationPricesTheNightsAndFillsTheFromPriceWithTheCheapestRoom() {
        Hotel hotel = activeHotelWithTwoRoomTypes();
        LocalDate today = LocalDate.now();

        // A night far ahead: base prices, and the hotel's "from" price is the cheaper room.
        assertThat(nightPrice(hotel.standardRoomId(), today.plusDays(60))).isEqualByComparingTo("2000.00");
        assertThat(nightPrice(hotel.suiteRoomId(), today.plusDays(60))).isEqualByComparingTo("2500.00");
        assertThat(fromPrice(hotel.id(), today.plusDays(60))).isEqualByComparingTo("2000.00");

        // A night within 7 days: +15% last-minute surcharge on every room.
        assertThat(nightPrice(hotel.standardRoomId(), today.plusDays(3))).isEqualByComparingTo("2300.00");
        assertThat(fromPrice(hotel.id(), today.plusDays(3))).isEqualByComparingTo("2300.00");

        // One "from" price per night.
        long nights = ChronoUnit.DAYS.between(today, today.plusYears(1)) + 1;
        assertThat(count("SELECT count(*) FROM hotel_min_price WHERE hotel_id = ?", hotel.id())).isEqualTo(nights);
    }
}
