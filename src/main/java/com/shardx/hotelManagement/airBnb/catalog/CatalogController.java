package com.shardx.hotelManagement.airBnb.catalog;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/catalog")
public class CatalogController {

    @GetMapping
    @Operation(summary = "The fixed choices for hotel amenities, room amenities and room types", tags = {"Catalog"})
    public ResponseEntity<CatalogDto> getCatalog() {
        CatalogDto catalog = new CatalogDto(Catalog.HOTEL_AMENITIES, Catalog.ROOM_AMENITIES, Catalog.ROOM_TYPES);
        // The lists only change with a release, so browsers may keep them for an hour.
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(catalog);
    }
}
