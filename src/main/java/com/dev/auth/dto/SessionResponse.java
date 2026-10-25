package com.dev.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        String deviceName
) {}