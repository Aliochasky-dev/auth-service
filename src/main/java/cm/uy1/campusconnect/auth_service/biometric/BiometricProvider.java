package cm.uy1.campusconnect.auth_service.biometric;
import cm.uy1.campusconnect.auth_service.enums.BiometricType;
import cm.uy1.campusconnect.auth_service.enums.ProviderType;

public interface BiometricProvider {
    ProviderType provider();
    boolean supports(BiometricType type);
}