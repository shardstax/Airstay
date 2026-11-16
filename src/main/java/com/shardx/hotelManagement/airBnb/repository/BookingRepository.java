package com.shardx.hotelManagement.airBnb.repository;

import com.shardx.hotelManagement.airBnb.entity.Booking;
import com.shardx.hotelManagement.airBnb.entity.Hotel;
import com.shardx.hotelManagement.airBnb.entity.Room;
import com.shardx.hotelManagement.airBnb.entity.User;
import com.shardx.hotelManagement.airBnb.entity.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByPaymentSessionId(String sessionId);

    List<Booking> findByHotel(Hotel hotel);

    List<Booking> findByHotelAndCreatedAtBetween(Hotel hotel, LocalDateTime startDateTime, LocalDateTime endDateTime);

    List<Booking> findByUser(User user);

    boolean existsByHotel(Hotel hotel);

    boolean existsByRoom(Room room);

    /** Row-locks the booking so state changes (guests, payment, webhook, expiry) are serialised. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.paymentSessionId = :sessionId")
    Optional<Booking> findByPaymentSessionIdForUpdate(@Param("sessionId") String sessionId);

    @Query("""
            SELECT b.id
            FROM Booking b
            WHERE b.bookingStatus IN :statuses
              AND b.expiresAt < :now
            ORDER BY b.expiresAt
            """)
    List<Long> findExpiredHoldIds(@Param("statuses") Collection<BookingStatus> statuses,
                                  @Param("now") LocalDateTime now,
                                  Pageable pageable);
}
