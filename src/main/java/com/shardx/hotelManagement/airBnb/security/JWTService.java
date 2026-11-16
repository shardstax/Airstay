package com.shardx.hotelManagement.airBnb.security;

import com.shardx.hotelManagement.airBnb.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * Issues and verifies JWTs. Access and refresh tokens carry a "type" claim so a long-lived
 * refresh token can never be used as a bearer token on API calls.
 */
@Service
public class JWTService {

    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(10);
    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(180);

    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    @Value("${jwt.secretKey}")
    private String jwtSecretKey;

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(jwtSecretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(TYPE_CLAIM, ACCESS_TYPE)
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_TTL.toMillis()))
                .signWith(getSecretKey())
                .compact();
    }

    public String generateRefreshToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(TYPE_CLAIM, REFRESH_TYPE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_TTL.toMillis()))
                .signWith(getSecretKey())
                .compact();
    }

    public Long getUserIdFromAccessToken(String token) {
        return getUserId(token, ACCESS_TYPE);
    }

    public Long getUserIdFromRefreshToken(String token) {
        return getUserId(token, REFRESH_TYPE);
    }

    private Long getUserId(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!expectedType.equals(claims.get(TYPE_CLAIM, String.class))) {
            throw new JwtException("Invalid token type");
        }
        return Long.valueOf(claims.getSubject());
    }
}
