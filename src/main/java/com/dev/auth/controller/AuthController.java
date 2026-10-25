package com.dev.auth.controller;

import java.util.Map;
import java.util.UUID;
import com.dev.auth.dto.RegisterRequest;
import com.dev.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.dev.auth.dto.LoginRequest;
import com.dev.auth.dto.TokenResponse;
import com.dev.auth.dto.SessionResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Map<String, Object>> register(
        @Valid @RequestBody RegisterRequest request
    ) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            Authentication authentication
    ) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = authorization.substring(7).trim();

        authService.logout(token, authentication.getName());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> getActiveSessions(
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());

        List<SessionResponse> sessions =
                authService.getActiveSessions(userId);

        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());

        authService.revokeSession(sessionId, userId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());

        authService.logoutAll(userId);

        return ResponseEntity.noContent().build();
    }
}