package cm.uy1.campusconnect.auth_service.repository;


import cm.uy1.campusconnect.auth_service.entity.BiometricChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BiometricChallengeRepository extends JpaRepository<BiometricChallenge, UUID> {
    Optional<BiometricChallenge> findByIdAndNonce(UUID id, String nonce);
}
