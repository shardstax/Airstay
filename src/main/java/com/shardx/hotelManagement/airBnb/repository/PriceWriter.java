package com.shardx.hotelManagement.airBnb.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Writes prices with set-based SQL: ONE statement per hotel whatever the number of nights, instead of one statement per
 * night. It uses plain JDBC through the same connection as the surrounding JPA transaction, so it commits or rolls back
 * together with it.
 *
 * <p>Both statements only touch rows whose price actually changes, so an hourly refresh in which nothing changed writes
 * nothing.
 */
@Repository
public class PriceWriter {

    private final JdbcTemplate jdbc;

    public PriceWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Sets the price of the given inventory rows: the rows are locked in id order first, then updated in one statement.
     *
     * <p>Why the explicit lock: a single UPDATE locks the rows in whatever order PostgreSQL visits them, while bookings
     * lock the rows of a room in date order (which is id order, because a room's nights are created in date order). Two
     * transactions that lock overlapping rows in different orders can deadlock; locking in ascending id order here keeps
     * the "everyone locks in the same order" rule that the booking code relies on.
     *
     * @return the number of rows updated
     */
    public int updateInventoryPrices(List<Long> inventoryIds, List<BigDecimal> prices, LocalDateTime now) {
        return jdbc.execute((java.sql.Connection connection) -> {
            Array idArray = connection.createArrayOf("bigint", inventoryIds.toArray());
            Array priceArray = connection.createArrayOf("numeric", prices.toArray());
            try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT id FROM inventory WHERE id = ANY(?) ORDER BY id FOR UPDATE")) {
                lock.setArray(1, idArray);
                lock.executeQuery().close();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE inventory
                    SET price = changes.price, updated_at = ?
                    FROM unnest(CAST(? AS bigint[]), CAST(? AS numeric[])) AS changes(id, price)
                    WHERE inventory.id = changes.id
                    """)) {
                statement.setObject(1, now);
                statement.setArray(2, idArray);
                statement.setArray(3, priceArray);
                return statement.executeUpdate();
            }
        });
    }

    /**
     * Inserts or updates the hotel's lowest nightly price per date in one statement. An existing row is only rewritten
     * when its price differs.
     *
     * @return the number of rows inserted or updated
     */
    public int upsertHotelMinPrices(long hotelId, Map<LocalDate, BigDecimal> cheapestByDate, LocalDateTime now) {
        java.sql.Date[] dates = cheapestByDate.keySet().stream().map(java.sql.Date::valueOf).toArray(java.sql.Date[]::new);
        BigDecimal[] prices = cheapestByDate.values().toArray(new BigDecimal[0]);
        return jdbc.execute((java.sql.Connection connection) -> {
            Array dateArray = connection.createArrayOf("date", dates);
            Array priceArray = connection.createArrayOf("numeric", prices);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO hotel_min_price (hotel_id, date, price, created_at, updated_at)
                    SELECT ?, nightly.date, nightly.price, ?, ?
                    FROM unnest(CAST(? AS date[]), CAST(? AS numeric[])) AS nightly(date, price)
                    ON CONFLICT (hotel_id, date) DO UPDATE
                        SET price = EXCLUDED.price, updated_at = EXCLUDED.updated_at
                        WHERE hotel_min_price.price <> EXCLUDED.price
                    """)) {
                statement.setLong(1, hotelId);
                statement.setObject(2, now);
                statement.setObject(3, now);
                statement.setArray(4, dateArray);
                statement.setArray(5, priceArray);
                return statement.executeUpdate();
            }
        });
    }
}
