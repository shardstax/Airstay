package com.shardx.hotelManagement.airBnb.security;

import com.shardx.hotelManagement.airBnb.entity.User;
import com.shardx.hotelManagement.airBnb.exception.ResourceNotFoundException;
import com.shardx.hotelManagement.airBnb.service.UserService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JWTAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JWTService jwtService;
    private final UserService userService;

    @Autowired
    @Qualifier("handlerExceptionResolver")
    private HandlerExceptionResolver handlerExceptionResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            Long userId = jwtService.getUserIdFromAccessToken(token);
            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                authenticate(userId, request);
            }
        } catch (JwtException | IllegalArgumentException ex) {
            // Bad, expired or wrong-type token: answer 401 through the global exception handler.
            handlerExceptionResolver.resolveException(request, response, null,
                    ex instanceof JwtException ? ex : new JwtException("Invalid token", ex));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(Long userId, HttpServletRequest request) {
        User user;
        try {
            user = userService.getUserById(userId);
        } catch (ResourceNotFoundException ex) {
            // The account behind a still-valid token was deleted: treat the request as anonymous.
            return;
        }
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }
}
