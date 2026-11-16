package com.shardx.hotelManagement.airBnb.controller;

import com.shardx.hotelManagement.airBnb.dto.LoginDto;
import com.shardx.hotelManagement.airBnb.dto.LoginResponseDto;
import com.shardx.hotelManagement.airBnb.dto.SignUpRequestDto;
import com.shardx.hotelManagement.airBnb.dto.UserDto;
import com.shardx.hotelManagement.airBnb.security.AuthService;
import com.shardx.hotelManagement.airBnb.security.JWTService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "refreshToken";

    private final AuthService authService;

    @Value("${airstay.security.refresh-cookie.secure}")
    private boolean refreshCookieSecure;

    @Value("${airstay.security.refresh-cookie.same-site}")
    private String refreshCookieSameSite;

    @PostMapping("/signup")
    @Operation(summary = "Create an account (guest or hotel manager)", tags = {"Auth"})
    @ApiResponse(responseCode = "201", description = "Created")
    public ResponseEntity<UserDto> signup(@Valid @RequestBody SignUpRequestDto signUpRequestDto) {
        return new ResponseEntity<>(authService.signUp(signUpRequestDto), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in; returns an access token and sets the refresh-token cookie", tags = {"Auth"})
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginDto loginDto,
                                                  HttpServletRequest request,
                                                  HttpServletResponse response) {
        AuthService.AuthTokens tokens = authService.login(loginDto);
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookie(request, tokens.refreshToken(), JWTService.REFRESH_TOKEN_TTL).toString());
        return ResponseEntity.ok(new LoginResponseDto(tokens.accessToken()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Get a new access token using the refresh-token cookie", tags = {"Auth"})
    public ResponseEntity<LoginResponseDto> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthenticationServiceException("Refresh token cookie is missing");
        }
        return ResponseEntity.ok(new LoginResponseDto(authService.refreshToken(refreshToken)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Clear the refresh-token cookie", tags = {"Auth"})
    @ApiResponse(responseCode = "204", description = "Logged out")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(request, "", Duration.ZERO).toString());
        return ResponseEntity.noContent().build();
    }

    /** httpOnly, scoped to the auth endpoints only, so the browser never sends it to the rest of the API. */
    private ResponseCookie refreshCookie(HttpServletRequest request, String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path(request.getContextPath() + "/auth")
                .maxAge(maxAge)
                .build();
    }
}
