package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.dto.RoomDto;
import com.shardx.hotelManagement.airBnb.entity.Hotel;
import com.shardx.hotelManagement.airBnb.entity.Room;
import com.shardx.hotelManagement.airBnb.entity.User;
import com.shardx.hotelManagement.airBnb.exception.ConflictException;
import com.shardx.hotelManagement.airBnb.exception.ResourceNotFoundException;
import com.shardx.hotelManagement.airBnb.exception.UnAuthorisedException;
import com.shardx.hotelManagement.airBnb.repository.BookingRepository;
import com.shardx.hotelManagement.airBnb.repository.HotelRepository;
import com.shardx.hotelManagement.airBnb.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static com.shardx.hotelManagement.airBnb.util.AppUtils.getCurrentUser;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final BookingRepository bookingRepository;
    private final InventoryService inventoryService;
    private final PricingUpdateService pricingUpdateService;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public RoomDto createNewRoom(Long hotelId, RoomDto roomDto) {
        log.info("Creating a new room in hotel with ID: {}", hotelId);
        Hotel hotel = getOwnedHotel(hotelId);

        Room room = modelMapper.map(roomDto, Room.class);
        room.setId(null); // never let a client choose (or overwrite) an id
        room.setHotel(hotel);
        room = roomRepository.save(room);

        if (Boolean.TRUE.equals(hotel.getActive())) {
            inventoryService.initializeRoomForAYear(room);
            pricingUpdateService.updateHotelPrices(hotel);
        }
        return modelMapper.map(room, RoomDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomDto> getAllRoomsInHotel(Long hotelId) {
        log.info("Getting all rooms in hotel with ID: {}", hotelId);
        Hotel hotel = getOwnedHotel(hotelId);
        return hotel.getRooms().stream()
                .map(room -> modelMapper.map(room, RoomDto.class))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoomDto getRoomById(Long hotelId, Long roomId) {
        log.info("Getting the room with ID: {}", roomId);
        return modelMapper.map(getOwnedRoom(hotelId, roomId), RoomDto.class);
    }

    @Override
    @Transactional
    public void deleteRoomById(Long hotelId, Long roomId) {
        log.info("Deleting the room with ID: {}", roomId);
        Room room = getOwnedRoom(hotelId, roomId);
        if (bookingRepository.existsByRoom(room)) {
            throw new ConflictException("Rooms with bookings cannot be deleted; close their inventory instead");
        }
        Hotel hotel = room.getHotel();
        inventoryService.deleteAllInventories(room);
        roomRepository.deleteById(roomId);

        if (Boolean.TRUE.equals(hotel.getActive())) {
            // Recompute the hotel's "from" price (after this transaction commits) so search stops showing the deleted room's price.
            pricingUpdateService.refreshAfterCommit(hotel.getId());
        }
    }

    @Override
    @Transactional
    public RoomDto updateRoomById(Long hotelId, Long roomId, RoomDto roomDto) {
        log.info("Updating the room with ID: {}", roomId);
        Room room = getOwnedRoom(hotelId, roomId);
        Hotel hotel = room.getHotel();
        BigDecimal previousBasePrice = room.getBasePrice();
        Integer previousTotalCount = room.getTotalCount();

        modelMapper.map(roomDto, room);
        room.setId(roomId);
        room.setHotel(hotel);
        room = roomRepository.save(room);

        if (Boolean.TRUE.equals(hotel.getActive())) {
            // Keep future inventory in step with the room so search and booking see the new values.
            if (!Objects.equals(previousTotalCount, room.getTotalCount())) {
                inventoryService.updateRoomCapacity(room, room.getTotalCount());
            }
            if (previousBasePrice.compareTo(room.getBasePrice()) != 0) {
                // After this transaction commits: a capacity change above holds locks on the room's nights until then.
                pricingUpdateService.refreshAfterCommit(hotel.getId());
            }
        }
        return modelMapper.map(room, RoomDto.class);
    }

    private Hotel getOwnedHotel(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + hotelId));
        User user = getCurrentUser();
        if (!user.equals(hotel.getOwner())) {
            throw new UnAuthorisedException("This user does not own this hotel with id: " + hotelId);
        }
        return hotel;
    }

    /** The room must exist, belong to the hotel in the URL, and the hotel must belong to the caller. */
    private Room getOwnedRoom(Long hotelId, Long roomId) {
        Room room = roomRepository.findById(roomId)
                .filter(found -> found.getHotel().getId().equals(hotelId))
                .orElseThrow(() -> new ResourceNotFoundException("Room " + roomId + " not found in hotel " + hotelId));
        User user = getCurrentUser();
        if (!user.equals(room.getHotel().getOwner())) {
            throw new UnAuthorisedException("This user does not own this room with id: " + roomId);
        }
        return room;
    }
}
