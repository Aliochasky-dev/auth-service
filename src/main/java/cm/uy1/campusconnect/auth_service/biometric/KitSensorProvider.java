package cm.uy1.campusconnect.auth_service.biometric;


import cm.uy1.campusconnect.auth_service.biometric.dto.EnrollRequest;
import cm.uy1.campusconnect.auth_service.biometric.dto.EnrollResult;
import cm.uy1.campusconnect.auth_service.biometric.dto.VerifyRequest;
import cm.uy1.campusconnect.auth_service.biometric.dto.VerifyResult;
import cm.uy1.campusconnect.auth_service.config.BiometricProperties;
import cm.uy1.campusconnect.auth_service.entity.BiometricCredential;
import cm.uy1.campusconnect.auth_service.enums.BiometricType;
import cm.uy1.campusconnect.auth_service.enums.ProviderType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class KitSensorProvider implements BiometricEnroller, BiometricVerifier {

    private final BiometricProperties properties;

    public KitSensorProvider(BiometricProperties properties) {
        this.properties = properties;
    }

    @Override
    public ProviderType provider() {
        return ProviderType.KIT;
    }

    @Override
    public boolean supports(BiometricType type) {
        return properties.getKit().isEnabled()
                && (type == BiometricType.FACE || type == BiometricType.FINGERPRINT);
    }

    @Override
    public EnrollResult enroll(UUID userId, EnrollRequest request) {
        throw new UnsupportedOperationException("KitSensorProvider en attente du kit matériel.");
    }

    @Override
    public VerifyResult verify(UUID userId, BiometricCredential credential, VerifyRequest request) {
        return VerifyResult.failure("KIT_NOT_READY", null);
    }
}