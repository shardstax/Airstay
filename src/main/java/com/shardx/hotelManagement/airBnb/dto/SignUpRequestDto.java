package com.shardx.hotelManagement.airBnb.dto;

import com.shardx.hotelManagement.airBnb.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignUpRequestDto {

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    /** BCrypt only uses the first 72 bytes, so longer passwords are rejected. */
    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotBlank
    @Size(max = 100)
    private String name;

    /** GUEST (default) or HOTEL_MANAGER. Managers can also book stays as guests. */
    private Role accountType;
}
