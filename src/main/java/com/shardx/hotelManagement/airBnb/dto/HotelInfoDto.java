package com.shardx.hotelManagement.airBnb.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class HotelInfoDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private HotelDto hotel;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<RoomPriceResponseDto> rooms;
}
