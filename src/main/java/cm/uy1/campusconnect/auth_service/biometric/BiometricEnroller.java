package cm.uy1.campusconnect.auth_service.biometric;


import cm.uy1.campusconnect.auth_service.biometric.dto.EnrollRequest;
import cm.uy1.campusconnect.auth_service.biometric.dto.EnrollResult;

import java.util.UUID;

public interface BiometricEnroller extends BiometricProvider {
    EnrollResult enroll(UUID userId, EnrollRequest request);
}