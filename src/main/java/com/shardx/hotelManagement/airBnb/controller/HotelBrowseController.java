package com.shardx.hotelManagement.airBnb.controller;

import com.shardx.hotelManagement.airBnb.dto.*;
import com.shardx.hotelManagement.airBnb.service.HotelService;
import com.shardx.hotelManagement.airBnb.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Public browsing endpoints. Inputs are query parameters because browsers cannot send a body with GET. */
@RestController
@RequestMapping("/hotels")
@RequiredArgsConstructor
public class HotelBrowseController {

    private final InventoryService inventoryService;
    private final HotelService hotelService;

    @GetMapping("/search")
    @Operation(summary = "Search hotels with rooms free on every night of the stay", tags = {"Browse Hotels"})
    public ResponseEntity<Page<HotelPriceResponseDto>> searchHotels(
            @Valid @ParameterObject @ModelAttribute HotelSearchRequest hotelSearchRequest) {
        return ResponseEntity.ok(inventoryService.searchHotels(hotelSearchRequest));
    }

    @GetMapping("/{hotelId}/info")
    @Operation(summary = "Hotel details with each room type's average nightly price for the stay", tags = {"Browse Hotels"})
    public ResponseEntity<HotelInfoDto> getHotelInfo(
            @PathVariable Long hotelId,
            @Valid @ParameterObject @ModelAttribute HotelInfoRequestDto hotelInfoRequestDto) {
        return ResponseEntity.ok(hotelService.getHotelInfoById(hotelId, hotelInfoRequestDto));
    }
}
