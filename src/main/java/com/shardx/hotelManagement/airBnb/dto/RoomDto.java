package com.shardx.hotelManagement.airBnb.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RoomDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    private String type;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal basePrice;

    private String[] photos;

    private String[] amenities;

    @NotNull
    @Min(1)
    @Max(1000)
    private Integer totalCount;

    @NotNull
    @Min(1)
    @Max(20)
    private Integer capacity;
}
