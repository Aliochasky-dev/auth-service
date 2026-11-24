package cm.uy1.campusconnect.auth_service.biometric;


import cm.uy1.campusconnect.auth_service.biometric.dto.VerifyRequest;
import cm.uy1.campusconnect.auth_service.biometric.dto.VerifyResult;
import cm.uy1.campusconnect.auth_service.entity.BiometricCredential;

import java.util.UUID;

public interface BiometricVerifier extends BiometricProvider {
    VerifyResult verify(UUID userId, BiometricCredential credential, VerifyRequest request);
}