package com.dev.auth.service;

import java.util.Map;
import com.dev.auth.dto.RegisterRequest;
import com.dev.auth.model.User;
import com.dev.auth.model.Session;
import com.dev.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.dev.auth.dto.LoginRequest;
import com.dev.auth.dto.TokenResponse;
import com.dev.auth.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.ResponseEntity;

import static org.springframework.http.HttpStatus.CONFLICT;

import org.springframework.http.HttpStatus;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final JwtService jwtService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        SessionService sessionService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        String username = request.username().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                CONFLICT, "Email is already registered"
            );
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Username is already taken."
            );
        }

        User user = new User(
                request.fullName() == null ? null : request.fullName().trim(),
                username,
                email,
                passwordEncoder.encode(request.password())
        );

        userRepository.save(user);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of(
                    "message" , "User created successfully"
                ));
    }

    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(
                request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        Session session = sessionService.createSession(
            user.getId(),
            null
        );

        String token = jwtService.generateToken(
            user.getId(),
            user.getEmail(),
            session.getId()
        );

        return new TokenResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds()
        );
    }
}