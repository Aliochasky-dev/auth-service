package cm.uy1.campusconnect.auth_service.biometric.dto;

import java.time.Instant;
import java.util.UUID;

public record EnrollResponse(
        UUID credentialId,
        int keyVersion,
        Instant enrolledAt
) {}