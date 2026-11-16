package com.shardx.hotelManagement.airBnb.repository;

import com.shardx.hotelManagement.airBnb.dto.HotelPriceDto;
import com.shardx.hotelManagement.airBnb.entity.Hotel;
import com.shardx.hotelManagement.airBnb.entity.HotelMinPrice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface HotelMinPriceRepository extends JpaRepository<HotelMinPrice, Long> {

    /**
     * Active hotels in the city that have at least one room type with enough free rooms on every night
     * of the stay, priced by the hotel's average nightly "from" price over those nights.
     */
    @Query(value = """
            SELECT new com.shardx.hotelManagement.airBnb.dto.HotelPriceDto(mp.hotel, AVG(mp.price))
            FROM HotelMinPrice mp
            WHERE mp.hotel.city = :city
              AND mp.hotel.active = true
              AND mp.date BETWEEN :startDate AND :endDate
              AND mp.hotel.id IN (
                    SELECT i.hotel.id
                    FROM Inventory i
                    WHERE i.city = :city
                      AND i.date BETWEEN :startDate AND :endDate
                      AND i.closed = false
                      AND (i.totalCount - i.bookedCount - i.reservedCount) >= :roomsCount
                    GROUP BY i.hotel.id, i.room.id
                    HAVING COUNT(i.date) = :dateCount
              )
            GROUP BY mp.hotel
            """,
            countQuery = """
            SELECT COUNT(DISTINCT mp.hotel.id)
            FROM HotelMinPrice mp
            WHERE mp.hotel.city = :city
              AND mp.hotel.active = true
              AND mp.date BETWEEN :startDate AND :endDate
              AND mp.hotel.id IN (
                    SELECT i.hotel.id
                    FROM Inventory i
                    WHERE i.city = :city
                      AND i.date BETWEEN :startDate AND :endDate
                      AND i.closed = false
                      AND (i.totalCount - i.bookedCount - i.reservedCount) >= :roomsCount
                    GROUP BY i.hotel.id, i.room.id
                    HAVING COUNT(i.date) = :dateCount
              )
            """)
    Page<HotelPriceDto> findHotelsWithAvailableInventory(
            @Param("city") String city,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("roomsCount") Integer roomsCount,
            @Param("dateCount") Long dateCount,
            Pageable pageable
    );

    Optional<HotelMinPrice> findByHotelAndDate(Hotel hotel, LocalDate date);

    @Modifying
    @Query("DELETE FROM HotelMinPrice mp WHERE mp.hotel = :hotel")
    void deleteByHotel(@Param("hotel") Hotel hotel);
}
