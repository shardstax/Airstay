package com.shardx.hotelManagement.airBnb.controller;

import com.shardx.hotelManagement.airBnb.dto.BookingDto;
import com.shardx.hotelManagement.airBnb.dto.GuestDto;
import com.shardx.hotelManagement.airBnb.dto.ProfileUpdateRequestDto;
import com.shardx.hotelManagement.airBnb.dto.UserDto;
import com.shardx.hotelManagement.airBnb.service.BookingService;
import com.shardx.hotelManagement.airBnb.service.GuestService;
import com.shardx.hotelManagement.airBnb.service.UserService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final BookingService bookingService;
    private final GuestService guestService;

    @PatchMapping("/profile")
    @ApiResponse(responseCode = "204", description = "Updated")
    public ResponseEntity<Void> updateProfile(@Valid @RequestBody ProfileUpdateRequestDto profileUpdateRequestDto) {
        userService.updateProfile(profileUpdateRequestDto);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/myBookings")
    public ResponseEntity<List<BookingDto>> getMyBookings() {
        return ResponseEntity.ok(bookingService.getMyBookings());
    }

    @GetMapping("/profile")
    public ResponseEntity<UserDto> getMyProfile() {
        return ResponseEntity.ok(userService.getMyProfile());
    }

    @GetMapping("/guests")
    public ResponseEntity<List<GuestDto>> getAllGuests() {
        return ResponseEntity.ok(guestService.getAllGuests());
    }

    @PostMapping("/guests")
    @ApiResponse(responseCode = "201", description = "Created")
    public ResponseEntity<GuestDto> addNewGuest(@Valid @RequestBody GuestDto guestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guestService.addNewGuest(guestDto));
    }

    @PutMapping("guests/{guestId}")
    @ApiResponse(responseCode = "204", description = "Updated")
    public ResponseEntity<Void> updateGuest(@PathVariable Long guestId, @Valid @RequestBody GuestDto guestDto) {
        guestService.updateGuest(guestId, guestDto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("guests/{guestId}")
    @ApiResponse(responseCode = "204", description = "Deleted")
    public ResponseEntity<Void> deleteGuest(@PathVariable Long guestId) {
        guestService.deleteGuest(guestId);
        return ResponseEntity.noContent().build();
    }

}
