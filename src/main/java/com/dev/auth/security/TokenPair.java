package com.dev.auth.security;

public record TokenPair(
        String accessToken,
        String refreshToken,
        long refreshTokenMaxAgeSeconds
) {
}