package com.shardx.hotelManagement.airBnb.dto;

import com.shardx.hotelManagement.airBnb.entity.HotelContactInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HotelDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String city;

    private String[] photos;

    private String[] amenities;

    private HotelContactInfo contactInfo;

    /** Read-only: a hotel only goes live through the activate endpoint. */
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Boolean active;
}
