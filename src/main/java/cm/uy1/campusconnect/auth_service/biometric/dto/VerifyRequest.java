package cm.uy1.campusconnect.auth_service.biometric.dto;

import cm.uy1.campusconnect.auth_service.enums.BiometricType;
import cm.uy1.campusconnect.auth_service.enums.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record VerifyRequest(
        @NotBlank String email,
        @NotNull UUID challengeId,
        @NotBlank String nonce,
        @NotNull BiometricType type,
        @NotNull ProviderType provider,
        @NotBlank String deviceId,
        String signatureBase64,
        List<String> imagesBase64,
        String livenessGesture,
        String deviceInfo
) {}
