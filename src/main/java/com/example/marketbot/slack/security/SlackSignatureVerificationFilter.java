package com.example.marketbot.slack.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/** Slack webhook 진입점에 도달하기 전에 요청 서명을 검증합니다. */
@Component
@RequiredArgsConstructor
public class SlackSignatureVerificationFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/slack/interaction",
            "/slack/command"
    );

    private final SlackSignatureVerifier signatureVerifier;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        byte[] rawBody = request.getInputStream().readAllBytes();
        String timestamp = request.getHeader("X-Slack-Request-Timestamp");
        String signature = request.getHeader("X-Slack-Signature");

        if (!signatureVerifier.isValid(timestamp, signature, rawBody)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid Slack signature");
            return;
        }

        filterChain.doFilter(new CachedBodyHttpServletRequest(request, rawBody), response);
    }
}
