package com.lifeadmin.auth.service;

import com.lifeadmin.auth.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory user store for MVP / development phase.
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  TODO (Phase 2): Replace this with UserRepository (JPA / JDBC)  │
 * │  backed by PostgreSQL. All method signatures stay identical.     │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * Dummy credentials pre-loaded at startup:
 *   admin@lifeadmin.com  / Admin@1234  (ADMIN, PREMIUM)
 *   user@lifeadmin.com   / User@1234   (USER,  FREE)
 *   premium@lifeadmin.com/ Premium@123 (USER,  PREMIUM)
 */
@Slf4j
@Repository
public class DummyUserStore {

    private final Map<String, User> usersByEmail  = new ConcurrentHashMap<>();
    private final Map<String, User> usersById     = new ConcurrentHashMap<>();
    private final BCryptPasswordEncoder encoder   = new BCryptPasswordEncoder(12);

    public DummyUserStore() {
        seed();
    }

    // ─── Queries ───────────────────────────────────────────────────────────────

    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(usersByEmail.get(email.toLowerCase()));
    }

    public Optional<User> findById(String id) {
        return Optional.ofNullable(usersById.get(id));
    }

    public boolean existsByEmail(String email) {
        return usersByEmail.containsKey(email.toLowerCase());
    }

    // ─── Mutations ─────────────────────────────────────────────────────────────

    public User save(User user) {
        if (user.getId() == null) {
            user.setId(UUID.randomUUID().toString());
        }
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(LocalDateTime.now());
        }
        user.setUpdatedAt(LocalDateTime.now());
        usersByEmail.put(user.getEmail().toLowerCase(), user);
        usersById.put(user.getId(), user);
        log.debug("Saved user: {} [{}]", user.getEmail(), user.getId());
        return user;
    }

    public void updateLastLogin(String userId) {
        findById(userId).ifPresent(u -> {
            u.setLastLoginAt(LocalDateTime.now());
            save(u);
        });
    }

    // ─── Seed ──────────────────────────────────────────────────────────────────

    private void seed() {
        save(User.builder()
                .id("dummy-admin-001")
                .email("admin@lifeadmin.com")
                .passwordHash(encoder.encode("Admin@1234"))
                .fullName("Admin User")
                .role(User.Role.ADMIN)
                .tier(User.Tier.PREMIUM)
                .enabled(true)
                .emailVerified(true)
                .createdAt(LocalDateTime.now())
                .build());

        save(User.builder()
                .id("dummy-user-001")
                .email("user@lifeadmin.com")
                .passwordHash(encoder.encode("User@1234"))
                .fullName("Free Tier User")
                .role(User.Role.USER)
                .tier(User.Tier.FREE)
                .enabled(true)
                .emailVerified(true)
                .createdAt(LocalDateTime.now())
                .build());

        save(User.builder()
                .id("dummy-user-002")
                .email("premium@lifeadmin.com")
                .passwordHash(encoder.encode("Premium@123"))
                .fullName("Premium User")
                .role(User.Role.USER)
                .tier(User.Tier.PREMIUM)
                .enabled(true)
                .emailVerified(true)
                .createdAt(LocalDateTime.now())
                .build());

        log.info("DummyUserStore seeded with {} users", usersById.size());
    }
}
