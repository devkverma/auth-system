package com.dev.auth.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.dev.auth.dto.LoginRequest;
import com.dev.auth.dto.RegisterRequest;
import com.dev.auth.dto.SessionResponse;
import com.dev.auth.model.Session;
import com.dev.auth.model.User;
import com.dev.auth.repository.UserRepository;
import com.dev.auth.security.JwtService;
import com.dev.auth.security.TokenIssuanceService;
import com.dev.auth.security.TokenPair;

import io.jsonwebtoken.JwtException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class AuthService {

    private static final Logger log =
            LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final JwtService jwtService;
    private final TokenIssuanceService tokenIssuanceService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SessionService sessionService,
            TokenIssuanceService tokenIssuanceService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.tokenIssuanceService = tokenIssuanceService;
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> register(
            RegisterRequest request) {

        String email = request.email().trim().toLowerCase();
        String username = request.username().trim().toLowerCase();

        log.info("Registration attempt received");

        if (userRepository.existsByEmail(email)) {
            log.warn("Registration rejected: email already registered");

            throw new ResponseStatusException(
                    CONFLICT,
                    "Email is already registered"
            );
        }

        if (userRepository.existsByUsername(username)) {
            log.warn("Registration rejected: username already taken");

            throw new ResponseStatusException(
                    CONFLICT,
                    "Username is already taken"
            );
        }

        User user = new User(
                request.fullName() == null
                        ? null
                        : request.fullName().trim(),
                username,
                email,
                passwordEncoder.encode(request.password())
        );

        userRepository.save(user);

        log.info("Registration completed successfully: userId={}",
                user.getId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "User created successfully"
                ));
    }

    @Transactional
    public TokenPair loginWithRefreshToken(LoginRequest request) {

        String email = request.email().trim().toLowerCase();

        log.info("Login attempt received");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Login rejected: invalid credentials");

                    return new BadCredentialsException(
                            "Invalid email or password");
                });

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash())) {

            log.warn("Login rejected: invalid credentials");

            throw new BadCredentialsException(
                    "Invalid email or password");
        }

        log.info("Login credentials validated: userId={}", user.getId());

        Session session = sessionService.createSession(
                user.getId(),
                null
        );

        log.info(
                "Session created: userId={}, sessionId={}, expiresAt={}",
                user.getId(),
                session.getId(),
                session.getExpiresAt()
        );

        TokenPair tokens = tokenIssuanceService.issueForLogin(
                session,
                user.getEmail()
        );

        log.info(
                "Login completed: userId={}, sessionId={}",
                user.getId(),
                session.getId()
        );

        return tokens;
    }

    @Transactional
    public void logout(String token, String authenticatedUserId) {

        Long userId;

        try {
            userId = Long.parseLong(authenticatedUserId);
        } catch (NumberFormatException ex) {
            log.warn("Logout rejected: invalid authenticated principal");

            throw new BadCredentialsException(
                    "Invalid authenticated user");
        }

        UUID sessionId;

        try {
            sessionId = jwtService.extractSessionId(token);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn(
                    "Logout rejected: invalid access token, exceptionType={}",
                    ex.getClass().getSimpleName()
            );

            throw new BadCredentialsException(
                    "Invalid authentication token");
        }

        log.info(
                "Logout requested: userId={}, sessionId={}",
                userId,
                sessionId
        );

        // This must verify that the session belongs to the user.
        sessionService.revokeSession(sessionId, userId);

        // Revoke the associated refresh-token records as well.
        tokenIssuanceService.revokeAllForSession(sessionId);

        log.info(
                "Logout completed: userId={}, sessionId={}",
                userId,
                sessionId
        );
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> getActiveSessions(Long userId) {

        log.debug("Fetching active sessions: userId={}", userId);

        List<SessionResponse> sessions = sessionService
                .getActiveSessions(userId)
                .stream()
                .map(session -> new SessionResponse(
                        session.getId(),
                        session.getCreatedAt(),
                        session.getLastSeenAt(),
                        session.getExpiresAt(),
                        session.getDeviceName()
                ))
                .toList();

        log.debug(
                "Active sessions fetched: userId={}, count={}",
                userId,
                sessions.size()
        );

        return sessions;
    }

    @Transactional
    public void revokeSession(UUID sessionId, Long userId) {

        log.info(
                "Session revocation requested: userId={}, sessionId={}",
                userId,
                sessionId
        );

        sessionService.revokeSession(sessionId, userId);
        tokenIssuanceService.revokeAllForSession(sessionId);

        log.info(
                "Session and refresh tokens revoked: userId={}, sessionId={}",
                userId,
                sessionId
        );
    }

    @Transactional
    public int logoutAll(Long userId) {

        log.info("Logout-all requested: userId={}", userId);

        int revokedCount = sessionService.revokeAllActiveSessions(userId);

        // See the note below about revoking refresh-token records.
        tokenIssuanceService.revokeAllForUser(userId);

        log.info(
                "Logout-all completed: userId={}, revokedSessions={}",
                userId,
                revokedCount
        );

        return revokedCount;
    }
}