package cm.uy1.campusconnect.auth_service.biometric.dto;


import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank String refreshToken
) {}