package com.shardx.hotelManagement.airBnb.dto;

import com.shardx.hotelManagement.airBnb.catalog.Catalog;
import com.shardx.hotelManagement.airBnb.catalog.HttpsUrls;
import com.shardx.hotelManagement.airBnb.catalog.InCatalog;
import com.shardx.hotelManagement.airBnb.entity.HotelContactInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HotelDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @NotBlank
    @Size(min = 2, max = 100)
    private String name;

    /**
     * The id of the real place this hotel is in, taken from {@code GET /places}. The server looks the place up and fills in
     * {@link #city}; a place that does not exist is refused.
     */
    @NotNull
    private Long placeId;

    /** Read-only: the place's display label, e.g. "Pune, Maharashtra, India". Search matches on this text. */
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String city;

    @HttpsUrls
    private String[] photos;

    @InCatalog(Catalog.Kind.HOTEL_AMENITY)
    @Size(max = 15)
    private String[] amenities;

    @Valid
    private HotelContactInfo contactInfo;

    /** Read-only: a hotel only goes live through the activate endpoint. */
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Boolean active;
}
