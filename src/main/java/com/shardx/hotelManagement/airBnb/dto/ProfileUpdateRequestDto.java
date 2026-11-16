package com.shardx.hotelManagement.airBnb.dto;

import com.shardx.hotelManagement.airBnb.entity.enums.Gender;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProfileUpdateRequestDto {

    @Size(min = 1, max = 100)
    private String name;

    @Past
    private LocalDate dateOfBirth;

    private Gender gender;
}
