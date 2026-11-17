package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.dto.*;
import com.shardx.hotelManagement.airBnb.entity.Hotel;
import com.shardx.hotelManagement.airBnb.entity.Inventory;
import com.shardx.hotelManagement.airBnb.entity.Room;
import com.shardx.hotelManagement.airBnb.entity.User;
import com.shardx.hotelManagement.airBnb.exception.BadRequestException;
import com.shardx.hotelManagement.airBnb.exception.ConflictException;
import com.shardx.hotelManagement.airBnb.exception.ResourceNotFoundException;
import com.shardx.hotelManagement.airBnb.repository.HotelMinPriceRepository;
import com.shardx.hotelManagement.airBnb.repository.InventoryRepository;
import com.shardx.hotelManagement.airBnb.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.shardx.hotelManagement.airBnb.util.AppUtils.getCurrentUser;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final RoomRepository roomRepository;
    private final ModelMapper modelMapper;
    private final InventoryRepository inventoryRepository;
    private final HotelMinPriceRepository hotelMinPriceRepository;
    private final PricingUpdateService pricingUpdateService;
    private final Clock clock;

    @Value("${airstay.booking.max-nights}")
    private long maxNights;

    @Override
    @Transactional
    public void initializeRoomForAYear(Room room) {
        LocalDate today = LocalDate.now(clock);
        int lastDayOffset = (int) ChronoUnit.DAYS.between(today, today.plusYears(1)); // today and the next year, inclusive

        // One statement creates every night (it used to be one INSERT per night per room type).
        int created = inventoryRepository.insertNights(room.getHotel().getId(), room.getId(), today, lastDayOffset,
                room.getTotalCount(), room.getBasePrice(), room.getHotel().getCity(), LocalDateTime.now(clock));
        if (created != lastDayOffset + 1) {
            throw new IllegalStateException("Created " + created + " inventory rows for room " + room.getId()
                    + ", expected " + (lastDayOffset + 1));
        }
    }

    @Override
    @Transactional
    public void deleteAllInventories(Room room) {
        log.info("Deleting the inventories of room with id: {}", room.getId());
        inventoryRepository.deleteByRoom(room);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HotelPriceResponseDto> searchHotels(HotelSearchRequest hotelSearchRequest) {
        log.info("Searching hotels for {} city, from {} to {}", hotelSearchRequest.getCity(),
                hotelSearchRequest.getStartDate(), hotelSearchRequest.getEndDate());

        // endDate is the check-out day, so the nights are [startDate, endDate - 1].
        long nights = ChronoUnit.DAYS.between(hotelSearchRequest.getStartDate(), hotelSearchRequest.getEndDate());
        if (nights > maxNights) {
            throw new BadRequestException("A stay can be at most " + maxNights + " nights");
        }

        Pageable pageable = PageRequest.of(hotelSearchRequest.getPage(), hotelSearchRequest.getSize());
        Page<HotelPriceDto> hotelPage = hotelMinPriceRepository.findHotelsWithAvailableInventory(
                hotelSearchRequest.getCity().trim(),
                hotelSearchRequest.getStartDate(),
                hotelSearchRequest.getEndDate().minusDays(1),
                hotelSearchRequest.getRoomsCount(),
                nights,
                pageable);

        return hotelPage.map(hotelPriceDto -> {
            HotelPriceResponseDto hotelPriceResponseDto = modelMapper.map(hotelPriceDto.getHotel(), HotelPriceResponseDto.class);
            hotelPriceResponseDto.setPrice(hotelPriceDto.getPrice());
            return hotelPriceResponseDto;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryDto> getAllInventoryByRoom(Long roomId) {
        log.info("Getting All inventory by room for room with id: {}", roomId);
        Room room = getOwnedRoom(roomId);
        return inventoryRepository.findByRoomOrderByDate(room).stream()
                .map(inventory -> modelMapper.map(inventory, InventoryDto.class))
                .toList();
    }

    @Override
    @Transactional
    public void updateInventory(Long roomId, UpdateInventoryRequestDto updateInventoryRequestDto) {
        log.info("Updating All inventory by room for room with id: {} between date range: {} - {}", roomId,
                updateInventoryRequestDto.getStartDate(), updateInventoryRequestDto.getEndDate());
        Room room = getOwnedRoom(roomId);
        Hotel hotel = room.getHotel();

        inventoryRepository.lockInventoryForRoom(roomId, updateInventoryRequestDto.getStartDate(),
                updateInventoryRequestDto.getEndDate());
        inventoryRepository.updateInventory(roomId, updateInventoryRequestDto.getStartDate(),
                updateInventoryRequestDto.getEndDate(), updateInventoryRequestDto.getClosed(),
                updateInventoryRequestDto.getSurgeFactor());

        // A new surge factor changes nightly prices; refresh them now instead of waiting for the hourly job.
        pricingUpdateService.updateHotelPrices(hotel);
    }

    @Override
    @Transactional
    public void updateRoomCapacity(Room room, int newTotalCount) {
        LocalDate today = LocalDate.now(clock);
        inventoryRepository.lockInventoryForRoom(room.getId(), today, today.plusYears(2));

        long nightsOverCapacity = inventoryRepository.countNightsOverCapacity(room.getId(), today, newTotalCount);
        if (nightsOverCapacity > 0) {
            throw new ConflictException("Cannot reduce to " + newTotalCount + " rooms: " + nightsOverCapacity
                    + " upcoming nights already have more rooms held or booked");
        }
        int updatedNights = inventoryRepository.updateTotalCount(room.getId(), today, newTotalCount);
        log.info("Room {} now has {} rooms on {} upcoming nights", room.getId(), newTotalCount, updatedNights);
    }

    private Room getOwnedRoom(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));
        User user = getCurrentUser();
        if (!user.equals(room.getHotel().getOwner())) {
            throw new AccessDeniedException("You are not the owner of room with id: " + roomId);
        }
        return room;
    }
}
