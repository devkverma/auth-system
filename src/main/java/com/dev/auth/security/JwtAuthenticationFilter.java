package com.dev.auth.security;

import com.dev.auth.service.SessionService;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.dev.auth.service.SessionService.SessionStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SessionService sessionService;
    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    public JwtAuthenticationFilter(
            JwtService jwtService,
            SessionService sessionService) {

        this.jwtService = jwtService;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();

        if (token.isEmpty()
                || SecurityContextHolder.getContext()
                        .getAuthentication() != null) {

            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (!jwtService.isTokenValid(token)) {
                log.warn("Authentication rejected: JWT is invalid or expired. remoteAddress={}",
                        request.getRemoteAddr());

                filterChain.doFilter(request, response);
                return;
            }

            Long userId = jwtService.extractUserId(token);
            UUID sessionId = jwtService.extractSessionId(token);

            SessionStatus status = sessionService.getSessionStatus(sessionId, userId);

            if (status != SessionStatus.ACTIVE) {
                switch (status) {
                    case REVOKED ->
                            log.warn("Authentication rejected: session revoked. userId={}, sessionId={}",
                                    userId, sessionId);

                    case EXPIRED ->
                            log.warn("Authentication rejected: session expired. userId={}, sessionId={}",
                                    userId, sessionId);

                    case NOT_FOUND ->
                            log.warn("Authentication rejected: session not found. userId={}, sessionId={}",
                                    userId, sessionId);

                    default -> {
                        // No action needed for ACTIVE.
                    }
                }

                filterChain.doFilter(request, response);
                return;
            }

            var authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId.toString(),
                            null,
                            List.of()
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource()
                            .buildDetails(request)
            );

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

        } catch (JwtException | IllegalArgumentException ex) {
            // Malformed, expired, or otherwise invalid JWT.
            SecurityContextHolder.clearContext();

        } catch (DataAccessException ex) {
            // Session state could not be verified. Fail closed.
            SecurityContextHolder.clearContext();
            response.setStatus(
                HttpStatus.SERVICE_UNAVAILABLE.value()
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}