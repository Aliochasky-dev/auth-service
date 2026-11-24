package cm.uy1.campusconnect.auth_service.biometric.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, message = "Le mot de passe doit faire au moins 8 caractères") String password
) {}