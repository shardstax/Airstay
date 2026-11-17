package com.shardx.hotelManagement.airBnb.dto;

import com.shardx.hotelManagement.airBnb.catalog.Catalog;
import com.shardx.hotelManagement.airBnb.catalog.HttpsUrls;
import com.shardx.hotelManagement.airBnb.catalog.InCatalog;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RoomDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    /** One of the room types listed by {@code GET /catalog}. */
    @NotBlank
    @InCatalog(Catalog.Kind.ROOM_TYPE)
    private String type;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal basePrice;

    @HttpsUrls
    private String[] photos;

    @InCatalog(Catalog.Kind.ROOM_AMENITY)
    @Size(max = 15)
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
