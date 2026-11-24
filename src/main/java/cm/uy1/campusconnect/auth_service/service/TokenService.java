package cm.uy1.campusconnect.auth_service.service;

import cm.uy1.campusconnect.auth_service.biometric.dto.TokenPairResponse;
import cm.uy1.campusconnect.auth_service.entity.RefreshToken;
import cm.uy1.campusconnect.auth_service.entity.User;
import cm.uy1.campusconnect.auth_service.exception.ApiException;
import cm.uy1.campusconnect.auth_service.exception.ErrorCode;
import cm.uy1.campusconnect.auth_service.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {

    /** Identity extracted from a valid access token. */
    public record AccessTokenData(UUID userId, List<String> roles) {}

    private final SecretKey signingKey;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(
            // No default value on purpose: the secret MUST come from configuration / environment.
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds,
            @Value("${app.jwt.refresh-token-ttl-seconds:604800}") long refreshTokenTtlSeconds,
            RefreshTokenRepository refreshTokenRepository) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // Fail fast instead of silently padding with zeros (which weakens the key).
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes long");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public TokenPairResponse issueTokenPair(User user, String deviceInfo) {
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName().name())
                .toList();

        Instant now = Instant.now();
        Instant accessExp = now.plusSeconds(accessTokenTtlSeconds);

        String accessToken = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(accessExp))
                .signWith(signingKey)
                .compact();

        String rawRefresh = generateOpaqueToken();
        RefreshToken refreshEntity = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(sha256Hex(rawRefresh))
                .deviceId(deviceInfo)
                .expiresAt(now.plusSeconds(refreshTokenTtlSeconds))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshEntity);

        return new TokenPairResponse(accessToken, rawRefresh, accessTokenTtlSeconds, user.getId(), roles);
    }

    /** Validates signature and expiry, returns user id and roles. Throws ApiException when invalid. */
    public AccessTokenData parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            List<String> roles = claims.get("roles") instanceof List<?> raw
                    ? raw.stream().map(String::valueOf).toList()
                    : List.of();
            return new AccessTokenData(UUID.fromString(claims.getSubject()), roles);
        } catch (ExpiredJwtException e) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Access token expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Invalid access token");
        }
    }

    /** Kept for compatibility with existing callers. */
    public UUID parseAndValidateAccessToken(String token) {
        return parseAccessToken(token).userId();
    }

    @Transactional
    public void revokeRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(sha256Hex(rawRefreshToken))
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                });
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    /**
     * Validates a refresh token and revokes it (rotation: one use only).
     * Presenting an already revoked token is treated as theft: all the user's tokens are revoked.
     * noRollbackFor: the mass revocation must be committed even though we throw.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public RefreshToken consumeValidRefreshToken(String rawRefreshToken) {
        RefreshToken rt = refreshTokenRepository.findByTokenHash(sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Invalid refresh token"));
        if (rt.isRevoked()) {
            refreshTokenRepository.revokeAllByUserId(rt.getUserId());
            throw new ApiException(ErrorCode.TOKEN_REVOKED, "Refresh token already used or revoked");
        }
        if (Instant.now().isAfter(rt.getExpiresAt())) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Refresh token expired");
        }
        rt.setRevoked(true);
        return refreshTokenRepository.save(rt);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 hashing failed", ex);
        }
    }
}
