package com.lifeadmin.auth.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    @Value("${jwt.issuer}")
    private String issuer;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ─── Token Generation ──────────────────────────────────────────────────────

    public String generateAccessToken(String userId, String email, String role, String tier) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("tier", tier);
        claims.put("type", "access");
        return buildToken(claims, email, accessTokenExpiryMs);
    }

    public String generateRefreshToken(String userId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");
        return buildToken(claims, email, refreshTokenExpiryMs);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiryMs) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(getSigningKey())
                .compact();
    }

    // ─── Token Validation ──────────────────────────────────────────────────────

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        return "access".equals(getClaim(token, c -> c.get("type", String.class)));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(getClaim(token, c -> c.get("type", String.class)));
    }

    // ─── Token Extraction ──────────────────────────────────────────────────────

    public String extractEmail(String token) {
        return getClaim(token, Claims::getSubject);
    }

    public String extractUserId(String token) {
        return getClaim(token, c -> c.get("userId", String.class));
    }

    public String extractRole(String token) {
        return getClaim(token, c -> c.get("role", String.class));
    }

    public String extractTier(String token) {
        return getClaim(token, c -> c.get("tier", String.class));
    }

    public Date extractExpiration(String token) {
        return getClaim(token, Claims::getExpiration);
    }

    public long getAccessTokenExpirySeconds() {
        return accessTokenExpiryMs / 1000;
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private <T> T getClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(parseClaims(token));
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
