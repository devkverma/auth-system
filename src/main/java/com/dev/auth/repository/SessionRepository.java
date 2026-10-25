package com.dev.auth.repository;

import com.dev.auth.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    @Query("""
        SELECT s
        FROM Session s
        WHERE s.user.id = :userId
          AND s.revokedAt IS NULL
          AND s.expiresAt > :now
        ORDER BY s.createdAt ASC
        """)
    List<Session> findActiveSessions(
        @Param("userId") Long userId,
        @Param("now") Instant now
    );

    @Query("""
        SELECT COUNT(s)
        FROM Session s
        WHERE s.user.id = :userId
          AND s.revokedAt IS NULL
          AND s.expiresAt > :now
        """)
    long countActiveSessions(
        @Param("userId") Long userId,
        @Param("now") Instant now
    );

    @Query("""
        SELECT s
        FROM Session s
        WHERE s.id = :sessionId
          AND s.user.id = :userId
        """)
    Optional<Session> findByIdAndUserId(
        @Param("sessionId") UUID sessionId,
        @Param("userId") Long userId
    );

    @Modifying
    @Query("""
        UPDATE Session s
        SET s.revokedAt = :now
        WHERE s.user.id = :userId
        AND s.revokedAt IS NULL
        AND s.expiresAt > :now
        """)
    int revokeAllActiveSessions(
            @Param("userId") Long userId,
            @Param("now") Instant now
    );
}