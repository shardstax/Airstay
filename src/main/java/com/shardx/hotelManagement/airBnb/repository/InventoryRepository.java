package com.shardx.hotelManagement.airBnb.repository;

import com.shardx.hotelManagement.airBnb.dto.RoomPriceDto;
import com.shardx.hotelManagement.airBnb.entity.Hotel;
import com.shardx.hotelManagement.airBnb.entity.Inventory;
import com.shardx.hotelManagement.airBnb.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Inventory rows are one per room type per night. All date ranges here are inclusive night ranges:
 * a stay from check-in to check-out uses nights [checkIn, checkOut - 1].
 * Every count-changing update returns the number of rows changed so callers can verify that
 * every night of the stay was updated.
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    void deleteByRoom(Room room);

    List<Inventory> findByHotelAndDateBetween(Hotel hotel, LocalDate startDate, LocalDate endDate);

    List<Inventory> findByRoomOrderByDate(Room room);

    /**
     * Locks only the nights that can take the requested rooms. If fewer rows come back than nights
     * requested, the room is not available. Locked in date order (see lockInventoryForRoom).
     */
    @Query("""
            SELECT i
            FROM Inventory i
            WHERE i.room.id = :roomId
                AND i.date BETWEEN :startDate AND :endDate
                AND i.closed = false
                AND (i.totalCount - i.bookedCount - i.reservedCount) >= :roomsCount
            ORDER BY i.date
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Inventory> findAndLockAvailableInventory(@Param("roomId") Long roomId,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate,
                                                  @Param("roomsCount") Integer roomsCount);

    /**
     * Locks every inventory row of the room in the range, whatever its counts.
     * Rows are always locked in date order so two overlapping requests cannot deadlock.
     */
    @Query("""
            SELECT i
            FROM Inventory i
            WHERE i.room.id = :roomId
              AND i.date BETWEEN :startDate AND :endDate
            ORDER BY i.date
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Inventory> lockInventoryForRoom(@Param("roomId") Long roomId,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    /** Places a hold: free rooms move into reservedCount. */
    @Modifying
    @Query("""
            UPDATE Inventory i
            SET i.reservedCount = i.reservedCount + :numberOfRooms
            WHERE i.room.id = :roomId
              AND i.date BETWEEN :startDate AND :endDate
              AND (i.totalCount - i.bookedCount - i.reservedCount) >= :numberOfRooms
              AND i.closed = false
            """)
    int reserveRooms(@Param("roomId") Long roomId,
                     @Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate,
                     @Param("numberOfRooms") int numberOfRooms);

    /**
     * Payment succeeded: held rooms become booked. Deliberately ignores the closed flag, because the
     * rooms were already held for this guest before the owner closed the date.
     */
    @Modifying
    @Query("""
            UPDATE Inventory i
            SET i.reservedCount = i.reservedCount - :numberOfRooms,
                i.bookedCount = i.bookedCount + :numberOfRooms
            WHERE i.room.id = :roomId
              AND i.date BETWEEN :startDate AND :endDate
              AND i.reservedCount >= :numberOfRooms
            """)
    int confirmBooking(@Param("roomId") Long roomId,
                       @Param("startDate") LocalDate startDate,
                       @Param("endDate") LocalDate endDate,
                       @Param("numberOfRooms") int numberOfRooms);

    /** Hold lapsed or payment session expired: held rooms become free again. */
    @Modifying
    @Query("""
            UPDATE Inventory i
            SET i.reservedCount = i.reservedCount - :numberOfRooms
            WHERE i.room.id = :roomId
              AND i.date BETWEEN :startDate AND :endDate
              AND i.reservedCount >= :numberOfRooms
            """)
    int releaseReservation(@Param("roomId") Long roomId,
                           @Param("startDate") LocalDate startDate,
                           @Param("endDate") LocalDate endDate,
                           @Param("numberOfRooms") int numberOfRooms);

    /** Confirmed booking cancelled: booked rooms become free again. */
    @Modifying
    @Query("""
            UPDATE Inventory i
            SET i.bookedCount = i.bookedCount - :numberOfRooms
            WHERE i.room.id = :roomId
              AND i.date BETWEEN :startDate AND :endDate
              AND i.bookedCount >= :numberOfRooms
            """)
    int releaseBookedRooms(@Param("roomId") Long roomId,
                           @Param("startDate") LocalDate startDate,
                           @Param("endDate") LocalDate endDate,
                           @Param("numberOfRooms") int numberOfRooms);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Inventory i
            SET i.surgeFactor = :surgeFactor,
                i.closed = :closed
            WHERE i.room.id = :roomId
              AND i.date BETWEEN :startDate AND :endDate
            """)
    int updateInventory(@Param("roomId") Long roomId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("closed") boolean closed,
                        @Param("surgeFactor") BigDecimal surgeFactor);

    /** Inventory keeps a copy of the hotel city for fast search, so it must follow hotel edits. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Inventory i SET i.city = :city WHERE i.hotel.id = :hotelId")
    int updateCityForHotel(@Param("hotelId") Long hotelId, @Param("city") String city);

    /** Nights from the given date where a new room count could not cover rooms already held or sold. */
    @Query("""
            SELECT COUNT(i)
            FROM Inventory i
            WHERE i.room.id = :roomId
              AND i.date >= :fromDate
              AND (i.bookedCount + i.reservedCount) > :newTotalCount
            """)
    long countNightsOverCapacity(@Param("roomId") Long roomId,
                                 @Param("fromDate") LocalDate fromDate,
                                 @Param("newTotalCount") int newTotalCount);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Inventory i
            SET i.totalCount = :newTotalCount
            WHERE i.room.id = :roomId
              AND i.date >= :fromDate
            """)
    int updateTotalCount(@Param("roomId") Long roomId,
                         @Param("fromDate") LocalDate fromDate,
                         @Param("newTotalCount") int newTotalCount);

    /**
     * Average nightly price per room type for the stay. Price is NULL when the room type is not free
     * on every night, which the caller shows as unavailable.
     */
    @Query("""
            SELECT new com.shardx.hotelManagement.airBnb.dto.RoomPriceDto(
                i.room,
                CASE
                    WHEN COUNT(i) = :dateCount THEN AVG(i.price)
                    ELSE NULL
                END
            )
            FROM Inventory i
            WHERE i.hotel.id = :hotelId
              AND i.date BETWEEN :startDate AND :endDate
              AND (i.totalCount - i.bookedCount - i.reservedCount) >= :roomsCount
              AND i.closed = false
            GROUP BY i.room
            """)
    List<RoomPriceDto> findRoomAveragePrice(@Param("hotelId") Long hotelId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate,
                                            @Param("roomsCount") Long roomsCount,
                                            @Param("dateCount") Long dateCount);
}
