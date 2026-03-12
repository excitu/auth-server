package com.lifeadmin.auth.controller;

import com.lifeadmin.auth.dto.AuthDtos.*;
import com.lifeadmin.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/v1/auth/register
     * Register a new user account.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AuthResponse auth = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Account created successfully.", auth));
    }

    /**
     * POST /api/v1/auth/login
     * Authenticate with email + password.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse auth = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful.", auth));
    }

    /**
     * POST /api/v1/auth/refresh
     * Exchange a valid refresh token for a new access + refresh token pair.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        AuthResponse auth = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok("Tokens refreshed.", auth));
    }

    /**
     * POST /api/v1/auth/logout
     * Revoke the provided refresh token (single device logout).
     * Requires: valid access token in Authorization header.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully."));
    }

    /**
     * POST /api/v1/auth/logout-all
     * Revoke ALL refresh tokens for the current user (all devices logout).
     * Requires: valid access token in Authorization header.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(Authentication authentication) {
        String userId = (String) authentication.getDetails();
        authService.logoutAll(userId);
        return ResponseEntity.ok(ApiResponse.ok("Logged out from all devices."));
    }

    /**
     * GET /api/v1/auth/me
     * Return current authenticated user's profile.
     * Requires: valid access token in Authorization header.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> me(Authentication authentication) {
        String userId = (String) authentication.getDetails();
        UserDto user = authService.validateToken(extractToken(authentication));
        return ResponseEntity.ok(ApiResponse.ok("Profile fetched.", user));
    }

    /**
     * POST /api/v1/auth/change-password
     * Change password for authenticated user.
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        String userId = (String) authentication.getDetails();
        authService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("Password changed. Please log in again."));
    }

    /**
     * GET /api/v1/auth/validate
     * Lightweight token validation endpoint for other microservices / gateway.
     * Returns the user info embedded in the token.
     */
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<UserDto>> validate(Authentication authentication) {
        String userId = (String) authentication.getDetails();
        UserDto user = authService.validateToken(extractBearerToken(authentication));
        return ResponseEntity.ok(ApiResponse.ok("Token is valid.", user));
    }

    // Helper: pull raw token from Authentication principal (email = subject)
    private String extractToken(Authentication auth) {
        // In this setup the raw token is not re-attached to Authentication;
        // /validate just uses the authenticated context from JwtAuthFilter.
        // The actual user is re-fetched from the store via email.
        return auth.getName(); // email
    }

    private String extractBearerToken(Authentication auth) {
        return auth.getName();
    }
}
