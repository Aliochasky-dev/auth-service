package cm.uy1.campusconnect.auth_service.biometric.dto;
import java.time.Instant;
import java.util.UUID;

public record EnrollResult(
        UUID credentialId,
        byte[] templateEncrypted,
        byte[] publicKey,
        int keyVersion,
        Instant enrolledAt
) {}
