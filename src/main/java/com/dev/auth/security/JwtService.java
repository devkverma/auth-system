package com.dev.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {

        if (expirationMs <= 0) {
            throw new IllegalArgumentException(
                    "JWT expiration must be positive"
            );
        }

        byte[] keyBytes = Decoders.BASE64.decode(secret);

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(
            Long userId,
            String email,
            UUID sessionId) {

        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("sid", sessionId.toString())
                .claim("iat", now.getEpochSecond())
                .claim("exp", expiresAt.getEpochSecond())
                .signWith(signingKey)
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    public Claims extractClaims(String token) {
        Jws<Claims> signedClaims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);

        return signedClaims.getPayload();
    }

    public Long extractUserId(String token) {
        Claims claims = extractClaims(token);

        return Long.parseLong(claims.getSubject());
    }

    public UUID extractSessionId(String token) {
        Claims claims = extractClaims(token);

        String sessionId = claims.get("sid", String.class);

        if (sessionId == null || sessionId.isBlank()) {
            throw new JwtException("Missing session ID");
        }

        try {
            return UUID.fromString(sessionId);
        } catch (IllegalArgumentException ex) {
            throw new JwtException("Invalid session ID", ex);
        }
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);

            if (claims.getSubject() == null
                    || claims.getExpiration() == null) {
                return false;
            }

            // Ensure the subject is a valid user ID.
            Long.parseLong(claims.getSubject());

            // Ensure the token contains a valid session ID.
            String sessionId = claims.get("sid", String.class);

            if (sessionId == null || sessionId.isBlank()) {
                return false;
            }

            UUID.fromString(sessionId);

            // Validate expiration using java.time.
            Instant expiration = claims.getExpiration().toInstant();

            return expiration.isAfter(Instant.now());

        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}