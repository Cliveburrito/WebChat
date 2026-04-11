package com.example.WebChat.auth;

import com.example.WebChat.config.AppProperties;
import com.example.WebChat.observability.RequestTracking;
import com.example.WebChat.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final AppProperties appProperties;

    @Transactional
    public String issue(User user) {
        String rawToken = generateToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(hash(rawToken))
                .user(user)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusMillis(appProperties.getSecurity().getRefreshExpirationMs()))
                .build();
        refreshTokenRepository.save(refreshToken);
        log.info("auth_refresh_issued requestId={} userId={}", RequestTracking.currentRequestId(), user.getId());
        return rawToken;
    }

    @Transactional
    public RotatedRefreshToken rotate(String rawToken) {
        Instant now = Instant.now();
        String tokenHash = hash(rawToken);
        RefreshToken current = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("invalid_refresh_token"));

        if (!current.isActive(now)) {
            log.warn("auth_refresh_failed requestId={} reason=expired_or_revoked", RequestTracking.currentRequestId());
            throw new BadCredentialsException("expired_refresh_token");
        }

        User user = current.getUser();
        user.getUsername();
        user.getEmail();
        user.getAvatarUrl();

        String replacementRawToken = generateToken();
        String replacementHash = hash(replacementRawToken);
        current.setRevokedAt(now);
        current.setReplacedByTokenHash(replacementHash);

        RefreshToken replacement = RefreshToken.builder()
                .tokenHash(replacementHash)
                .user(user)
                .createdAt(now)
                .expiresAt(now.plusMillis(appProperties.getSecurity().getRefreshExpirationMs()))
                .build();
        refreshTokenRepository.save(replacement);

        log.info("auth_refresh_rotated requestId={} userId={}", RequestTracking.currentRequestId(), user.getId());
        return new RotatedRefreshToken(user, replacementRawToken);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                log.info("auth_refresh_revoked requestId={} userId={}", RequestTracking.currentRequestId(), token.getUser().getId());
            }
        });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        int count = refreshTokenRepository.revokeAllActiveForUser(userId, Instant.now());
        log.info("auth_refresh_revoke_all requestId={} userId={} count={}", RequestTracking.currentRequestId(), userId, count);
    }

    private static String generateToken() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record RotatedRefreshToken(User user, String refreshToken) {
    }
}
