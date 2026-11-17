package com.shardx.hotelManagement.airBnb.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** The fixed choices for amenities and room types (see {@link Catalog}). */
public record CatalogDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> hotelAmenities,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> roomAmenities,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> roomTypes) {
}
