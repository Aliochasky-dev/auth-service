package cm.uy1.campusconnect.auth_service.repository;


import cm.uy1.campusconnect.auth_service.entity.BiometricCredential;
import cm.uy1.campusconnect.auth_service.enums.BiometricType;
import cm.uy1.campusconnect.auth_service.enums.ProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BiometricCredentialRepository extends JpaRepository<BiometricCredential, UUID> {

    Optional<BiometricCredential> findFirstByUserIdAndTypeAndProviderAndDeviceIdAndIsActiveTrue(
            UUID userId, BiometricType type, ProviderType provider, String deviceId);

    List<BiometricCredential> findAllByUserIdAndIsActiveTrue(UUID userId);

    Optional<BiometricCredential> findByIdAndUserId(UUID id, UUID userId);
}