package cm.uy1.campusconnect.auth_service.biometric.dto;



import cm.uy1.campusconnect.auth_service.enums.BiometricType;
import cm.uy1.campusconnect.auth_service.enums.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record EnrollRequest(
        @NotNull BiometricType type,
        @NotNull ProviderType provider,
        @NotBlank String deviceId,
        String publicKeyBase64,
        List<String> imagesBase64
) {}
