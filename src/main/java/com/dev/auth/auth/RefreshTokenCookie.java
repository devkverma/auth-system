package com.dev.auth.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;

@Component
public class RefreshTokenCookie {

    private static final String COOKIE_NAME = "refresh_token";

    private final boolean secure;

    public RefreshTokenCookie(
            @Value("${app.cookie.secure:true}") boolean secure) {
        this.secure = secure;
    }

    public void set(
            HttpServletResponse response,
            String token,
            long maxAgeSeconds) {

        ResponseCookie cookie = ResponseCookie
                .from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/auth")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
                .from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/auth")
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString());
    }
}