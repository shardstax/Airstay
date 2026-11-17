package com.shardx.hotelManagement.airBnb.repository;

import com.shardx.hotelManagement.airBnb.entity.Hotel;
import com.shardx.hotelManagement.airBnb.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long> {
    List<Hotel> findByOwner(User user);

    /**
     * Just the ids, in a stable order, one page at a time (a Slice, so no extra count query). Loading whole hotels is
     * expensive because every hotel eagerly loads its owner and the owner's roles, and the price refresh only needs the id.
     */
    @Query("SELECT h.id FROM Hotel h ORDER BY h.id")
    Slice<Long> findAllIds(Pageable pageable);
}
