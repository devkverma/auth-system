package com.dev.auth.service;

import com.dev.auth.model.Session;
import com.dev.auth.model.User;
import com.dev.auth.repository.SessionRepository;
import com.dev.auth.repository.UserRepository;
import com.dev.auth.exception.SessionLimitExceededException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final int maxSessions;
    private final Duration sessionTtl;

    public SessionService(
            SessionRepository sessionRepository,
            UserRepository userRepository,
            @Value("${app.auth.max-sessions:3}") int maxSessions,
            @Value("${app.auth.session-ttl:PT168H}") Duration sessionTtl) {

        if (maxSessions < 1) {
            throw new IllegalArgumentException(
                "Maximum sessions must be at least 1"
            );
        }

        if (sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException(
                "Session lifetime must be positive"
            );
        }

        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.maxSessions = maxSessions;
        this.sessionTtl = sessionTtl;
    }

    @Transactional
    public Session createSession(Long userId, String deviceName) {
        // Lock this user's row until the transaction commits.
        User user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() ->
                new EntityNotFoundException("User not found")
            );

        Instant now = Instant.now();

        long activeSessions = sessionRepository.countActiveSessions(
            userId,
            now
        );

        if (activeSessions >= maxSessions) {
            throw new SessionLimitExceededException(
                "Maximum number of active sessions reached"
            );
        }

        Session session = new Session(
            user,
            now.plus(sessionTtl),
            deviceName
        );

        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public boolean isSessionActive(UUID sessionId, Long userId) {
        return sessionRepository
            .findByIdAndUserId(sessionId, userId)
            .map(Session::isActive)
            .orElse(false);
    }

    @Transactional
    public void revokeSession(UUID sessionId, Long userId) {
        Session session = sessionRepository
            .findByIdAndUserId(sessionId, userId)
            .orElseThrow(() ->
                new EntityNotFoundException("Session not found")
            );

        session.revoke();
    }

    @Transactional(readOnly = true)
    public List<Session> getActiveSessions(Long userId) {
        return sessionRepository.findActiveSessions(
            userId,
            Instant.now()
        );
    }
}