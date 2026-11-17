package com.shardx.hotelManagement.airBnb.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.shardx.hotelManagement.airBnb.entity.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingStatusResponseDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BookingStatus bookingStatus;
}
