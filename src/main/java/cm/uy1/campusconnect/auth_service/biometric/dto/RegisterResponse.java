package cm.uy1.campusconnect.auth_service.biometric.dto;

import java.util.UUID;

public record RegisterResponse(UUID userId, String email, String status, String role) {}
