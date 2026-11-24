package cm.uy1.campusconnect.auth_service.biometric.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerifyCodeRequest(
        @NotBlank @Email String email,
        @NotBlank String code
) {}