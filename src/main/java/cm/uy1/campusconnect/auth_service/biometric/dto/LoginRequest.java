package cm.uy1.campusconnect.auth_service.biometric.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "etudiant.test@uy1.cm") @NotBlank @Email String email,
        @Schema(example = "Password123!") @NotBlank String password) {}