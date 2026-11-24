package cm.uy1.campusconnect.auth_service.biometric;

import cm.uy1.campusconnect.auth_service.config.BiometricProperties;
import cm.uy1.campusconnect.auth_service.entity.BiometricChallenge;
import cm.uy1.campusconnect.auth_service.enums.BiometricType;
import cm.uy1.campusconnect.auth_service.repository.BiometricChallengeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class ChallengeService {

    private final BiometricChallengeRepository challengeRepository;
    private final BiometricProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public ChallengeService(
            BiometricChallengeRepository challengeRepository,
            BiometricProperties properties) {
        this.challengeRepository = challengeRepository;
        this.properties = properties;
    }

    @Transactional
    public BiometricChallenge issueChallenge(UUID userId, BiometricType type) {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        // On génère un nonce propre en Base64 URL safe
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getChallengeTtlSeconds());

        BiometricChallenge challenge = BiometricChallenge.builder()
                .userId(userId)
                .type(type)
                .nonce(nonce)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        return challengeRepository.save(challenge);
    }

    @Transactional
    public void consumeChallenge(UUID challengeId, String nonce, UUID expectedUserId, BiometricType expectedType) {
        // 1. On cherche par ID
        BiometricChallenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("ERREUR: L'ID du challenge n'existe pas en base de données."));

        // 2. On compare le nonce
        if (nonce == null || !challenge.getNonce().equals(nonce.trim())) {
            throw new IllegalArgumentException("ERREUR: Le nonce ne correspond pas. (Envoyé: '" + nonce + "' | En base: '" + challenge.getNonce() + "')");
        }

        // 3. Le reste des vérifications
        if (challenge.isUsed()) {
            throw new IllegalStateException("ERREUR: Ce challenge a déjà été utilisé.");
        }
        if (Instant.now().isAfter(challenge.getExpiresAt())) {
            throw new IllegalStateException("ERREUR: Le challenge a expiré.");
        }
    }     // ... (garde le reste de la méthode tel quel)
}