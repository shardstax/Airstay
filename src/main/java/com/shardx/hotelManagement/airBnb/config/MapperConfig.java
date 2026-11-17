package com.shardx.hotelManagement.airBnb.config;

import com.shardx.hotelManagement.airBnb.dto.BookingDto;
import com.shardx.hotelManagement.airBnb.entity.Booking;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        // Explicit so ModelMapper never has to guess which nested id goes where.
        modelMapper.typeMap(Booking.class, BookingDto.class).addMappings(mapper -> {
            mapper.map(src -> src.getHotel().getId(), BookingDto::setHotelId);
            mapper.map(src -> src.getRoom().getId(), BookingDto::setRoomId);
            mapper.map(src -> src.getHotel().getName(), BookingDto::setHotelName);
            mapper.map(src -> src.getRoom().getType(), BookingDto::setRoomType);
            // Set in BookingServiceImpl.toDto: the entity keeps zone-less local times, the API sends UTC instants.
            mapper.skip(BookingDto::setCreatedAt);
            mapper.skip(BookingDto::setUpdatedAt);
            mapper.skip(BookingDto::setExpiresAt);
            mapper.skip(BookingDto::setMaxGuests);
        });
        return modelMapper;
    }
}
