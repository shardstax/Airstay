package com.shardx.hotelManagement.airBnb.entity;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class HotelContactInfo {

    @Size(max = 255)
    private String address;

    /** An optional leading + or bracket, then digits with spaces, brackets or dashes: 7 to 20 characters in all. */
    @Pattern(regexp = "^[+(]?[0-9][0-9 ()-]{6,19}$", message = "must be a phone number such as +91 98765 43210")
    private String phoneNumber;

    @Email
    @Size(max = 255)
    private String email;

    /** Filled in by the server from the hotel's place as "latitude,longitude"; a client's value is ignored. */
    private String location;
}
