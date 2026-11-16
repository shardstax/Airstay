package com.shardx.hotelManagement.airBnb.security;

import com.shardx.hotelManagement.airBnb.entity.User;
import com.shardx.hotelManagement.airBnb.entity.enums.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JWTServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-long-enough-for-hs256";

    private final JWTService jwtService = new JWTService();
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "jwtSecretKey", SECRET);
        user = new User();
        user.setId(42L);
        user.setEmail("guest@example.com");
        user.setRoles(Set.of(Role.GUEST));
    }

    @Test
    void accessTokenResolvesToTheUser() {
        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.getUserIdFromAccessToken(token)).isEqualTo(42L);
    }

    @Test
    void refreshTokenResolvesToTheUser() {
        String token = jwtService.generateRefreshToken(user);

        assertThat(jwtService.getUserIdFromRefreshToken(token)).isEqualTo(42L);
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        String refreshToken = jwtService.generateRefreshToken(user);

        assertThatThrownBy(() -> jwtService.getUserIdFromAccessToken(refreshToken))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("token type");
    }

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() {
        String accessToken = jwtService.generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.getUserIdFromRefreshToken(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JWTService otherService = new JWTService();
        ReflectionTestUtils.setField(otherService, "jwtSecretKey", "another-secret-key-that-is-long-enough-for-hs256");
        String foreignToken = otherService.generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.getUserIdFromAccessToken(foreignToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateAccessToken(user);
        String[] parts = token.split("\\.");
        // Swap the payload for one claiming a different user, keeping the original signature.
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"type\":\"access\"}".getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtService.getUserIdFromAccessToken(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = Jwts.builder()
                .subject("42")
                .claim("type", "access")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> jwtService.getUserIdFromAccessToken(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
