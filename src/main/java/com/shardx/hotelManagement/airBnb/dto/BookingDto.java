package com.shardx.hotelManagement.airBnb.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.shardx.hotelManagement.airBnb.entity.enums.BookingStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Set;

@Data
public class BookingDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long hotelId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String hotelName;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long roomId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String roomType;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer roomsCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate checkInDate;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate checkOutDate;
    /** UTC instants (they end in Z), so a browser in any time zone reads them correctly. */
    private Instant createdAt;
    private Instant updatedAt;
    /** When the hold on the rooms ends; absent once the booking is confirmed, cancelled or expired. */
    private Instant expiresAt;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BookingStatus bookingStatus;
    /** The most guests this booking can name: the room capacity times the number of rooms. */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer maxGuests;
    private Set<GuestDto> guests;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;
}
