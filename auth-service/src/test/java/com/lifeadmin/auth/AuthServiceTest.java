package com.lifeadmin.auth;

import com.lifeadmin.auth.dto.AuthDtos.*;
import com.lifeadmin.auth.service.AuthService;
import com.lifeadmin.auth.service.DummyUserStore;
import com.lifeadmin.auth.service.RefreshTokenStore;
import com.lifeadmin.auth.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class AuthServiceTest {

    @Autowired AuthService authService;
    @Autowired DummyUserStore userStore;
    @Autowired JwtUtil jwtUtil;

    // ─── Login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Login with valid dummy credentials returns tokens")
    void loginSuccess() {
        var req = new LoginRequest("user@lifeadmin.com", "User@1234");
        AuthResponse res = authService.login(req);

        assertThat(res.getAccessToken()).isNotBlank();
        assertThat(res.getRefreshToken()).isNotBlank();
        assertThat(res.getTokenType()).isEqualTo("Bearer");
        assertThat(res.getUser().getEmail()).isEqualTo("user@lifeadmin.com");
    }

    @Test
    @DisplayName("Login with wrong password throws BadCredentials")
    void loginWrongPassword() {
        var req = new LoginRequest("user@lifeadmin.com", "wrongpassword");
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Login with unknown email throws BadCredentials")
    void loginUnknownEmail() {
        var req = new LoginRequest("nobody@lifeadmin.com", "password");
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ─── Register ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("New user registration returns auth tokens")
    void registerSuccess() {
        var req = new RegisterRequest("Test User", "newuser@test.com", "Secure@123");
        AuthResponse res = authService.register(req);

        assertThat(res.getUser().getEmail()).isEqualTo("newuser@test.com");
        assertThat(res.getUser().getTier()).isEqualTo("FREE");
        assertThat(res.getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("Duplicate registration throws IllegalArgumentException")
    void registerDuplicate() {
        var req = new RegisterRequest("Someone", "user@lifeadmin.com", "Password@123");
        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    // ─── Token ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Access token is valid and contains expected claims")
    void tokenClaims() {
        var req = new LoginRequest("admin@lifeadmin.com", "Admin@1234");
        AuthResponse res = authService.login(req);

        String token = res.getAccessToken();
        assertThat(jwtUtil.isTokenValid(token)).isTrue();
        assertThat(jwtUtil.isAccessToken(token)).isTrue();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("admin@lifeadmin.com");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
        assertThat(jwtUtil.extractTier(token)).isEqualTo("PREMIUM");
    }

    // ─── Refresh ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Refresh token produces new access token")
    void refreshSuccess() {
        var login = authService.login(new LoginRequest("user@lifeadmin.com", "User@1234"));
        AuthResponse refreshed = authService.refreshToken(login.getRefreshToken());

        assertThat(refreshed.getAccessToken()).isNotBlank();
        assertThat(refreshed.getAccessToken()).isNotEqualTo(login.getAccessToken());
    }

    @Test
    @DisplayName("Used refresh token cannot be reused (rotation)")
    void refreshTokenRotation() {
        var login = authService.login(new LoginRequest("user@lifeadmin.com", "User@1234"));
        String oldRefresh = login.getRefreshToken();

        authService.refreshToken(oldRefresh);  // rotate

        // Old token should now be invalid
        assertThatThrownBy(() -> authService.refreshToken(oldRefresh))
                .isInstanceOf(BadCredentialsException.class);
    }
}
