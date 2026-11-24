package cm.uy1.campusconnect.auth_service.biometric;

import cm.uy1.campusconnect.auth_service.enums.BiometricType;
import cm.uy1.campusconnect.auth_service.enums.ProviderType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BiometricProviderRegistry {

    private final List<BiometricProvider> providers;

    public BiometricProviderRegistry(List<BiometricProvider> providers) {
        this.providers = providers;
    }

    public BiometricEnroller getEnroller(ProviderType providerType, BiometricType biometricType) {
        return providers.stream()
                .filter(p -> p.provider() == providerType && p.supports(biometricType))
                .filter(BiometricEnroller.class::isInstance)
                .map(BiometricEnroller.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucun BiometricEnroller disponible pour " + providerType + " / " + biometricType));
    }

    public BiometricVerifier getVerifier(ProviderType providerType, BiometricType biometricType) {
        return providers.stream()
                .filter(p -> p.provider() == providerType && p.supports(biometricType))
                .filter(BiometricVerifier.class::isInstance)
                .map(BiometricVerifier.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucun BiometricVerifier disponible pour " + providerType + " / " + biometricType));
    }
}
