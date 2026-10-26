package com.dev.auth.repository;

import com.dev.auth.model.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT rt
        FROM RefreshToken rt
        JOIN FETCH rt.session
        WHERE rt.tokenHash = :tokenHash
        """)
    Optional<RefreshToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
        UPDATE RefreshToken rt
        SET rt.revokedAt = :now
        WHERE rt.session.id = :sessionId
          AND rt.revokedAt IS NULL
        """)
    int revokeAllActiveBySessionId(
            @Param("sessionId") UUID sessionId,
            @Param("now") Instant now);

    @Modifying
    @Query("""
        UPDATE RefreshToken rt
        SET rt.revokedAt = :now
        WHERE rt.session.user.id = :userId
          AND rt.revokedAt IS NULL
        """)
    int revokeAllActiveByUserId(
            @Param("userId") Long userId,
            @Param("now") Instant now);
}