package com.lifeadmin.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * User domain model.
 * Currently backed by in-memory dummy store.
 * TODO: Replace with JPA @Entity + PostgreSQL repository in Phase 2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private String id;
    private String email;
    private String passwordHash;   // BCrypt hashed
    private String fullName;
    private String profilePicUrl;  // For Google OAuth users

    @Builder.Default
    private Role role = Role.USER;

    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private boolean emailVerified = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;

    // Subscription tier — drives feature gating
    @Builder.Default
    private Tier tier = Tier.FREE;

    public enum Role {
        USER, ADMIN
    }

    public enum AuthProvider {
        LOCAL, GOOGLE
    }

    public enum Tier {
        FREE, PREMIUM
    }
}
