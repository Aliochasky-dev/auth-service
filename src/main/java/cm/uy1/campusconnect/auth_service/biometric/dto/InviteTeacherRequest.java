package cm.uy1.campusconnect.auth_service.biometric.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteTeacherRequest(
        @NotBlank @Email String email,
        @NotBlank String phone
) {}