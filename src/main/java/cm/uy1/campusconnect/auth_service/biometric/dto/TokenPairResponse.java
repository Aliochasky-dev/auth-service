package cm.uy1.campusconnect.auth_service.biometric.dto;


import java.util.List;
import java.util.UUID;

public record TokenPairResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UUID userId,
        List<String> roles
) {}