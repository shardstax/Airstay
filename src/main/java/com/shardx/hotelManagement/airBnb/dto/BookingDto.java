package com.shardx.hotelManagement.airBnb.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.shardx.hotelManagement.airBnb.entity.enums.BookingStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Data
public class BookingDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long hotelId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long roomId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer roomsCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate checkInDate;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate checkOutDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BookingStatus bookingStatus;
    private Set<GuestDto> guests;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;
}
