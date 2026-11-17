package com.shardx.hotelManagement.airBnb.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.shardx.hotelManagement.airBnb.entity.enums.Gender;
import com.shardx.hotelManagement.airBnb.entity.enums.Role;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class UserDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    private Gender gender;
    private LocalDate dateOfBirth;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Set<Role> roles;
}
