package com.lifeadmin.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory refresh token store.
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  TODO (Phase 2): Replace with Redis SETEX for distributed        │
 * │  invalidation, multi-device support, and automatic TTL cleanup.  │
 * └──────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Component
@EnableScheduling
public class RefreshTokenStore {

    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    // token → TokenEntry(userId, expiry)
    private final Map<String, TokenEntry> store = new ConcurrentHashMap<>();

    record TokenEntry(String userId, Instant expiry) {
        boolean isExpired() { return Instant.now().isAfter(expiry); }
    }

    public void save(String token, String userId) {
        Instant expiry = Instant.now().plusMillis(refreshTokenExpiryMs);
        store.put(token, new TokenEntry(userId, expiry));
    }

    public Optional<String> getUserId(String token) {
        TokenEntry entry = store.get(token);
        if (entry == null || entry.isExpired()) {
            store.remove(token);
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    public void revoke(String token) {
        store.remove(token);
        log.debug("Revoked refresh token");
    }

    public void revokeAllForUser(String userId) {
        store.entrySet().removeIf(e -> e.getValue().userId().equals(userId));
        log.debug("Revoked all refresh tokens for user: {}", userId);
    }

    // Purge expired tokens every 30 minutes
    @Scheduled(fixedDelay = 1_800_000)
    public void purgeExpired() {
        int before = store.size();
        store.entrySet().removeIf(e -> e.getValue().isExpired());
        log.debug("Purged {} expired refresh tokens", before - store.size());
    }
}
