package com.lifeadmin.auth.service;

import com.lifeadmin.auth.dto.AuthDtos.*;
import com.lifeadmin.auth.model.User;
import com.lifeadmin.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final DummyUserStore userStore;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    // ─── Register ──────────────────────────────────────────────────────────────

    public AuthResponse register(RegisterRequest req) {
        String email = req.getEmail().toLowerCase().trim();

        if (userStore.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName().trim())
                .role(User.Role.USER)
                .tier(User.Tier.FREE)
                .enabled(true)
                .emailVerified(false)   // TODO: send verification email
                .authProvider(User.AuthProvider.LOCAL)
                .createdAt(LocalDateTime.now())
                .build();

        userStore.save(user);
        log.info("Registered new user: {}", email);

        return buildAuthResponse(user);
    }

    // ─── Login ─────────────────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest req) {
        String email = req.getEmail().toLowerCase().trim();

        User user = userStore.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled. Please contact support.");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password.");
        }

        userStore.updateLastLogin(user.getId());
        log.info("User logged in: {}", email);

        return buildAuthResponse(user);
    }

    // ─── Refresh ───────────────────────────────────────────────────────────────

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Invalid or expired refresh token.");
        }

        String userId = refreshTokenStore.getUserId(refreshToken)
                .orElseThrow(() -> new BadCredentialsException("Refresh token not found or expired."));

        User user = userStore.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found."));

        // Rotate: revoke old, issue new
        refreshTokenStore.revoke(refreshToken);
        log.info("Refreshed tokens for user: {}", user.getEmail());

        return buildAuthResponse(user);
    }

    // ─── Logout ────────────────────────────────────────────────────────────────

    public void logout(String refreshToken) {
        refreshTokenStore.revoke(refreshToken);
        log.info("User logged out (refresh token revoked)");
    }

    public void logoutAll(String userId) {
        refreshTokenStore.revokeAllForUser(userId);
        log.info("Revoked all sessions for user: {}", userId);
    }

    // ─── Change Password ───────────────────────────────────────────────────────

    public void changePassword(String userId, ChangePasswordRequest req) {
        User user = userStore.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userStore.save(user);

        // Invalidate all refresh tokens — force re-login on other devices
        refreshTokenStore.revokeAllForUser(userId);
        log.info("Password changed for user: {}", userId);
    }

    // ─── Validate Token (used by other services / gateway) ────────────────────

    public UserDto validateToken(String accessToken) {
        if (!jwtUtil.isTokenValid(accessToken) || !jwtUtil.isAccessToken(accessToken)) {
            throw new BadCredentialsException("Invalid or expired access token.");
        }
        String email = jwtUtil.extractEmail(accessToken);
        User user = userStore.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found."));
        return UserDto.from(user);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getEmail(),
                user.getRole().name(), user.getTier().name()
        );
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getEmail());

        refreshTokenStore.save(refreshToken, user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getAccessTokenExpirySeconds())
                .user(UserDto.from(user))
                .build();
    }
}
