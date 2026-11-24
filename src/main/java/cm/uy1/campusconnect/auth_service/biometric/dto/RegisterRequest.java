package cm.uy1.campusconnect.auth_service.biometric.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** No "role" field: every self-registered account is a STUDENT. Other roles are granted by an admin. */
public record RegisterRequest(
        @Schema(example = "etudiant.test@uy1.cm")
        @NotBlank @Email @Size(max = 255) String email,

        @Schema(example = "+237670000000")
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Phone must contain 8 to 15 digits (optional leading +)") String phone,

        @Schema(example = "Password123!")
        @NotBlank @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain at least one letter and one digit") String password
) {}
