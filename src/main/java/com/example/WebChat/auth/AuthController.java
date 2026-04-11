package com.example.WebChat.auth;

import com.example.WebChat.auth.dto.AuthTokens;
import com.example.WebChat.auth.dto.JwtAuthenticationResponse;
import com.example.WebChat.auth.dto.LoginUserRequest;
import com.example.WebChat.auth.dto.RegisterUserRequest;
import com.example.WebChat.config.AppProperties;
import com.example.WebChat.observability.TrackingLog;
import com.example.WebChat.user.dto.UserResponse;
import com.example.WebChat.user.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid; // <--- 1. Import this!
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private static final String REFRESH_COOKIE_NAME = "webchat_refresh";

    private final UserService userService;
    private final TrackingLog trackingLog;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/register")
    public ResponseEntity<JwtAuthenticationResponse> register(
            @Valid @RequestBody RegisterUserRequest registerRequest,
            HttpServletResponse response
    ) {
        trackingLog.checkpoint("controller.enter", "endpoint", "auth.register");
        AuthTokens authTokens = userService.register(registerRequest);
        addRefreshCookie(response, authTokens.refreshToken());
        messagingTemplate.convertAndSend("/topic/users", authTokens.user());
        return ResponseEntity.ok(new JwtAuthenticationResponse(authTokens.accessToken(), authTokens.user()));
    }

    @PostMapping("/login")
    public ResponseEntity<JwtAuthenticationResponse> login(
            @Valid @RequestBody LoginUserRequest loginRequest,
            HttpServletResponse response
    ) {
        trackingLog.checkpoint("controller.enter", "endpoint", "auth.login");
        AuthTokens authTokens = userService.login(loginRequest);
        addRefreshCookie(response, authTokens.refreshToken());
        return ResponseEntity.ok(new JwtAuthenticationResponse(authTokens.accessToken(), authTokens.user()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtAuthenticationResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        trackingLog.checkpoint("controller.enter", "endpoint", "auth.refresh");
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("auth_refresh_failed reason=missing_cookie");
            clearRefreshCookie(response);
            return ResponseEntity.status(401).build();
        }

        try {
            RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotate(refreshToken);
            var user = rotated.user();
            String accessToken = jwtService.generateToken(new com.example.WebChat.auth.dto.CustomPrincipal(
                    user.getId(),
                    user.getUsername(),
                    null,
                    user.isStealthMode(),
                    user.isEnabled(),
                    java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
            ));
            UserResponse userResponse = UserResponse.fromEntity(user);
            addRefreshCookie(response, rotated.refreshToken());
            return ResponseEntity.ok(new JwtAuthenticationResponse(accessToken, userResponse));
        } catch (BadCredentialsException ex) {
            log.warn("auth_refresh_failed reason={}", ex.getMessage());
            clearRefreshCookie(response);
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        trackingLog.checkpoint("controller.enter", "endpoint", "auth.logout");
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    private void addRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(appProperties.getSecurity().isRefreshCookieSecure())
                .sameSite(appProperties.getSecurity().getRefreshCookieSameSite())
                .path("/")
                .maxAge(Duration.ofMillis(appProperties.getSecurity().getRefreshExpirationMs()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(appProperties.getSecurity().isRefreshCookieSecure())
                .sameSite(appProperties.getSecurity().getRefreshCookieSameSite())
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
