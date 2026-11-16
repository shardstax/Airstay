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
        });
        return modelMapper;
    }
}
