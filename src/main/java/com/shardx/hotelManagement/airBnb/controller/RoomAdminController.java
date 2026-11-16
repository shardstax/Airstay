package com.shardx.hotelManagement.airBnb.controller;

import com.shardx.hotelManagement.airBnb.dto.RoomDto;
import com.shardx.hotelManagement.airBnb.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/hotels/{hotelId}/rooms")
@RequiredArgsConstructor
public class RoomAdminController {

    private final RoomService roomService;

    @PostMapping
    @Operation(summary = "Add a room type to the hotel", tags = {"Admin Rooms"})
    public ResponseEntity<RoomDto> createNewRoom(@PathVariable Long hotelId,
                                                 @Valid @RequestBody RoomDto roomDto) {
        RoomDto room = roomService.createNewRoom(hotelId, roomDto);
        return new ResponseEntity<>(room, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "List the hotel's room types", tags = {"Admin Rooms"})
    public ResponseEntity<List<RoomDto>> getAllRoomsInHotel(@PathVariable Long hotelId) {
        return ResponseEntity.ok(roomService.getAllRoomsInHotel(hotelId));
    }

    @GetMapping("/{roomId}")
    @Operation(summary = "Get a room type", tags = {"Admin Rooms"})
    public ResponseEntity<RoomDto> getRoomById(@PathVariable Long hotelId, @PathVariable Long roomId) {
        return ResponseEntity.ok(roomService.getRoomById(hotelId, roomId));
    }

    @DeleteMapping("/{roomId}")
    @Operation(summary = "Delete a room type that has no bookings", tags = {"Admin Rooms"})
    public ResponseEntity<Void> deleteRoomById(@PathVariable Long hotelId, @PathVariable Long roomId) {
        roomService.deleteRoomById(hotelId, roomId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{roomId}")
    @Operation(summary = "Update a room type; price and count changes flow into future inventory", tags = {"Admin Rooms"})
    public ResponseEntity<RoomDto> updateRoomById(@PathVariable Long hotelId, @PathVariable Long roomId,
                                                  @Valid @RequestBody RoomDto roomDto) {
        return ResponseEntity.ok(roomService.updateRoomById(hotelId, roomId, roomDto));
    }
}
