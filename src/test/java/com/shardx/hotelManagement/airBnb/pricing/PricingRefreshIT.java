package com.shardx.hotelManagement.airBnb.pricing;

import com.shardx.hotelManagement.airBnb.service.PricingUpdateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The price refresh reads a hotel's nights in one query and writes with set-based statements. These tests pin down what
 * must NOT change: prices follow the pricing rules, the hotel's "from" price is its cheapest room, and a refresh only
 * writes the rows whose price really changed (an hourly run in which nothing changed writes nothing). They also check
 * that owner changes show up in the prices by the time the owner's request returns.
 *
 * <p>"Written" is judged by PostgreSQL's row version ({@code xmin}), which changes whenever a row is rewritten, whatever
 * columns the statement touched and whatever the clock says.
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
        Map<Long, String> inventoryBefore = rowVersions("inventory", hotel.id());
        Map<Long, String> fromPricesBefore = rowVersions("hotel_min_price", hotel.id());

        pricingUpdateService.updateHotelPrices(hotel.id());

        // standard room: 2000 x 3.00; the cheapest room on that night is now the suite (2500)
        assertThat(nightPrice(hotel.standardRoomId(), surgeNight)).isEqualByComparingTo("6000.00");
        assertThat(fromPrice(hotel.id(), surgeNight)).isEqualByComparingTo("2500.00");
        // suite: 9 of 10 rooms booked is above 80%, so +20%; the standard room is still the cheapest
        assertThat(nightPrice(hotel.suiteRoomId(), busyNight)).isEqualByComparingTo("3000.00");
        assertThat(fromPrice(hotel.id(), busyNight)).isEqualByComparingTo("2000.00");

        // Only the two changed nights and the one changed "from" price were rewritten; everything else is untouched.
        // (The test's own UPDATEs above already changed two inventory rows, so compare against the state after them.)
        Map<Long, String> inventoryAfter = rowVersions("inventory", hotel.id());
        assertThat(rewrittenRows(inventoryBefore, inventoryAfter)).as("inventory rows rewritten by the refresh").isEqualTo(2);
        Map<Long, String> fromPricesAfter = rowVersions("hotel_min_price", hotel.id());
        assertThat(rewrittenRows(fromPricesBefore, fromPricesAfter)).as("'from' prices rewritten by the refresh").isEqualTo(1);

        // A second refresh with nothing new to do rewrites no row at all.
        pricingUpdateService.updateHotelPrices(hotel.id());
        assertThat(rowVersions("inventory", hotel.id())).isEqualTo(inventoryAfter);
        assertThat(rowVersions("hotel_min_price", hotel.id())).isEqualTo(fromPricesAfter);
    }

    @Test
    void theScheduledRefreshFixesEveryHotelAcrossSeveralPagesAndSkipsHotelsWithoutInventory() {
        // The test profile reads hotel ids 2 at a time, so these hotels (plus any other test's) span several pages.
        Hotel first = activeHotelWithTwoRoomTypes();
        Hotel second = activeHotelWithTwoRoomTypes();
        Hotel third = activeHotelWithTwoRoomTypes();
        // A hotel that was never activated has no inventory: the refresh must skip it without failing.
        String manager = newManagerToken();
        long draftId = data(post("/admin/hotels", manager, Map.of("name", "Not live", "placeId", newPlaceId())))
                .path("id").asLong();
        LocalDate night = LocalDate.now().plusDays(50);
        for (Hotel hotel : new Hotel[]{first, second, third}) {
            // Both the nightly price and the hotel's "from" price are wrong; the refresh has to repair both.
            jdbc.update("UPDATE inventory SET price = 1.00 WHERE hotel_id = ? AND date = ?", hotel.id(), night);
            jdbc.update("UPDATE hotel_min_price SET price = 1.00 WHERE hotel_id = ? AND date = ?", hotel.id(), night);
        }

        PricingUpdateService.RefreshResult result = pricingUpdateService.updatePrices();

        assertThat(result.failed()).as("hotels whose refresh failed").isZero();
        assertThat(result.hotels()).as("hotels visited, in several pages of 2").isGreaterThanOrEqualTo(4);
        for (Hotel hotel : new Hotel[]{first, second, third}) {
            assertThat(nightPrice(hotel.standardRoomId(), night)).isEqualByComparingTo("2000.00");
            assertThat(nightPrice(hotel.suiteRoomId(), night)).isEqualByComparingTo("2500.00");
            assertThat(fromPrice(hotel.id(), night)).isEqualByComparingTo("2000.00");
        }
        assertThat(count("SELECT count(*) FROM hotel_min_price WHERE hotel_id = ?", draftId))
                .as("a hotel without inventory gets no 'from' prices").isZero();
    }

    // ---- owner changes: prices are up to date when the owner's request returns -----------------------------

    @Test
    void anOwnersSurgeFactorChangesThePricesBeforeTheResponseReturns() {
        Hotel hotel = activeHotelWithTwoRoomTypes();
        LocalDate from = LocalDate.now().plusDays(30);

        var response = send(HttpMethod.PATCH, "/admin/inventory/rooms/" + hotel.standardRoomId(), hotel.managerToken(), Map.of(
                "startDate", from.toString(), "endDate", from.plusDays(1).toString(), "surgeFactor", "1.50", "closed", false));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(nightPrice(hotel.standardRoomId(), from)).isEqualByComparingTo("3000.00");
        assertThat(nightPrice(hotel.standardRoomId(), from.plusDays(1))).isEqualByComparingTo("3000.00");
        assertThat(nightPrice(hotel.standardRoomId(), from.plusDays(2))).isEqualByComparingTo("2000.00");
        assertThat(fromPrice(hotel.id(), from)).as("the suite is now the cheapest room").isEqualByComparingTo("2500.00");
        assertThat(fromPrice(hotel.id(), from.plusDays(2))).isEqualByComparingTo("2000.00");
    }

    @Test
    void changingARoomsBasePriceRefreshesThePricesAfterTheOwnersTransaction() {
        Hotel hotel = activeHotelWithTwoRoomTypes();
        LocalDate night = LocalDate.now().plusDays(60);

        var response = send(HttpMethod.PUT, "/admin/hotels/" + hotel.id() + "/rooms/" + hotel.standardRoomId(),
                hotel.managerToken(), Map.of("type", "Standard", "basePrice", "1800.00", "totalCount", 10, "capacity", 2));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(nightPrice(hotel.standardRoomId(), night)).isEqualByComparingTo("1800.00");
        assertThat(fromPrice(hotel.id(), night)).isEqualByComparingTo("1800.00");
        assertThat(nightPrice(hotel.suiteRoomId(), night)).as("the other room type is untouched").isEqualByComparingTo("2500.00");
    }

    @Test
    void deletingARoomRemovesItsPriceFromTheHotelsFromPrice() {
        Hotel hotel = activeHotelWithTwoRoomTypes();
        LocalDate night = LocalDate.now().plusDays(60);
        assertThat(fromPrice(hotel.id(), night)).isEqualByComparingTo("2000.00");

        var response = send(HttpMethod.DELETE, "/admin/hotels/" + hotel.id() + "/rooms/" + hotel.standardRoomId(),
                hotel.managerToken(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(count("SELECT count(*) FROM inventory WHERE room_id = ?", hotel.standardRoomId())).isZero();
        assertThat(fromPrice(hotel.id(), night)).as("only the suite is left").isEqualByComparingTo("2500.00");
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    /** id -> PostgreSQL row version. The version changes when a row is rewritten, whichever columns changed. */
    private Map<Long, String> rowVersions(String table, long hotelId) {
        Map<Long, String> versions = new HashMap<>();
        jdbc.query("SELECT id, xmin::text AS version FROM " + table + " WHERE hotel_id = ?",
                rs -> {
                    versions.put(rs.getLong("id"), rs.getString("version"));
                }, hotelId);
        return versions;
    }

    private static long rewrittenRows(Map<Long, String> before, Map<Long, String> after) {
        return before.entrySet().stream().filter(entry -> !entry.getValue().equals(after.get(entry.getKey()))).count();
    }
}
