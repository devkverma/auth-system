package com.dev.auth.security;

import java.util.UUID;
import com.dev.auth.model.Session;
import com.dev.auth.model.RefreshToken;
import com.dev.auth.repository.RefreshTokenRepository;
import com.dev.auth.exception.InvalidRefreshTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TokenIssuanceService {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;

    private final long refreshTokenLifetimeSeconds;

    private static final Logger log =
        LoggerFactory.getLogger(TokenIssuanceService.class);

    public TokenIssuanceService(
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-expiration-seconds:604800}")
            long refreshTokenLifetimeSeconds) {

        if (refreshTokenLifetimeSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Refresh token lifetime must be positive");
        }

        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenLifetimeSeconds = refreshTokenLifetimeSeconds;
    }

        @Transactional
        public TokenPair issueForLogin(
                Session session,
                String email) {

        Instant now = Instant.now();

        log.info(
                "Issuing tokens for login: userId={}, sessionId={}",
                session.getUser().getId(),
                session.getId()
        );

        validateSession(session, now);

        String rawRefreshToken = refreshTokenService.generateToken();

        Instant refreshExpiresAt = calculateRefreshExpiry(
                now,
                session.getExpiresAt()
        );

        RefreshToken refreshToken = new RefreshToken(
                session,
                refreshTokenService.hashToken(rawRefreshToken),
                now,
                refreshExpiresAt
        );

        refreshTokenRepository.save(refreshToken);

        String accessToken = jwtService.generateToken(
                session.getUser().getId(),
                email,
                session.getId()
        );

        long maxAge = Duration.between(now, refreshExpiresAt)
                .toSeconds();

        log.info(
                "Tokens issued successfully: userId={}, sessionId={}, " +
                        "accessTokenLifetimeSeconds={}, refreshTokenExpiresAt={}",
                session.getUser().getId(),
                session.getId(),
                jwtService.getExpirationSeconds(),
                refreshExpiresAt
        );

        return new TokenPair(
                accessToken,
                rawRefreshToken,
                Math.max(0, maxAge)
        );
        }

        @Transactional
        public TokenPair rotate(String rawRefreshToken) {

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
                log.warn("Refresh rejected: refresh token is missing");
                throw new InvalidRefreshTokenException();
        }

        String tokenHash = refreshTokenService.hashToken(rawRefreshToken);

        log.debug("Looking up refresh token by hash");

        RefreshToken current = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> {
                        log.warn("Refresh rejected: token hash not found");
                        return new InvalidRefreshTokenException();
                });

        Instant now = Instant.now();

        if (current.getRevokedAt() != null) {
                log.warn(
                        "Refresh rejected: token already revoked, " +
                                "sessionId={}, refreshTokenId={}",
                        current.getSession().getId(),
                        current.getId()
                );

                throw new InvalidRefreshTokenException();
        }

        if (!current.getExpiresAt().isAfter(now)) {
                log.warn(
                        "Refresh rejected: token expired, " +
                                "sessionId={}, refreshTokenId={}, expiresAt={}",
                        current.getSession().getId(),
                        current.getId(),
                        current.getExpiresAt()
                );

                throw new InvalidRefreshTokenException();
        }

        Session session = current.getSession();

        if (session.getRevokedAt() != null) {
                log.warn(
                        "Refresh rejected: session revoked, sessionId={}",
                        session.getId()
                );

                throw new InvalidRefreshTokenException();
        }

        if (!session.getExpiresAt().isAfter(now)) {
                log.warn(
                        "Refresh rejected: session expired, sessionId={}, expiresAt={}",
                        session.getId(),
                        session.getExpiresAt()
                );

                throw new InvalidRefreshTokenException();
        }

        log.info(
                "Refresh token validated: userId={}, sessionId={}, " +
                        "refreshTokenId={}",
                session.getUser().getId(),
                session.getId(),
                current.getId()
        );

        current.revoke(now);
        refreshTokenRepository.saveAndFlush(current);

        String newRawToken = refreshTokenService.generateToken();

        Instant newExpiry = calculateRefreshExpiry(
                now,
                session.getExpiresAt()
        );

        RefreshToken replacement = new RefreshToken(
                session,
                refreshTokenService.hashToken(newRawToken),
                now,
                newExpiry
        );

        replacement = refreshTokenRepository.saveAndFlush(replacement);

        current.setReplacedBy(replacement);
        refreshTokenRepository.save(current);

        String accessToken = jwtService.generateToken(
                session.getUser().getId(),
                session.getUser().getEmail(),
                session.getId()
        );

        long maxAge = Duration.between(now, newExpiry).toSeconds();

        log.info(
                "Refresh successful: userId={}, sessionId={}, " +
                        "oldRefreshTokenId={}, newRefreshTokenId={}, " +
                        "newRefreshExpiry={}",
                session.getUser().getId(),
                session.getId(),
                current.getId(),
                replacement.getId(),
                newExpiry
        );

        return new TokenPair(
                accessToken,
                newRawToken,
                Math.max(0, maxAge)
        );
        }

        private void validateSession(Session session, Instant now) {
        if (session.getRevokedAt() != null) {
                log.warn(
                        "Token issuance rejected: session revoked, sessionId={}",
                        session.getId()
                );

                throw new InvalidRefreshTokenException();
        }

        if (!session.getExpiresAt().isAfter(now)) {
                log.warn(
                        "Token issuance rejected: session expired, " +
                                "sessionId={}, expiresAt={}",
                        session.getId(),
                        session.getExpiresAt()
                );

                throw new InvalidRefreshTokenException();
        }
        }

    private Instant calculateRefreshExpiry(
            Instant now,
            Instant sessionExpiresAt) {

        Instant tokenExpiry = now.plusSeconds(
                refreshTokenLifetimeSeconds);

        return tokenExpiry.isBefore(sessionExpiresAt)
                ? tokenExpiry
                : sessionExpiresAt;
    }

        @Transactional
        public void revokeAllForSession(UUID sessionId) {
        Instant now = Instant.now();

        int revokedCount = refreshTokenRepository
                .revokeAllActiveBySessionId(sessionId, now);

        log.info(
                "Refresh tokens revoked for session: sessionId={}, count={}",
                sessionId,
                revokedCount
        );
        }

        @Transactional
        public void revokeAllForUser(Long userId) {
        Instant now = Instant.now();

        int revokedCount = refreshTokenRepository
                .revokeAllActiveByUserId(userId, now);

        log.info(
                "Refresh tokens revoked for user: userId={}, count={}",
                userId,
                revokedCount
        );
        }
}