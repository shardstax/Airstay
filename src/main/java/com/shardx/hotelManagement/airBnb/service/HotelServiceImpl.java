package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.dto.*;
import com.shardx.hotelManagement.airBnb.entity.Hotel;
import com.shardx.hotelManagement.airBnb.entity.Room;
import com.shardx.hotelManagement.airBnb.entity.User;
import com.shardx.hotelManagement.airBnb.exception.ConflictException;
import com.shardx.hotelManagement.airBnb.exception.ResourceNotFoundException;
import com.shardx.hotelManagement.airBnb.exception.UnAuthorisedException;
import com.shardx.hotelManagement.airBnb.repository.BookingRepository;
import com.shardx.hotelManagement.airBnb.repository.HotelMinPriceRepository;
import com.shardx.hotelManagement.airBnb.repository.HotelRepository;
import com.shardx.hotelManagement.airBnb.repository.InventoryRepository;
import com.shardx.hotelManagement.airBnb.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import static com.shardx.hotelManagement.airBnb.util.AppUtils.getCurrentUser;

@Service
@Slf4j
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService {

    private final HotelRepository hotelRepository;
    private final ModelMapper modelMapper;
    private final InventoryService inventoryService;
    private final RoomRepository roomRepository;
    private final InventoryRepository inventoryRepository;
    private final BookingRepository bookingRepository;
    private final HotelMinPriceRepository hotelMinPriceRepository;
    private final PricingUpdateService pricingUpdateService;

    @Override
    @Transactional
    public HotelDto createNewHotel(HotelDto hotelDto) {
        log.info("Creating a new hotel with name: {}", hotelDto.getName());
        Hotel hotel = modelMapper.map(hotelDto, Hotel.class);
        hotel.setId(null);      // never let a client choose (or overwrite) an id
        hotel.setActive(false); // goes live only through activateHotel
        hotel.setOwner(getCurrentUser());
        hotel = hotelRepository.save(hotel);
        log.info("Created a new hotel with ID: {}", hotel.getId());
        return modelMapper.map(hotel, HotelDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public HotelDto getHotelById(Long id) {
        log.info("Getting the hotel with ID: {}", id);
        return modelMapper.map(getOwnedHotel(id), HotelDto.class);
    }

    @Override
    @Transactional
    public HotelDto updateHotelById(Long id, HotelDto hotelDto) {
        log.info("Updating the hotel with ID: {}", id);
        Hotel hotel = getOwnedHotel(id);
        Boolean active = hotel.getActive();
        String previousCity = hotel.getCity();

        modelMapper.map(hotelDto, hotel);
        hotel.setId(id);
        hotel.setActive(active); // the active flag cannot be changed through an update
        hotel = hotelRepository.save(hotel);

        if (!Objects.equals(previousCity, hotel.getCity())) {
            // Search filters inventory by its copy of the city, so keep it in step.
            int updatedRows = inventoryRepository.updateCityForHotel(id, hotel.getCity());
            log.info("Hotel {} moved from {} to {}; updated {} inventory rows", id, previousCity, hotel.getCity(), updatedRows);
        }
        return modelMapper.map(hotel, HotelDto.class);
    }

    @Override
    @Transactional
    public void deleteHotelById(Long id) {
        Hotel hotel = getOwnedHotel(id);
        if (bookingRepository.existsByHotel(hotel)) {
            throw new ConflictException("Hotels with bookings cannot be deleted");
        }

        hotelMinPriceRepository.deleteByHotel(hotel);
        for (Room room : hotel.getRooms()) {
            inventoryService.deleteAllInventories(room);
            roomRepository.deleteById(room.getId());
        }
        hotelRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void activateHotel(Long hotelId) {
        log.info("Activating the hotel with ID: {}", hotelId);
        Hotel hotel = getOwnedHotel(hotelId);
        if (Boolean.TRUE.equals(hotel.getActive())) {
            throw new ConflictException("Hotel is already active");
        }
        if (hotel.getRooms() == null || hotel.getRooms().isEmpty()) {
            throw new ConflictException("Add at least one room before activating the hotel");
        }

        hotel.setActive(true);
        for (Room room : hotel.getRooms()) {
            inventoryService.initializeRoomForAYear(room);
        }
        // Compute prices now so the hotel shows up in search immediately, not after the hourly job.
        pricingUpdateService.updateHotelPrices(hotel);
    }

    @Override
    @Transactional(readOnly = true)
    public HotelInfoDto getHotelInfoById(Long hotelId, HotelInfoRequestDto hotelInfoRequestDto) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .filter(found -> Boolean.TRUE.equals(found.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + hotelId));

        // endDate is the check-out day, so the nights are [startDate, endDate - 1].
        long nights = ChronoUnit.DAYS.between(hotelInfoRequestDto.getStartDate(), hotelInfoRequestDto.getEndDate());
        List<RoomPriceDto> roomPriceDtoList = inventoryRepository.findRoomAveragePrice(hotelId,
                hotelInfoRequestDto.getStartDate(), hotelInfoRequestDto.getEndDate().minusDays(1),
                hotelInfoRequestDto.getRoomsCount(), nights);

        List<RoomPriceResponseDto> rooms = roomPriceDtoList.stream()
                .map(roomPriceDto -> {
                    RoomPriceResponseDto roomPriceResponseDto = modelMapper.map(roomPriceDto.getRoom(),
                            RoomPriceResponseDto.class);
                    roomPriceResponseDto.setPrice(roomPriceDto.getPrice());
                    return roomPriceResponseDto;
                })
                .toList();

        return new HotelInfoDto(modelMapper.map(hotel, HotelDto.class), rooms);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HotelDto> getAllHotels() {
        User user = getCurrentUser();
        log.info("Getting all hotels for the admin user with ID: {}", user.getId());
        return hotelRepository.findByOwner(user).stream()
                .map(hotel -> modelMapper.map(hotel, HotelDto.class))
                .toList();
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
}
