package com.dev.auth.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.dev.auth.auth.RefreshTokenCookie;
import com.dev.auth.dto.LoginRequest;
import com.dev.auth.dto.RegisterRequest;
import com.dev.auth.dto.SessionResponse;
import com.dev.auth.dto.TokenResponse;
import com.dev.auth.exception.InvalidRefreshTokenException;
import com.dev.auth.security.JwtService;
import com.dev.auth.security.TokenIssuanceService;
import com.dev.auth.security.TokenPair;
import com.dev.auth.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log =
            LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;
    private final TokenIssuanceService tokenIssuanceService;
    private final JwtService jwtService;

    public AuthController(
            AuthService authService,
            RefreshTokenCookie refreshTokenCookie,
            TokenIssuanceService tokenIssuanceService,
            JwtService jwtService) {

        this.authService = authService;
        this.refreshTokenCookie = refreshTokenCookie;
        this.tokenIssuanceService = tokenIssuanceService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request) {

        log.info("Registration request received");

        ResponseEntity<Map<String, Object>> result =
                authService.register(request);

        log.info(
                "Registration request completed: status={}",
                result.getStatusCode()
        );

        return result;
    }

        @PostMapping("/login")
        public ResponseEntity<TokenResponse> login(
                @Valid @RequestBody LoginRequest request,
                HttpServletRequest httpRequest,
                HttpServletResponse response) {

        log.info("Login request received");

        TokenPair tokens = authService.loginWithRefreshToken(request, httpRequest);

        refreshTokenCookie.set(
                response,
                tokens.refreshToken(),
                tokens.refreshTokenMaxAgeSeconds()
        );

        TokenResponse body = new TokenResponse(
                tokens.accessToken(),
                "Bearer",
                jwtService.getExpirationSeconds()
        );

        log.info("Login response prepared successfully");

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
        }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(
                    name = "refresh_token",
                    required = false
            ) String refreshToken,
            HttpServletResponse response) {

        log.info("Refresh request received");

        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("Refresh rejected: refresh-token cookie missing");

            refreshTokenCookie.clear(response);

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh token is missing"
            );
        }

        try {
            TokenPair tokens = tokenIssuanceService.rotate(refreshToken);

            refreshTokenCookie.set(
                    response,
                    tokens.refreshToken(),
                    tokens.refreshTokenMaxAgeSeconds()
            );

            TokenResponse body = new TokenResponse(
                    tokens.accessToken(),
                    "Bearer",
                    jwtService.getExpirationSeconds()
            );

            log.info("Refresh completed successfully");

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(body);

        } catch (InvalidRefreshTokenException ex) {
            log.warn("Refresh rejected: refresh token invalid or expired");

            refreshTokenCookie.clear(response);

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh token is invalid or expired"
            );
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Logout rejected: unauthenticated request");

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        String authorization =
                request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null
                || !authorization.regionMatches(
                        true, 0, "Bearer ", 0, 7)) {

            log.warn("Logout rejected: Bearer token missing");

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Bearer token is missing"
            );
        }

        String token = authorization.substring(7).trim();

        if (token.isEmpty()) {
            log.warn("Logout rejected: Bearer token is empty");

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Bearer token is missing"
            );
        }

        authService.logout(token, authentication.getName());

        refreshTokenCookie.clear(response);

        log.info("Logout completed successfully");

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> getActiveSessions(
            Authentication authentication) {

        Long userId = getAuthenticatedUserId(authentication);

        log.debug("Fetching active sessions: userId={}", userId);

        List<SessionResponse> sessions =
                authService.getActiveSessions(userId);

        log.debug(
                "Active sessions fetched: userId={}, count={}",
                userId,
                sessions.size()
        );

        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @PathVariable UUID sessionId,
            Authentication authentication) {

        Long userId = getAuthenticatedUserId(authentication);

        log.info(
                "Session revocation requested: userId={}, sessionId={}",
                userId,
                sessionId
        );

        authService.revokeSession(sessionId, userId);

        log.info(
                "Session revoked successfully: userId={}, sessionId={}",
                userId,
                sessionId
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            HttpServletResponse response,
            Authentication authentication) {

        Long userId = getAuthenticatedUserId(authentication);

        log.info("Logout-all requested: userId={}", userId);

        authService.logoutAll(userId);

        refreshTokenCookie.clear(response);

        log.info("All sessions revoked: userId={}", userId);

        return ResponseEntity.noContent().build();
    }

    private Long getAuthenticatedUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            log.warn(
                    "Invalid authenticated principal format: principalType={}",
                    authentication.getClass().getSimpleName()
            );

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authentication principal"
            );
        }
    }
}