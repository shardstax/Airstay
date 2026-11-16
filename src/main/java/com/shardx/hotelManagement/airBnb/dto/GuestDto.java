package com.shardx.hotelManagement.airBnb.dto;

import com.shardx.hotelManagement.airBnb.entity.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class GuestDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    private String name;

    private Gender gender;

    @Past
    private LocalDate dateOfBirth;
}
