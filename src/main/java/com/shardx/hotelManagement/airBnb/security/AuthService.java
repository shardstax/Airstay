package com.shardx.hotelManagement.airBnb.security;

import com.shardx.hotelManagement.airBnb.dto.LoginDto;
import com.shardx.hotelManagement.airBnb.dto.SignUpRequestDto;
import com.shardx.hotelManagement.airBnb.dto.UserDto;
import com.shardx.hotelManagement.airBnb.entity.User;
import com.shardx.hotelManagement.airBnb.entity.enums.Role;
import com.shardx.hotelManagement.airBnb.exception.ConflictException;
import com.shardx.hotelManagement.airBnb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTService jwtService;

    /** Access token for the response body, refresh token for the httpOnly cookie. */
    public record AuthTokens(String accessToken, String refreshToken) {
    }

    @Transactional
    public UserDto signUp(SignUpRequestDto signUpRequestDto) {
        String email = normaliseEmail(signUpRequestDto.getEmail());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("An account with this email already exists");
        }

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setName(signUpRequestDto.getName().trim());
        newUser.setPassword(passwordEncoder.encode(signUpRequestDto.getPassword()));
        newUser.setRoles(signUpRequestDto.getAccountType() == Role.HOTEL_MANAGER
                ? Set.of(Role.GUEST, Role.HOTEL_MANAGER)
                : Set.of(Role.GUEST));

        newUser = userRepository.save(newUser);
        return modelMapper.map(newUser, UserDto.class);
    }

    public AuthTokens login(LoginDto loginDto) {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                normaliseEmail(loginDto.getEmail()), loginDto.getPassword()
        ));
        User user = (User) authentication.getPrincipal();
        return new AuthTokens(jwtService.generateAccessToken(user), jwtService.generateRefreshToken(user));
    }

    public String refreshToken(String refreshToken) {
        Long id = jwtService.getUserIdFromRefreshToken(refreshToken);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadCredentialsException("Account no longer exists"));
        return jwtService.generateAccessToken(user);
    }

    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
