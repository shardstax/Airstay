package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.dto.*;
import com.shardx.hotelManagement.airBnb.entity.Room;
import org.springframework.data.domain.Page;

import java.util.List;

public interface InventoryService {

    void initializeRoomForAYear(Room room);

    void deleteAllInventories(Room room);

    Page<HotelPriceResponseDto> searchHotels(HotelSearchRequest hotelSearchRequest);

    List<InventoryDto> getAllInventoryByRoom(Long roomId);

    void updateInventory(Long roomId, UpdateInventoryRequestDto updateInventoryRequestDto);

    /** Applies a new room count to all future nights; refuses if it would drop below rooms already held or sold. */
    void updateRoomCapacity(Room room, int newTotalCount);
}
