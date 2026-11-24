package cm.uy1.campusconnect.auth_service.service;

import cm.uy1.campusconnect.auth_service.biometric.BiometricEnroller;
import cm.uy1.campusconnect.auth_service.biometric.BiometricProviderRegistry;
import cm.uy1.campusconnect.auth_service.biometric.BiometricVerifier;
import cm.uy1.campusconnect.auth_service.biometric.ChallengeService;
import cm.uy1.campusconnect.auth_service.biometric.dto.EnrollRequest;
import cm.uy1.campusconnect.auth_service.biometric.dto.EnrollResult;
import cm.uy1.campusconnect.auth_service.biometric.dto.EnrollResponse;
import cm.uy1.campusconnect.auth_service.biometric.dto.VerifyRequest;
import cm.uy1.campusconnect.auth_service.biometric.dto.VerifyResult;
import cm.uy1.campusconnect.auth_service.entity.BiometricChallenge;
import cm.uy1.campusconnect.auth_service.entity.BiometricCredential;
import cm.uy1.campusconnect.auth_service.entity.User;
import cm.uy1.campusconnect.auth_service.enums.AuthMethod;
import cm.uy1.campusconnect.auth_service.enums.BiometricType;
import cm.uy1.campusconnect.auth_service.enums.UserStatus;
import cm.uy1.campusconnect.auth_service.repository.BiometricCredentialRepository;
import cm.uy1.campusconnect.auth_service.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BiometricService {

    // ✅ Les champs sont bien déclarés ici
    private final UserRepository userRepository;
    private final BiometricCredentialRepository credentialRepository;
    private final BiometricProviderRegistry providerRegistry;
    private final ChallengeService challengeService;
    private final AuditService auditService;
    private final AuthEventProducer authEventProducer;

    public BiometricService(
            UserRepository userRepository,
            BiometricCredentialRepository credentialRepository,
            BiometricProviderRegistry providerRegistry,
            ChallengeService challengeService,
            AuditService auditService,
            AuthEventProducer authEventProducer) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.providerRegistry = providerRegistry;
        this.challengeService = challengeService;
        this.auditService = auditService;
        this.authEventProducer = authEventProducer;
    }

    @Transactional
    public EnrollResponse enroll(UUID userId, EnrollRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("ACCOUNT_NOT_ACTIVE");
        }

        credentialRepository
                .findFirstByUserIdAndTypeAndProviderAndDeviceIdAndIsActiveTrue(
                        userId, request.type(), request.provider(), request.deviceId())
                .ifPresent(existing -> {
                    existing.setActive(false);
                    credentialRepository.save(existing);
                });

        BiometricEnroller enroller = providerRegistry.getEnroller(request.provider(), request.type());
        EnrollResult result = enroller.enroll(userId, request);

        BiometricCredential credential = BiometricCredential.builder()
                .userId(userId)
                .type(request.type())
                .provider(request.provider())
                .templateEncrypted(result.templateEncrypted())
                .publicKey(result.publicKey())
                .keyVersion(result.keyVersion())
                .deviceId(request.deviceId())
                .isActive(true)
                .enrolledAt(result.enrolledAt())
                .build();

        BiometricCredential saved = credentialRepository.save(credential);

        return new EnrollResponse(saved.getId(), saved.getKeyVersion(), saved.getEnrolledAt());
    }

    @Transactional
    public ChallengeResponse createChallenge(ChallengeRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("EMAIL_REQUIRED");
        }
        UUID userId = userRepository.findByEmailIgnoreCase(request.email().trim())
                .map(User::getId)
                .orElseThrow(() -> new IllegalArgumentException("INVALID_CREDENTIALS"));

        BiometricChallenge challenge = challengeService.issueChallenge(userId, request.type());
        return new ChallengeResponse(challenge.getId(), challenge.getNonce(), challenge.getExpiresAt());
    }

    @Transactional
    public VerifyResult verifyBiometric(VerifyRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new IllegalArgumentException("INVALID_CREDENTIALS"));

        if (user.getStatus() == UserStatus.LOCKED
                && user.getLockedUntil() != null
                && Instant.now().isBefore(user.getLockedUntil())) {
            throw new IllegalStateException("ACCOUNT_LOCKED");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("ACCOUNT_NOT_ACTIVE");
        }

        challengeService.consumeChallenge(
                request.challengeId(), request.nonce(), user.getId(), request.type());

        BiometricCredential credential = credentialRepository
                .findFirstByUserIdAndTypeAndProviderAndDeviceIdAndIsActiveTrue(
                        user.getId(), request.type(), request.provider(), request.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("BIOMETRIC_CREDENTIAL_NOT_FOUND"));

        BiometricVerifier verifier = providerRegistry.getVerifier(request.provider(), request.type());
        VerifyResult result = verifier.verify(user.getId(), credential, request);

        AuthMethod method = request.type() == BiometricType.FACE ? AuthMethod.FACE : AuthMethod.FINGERPRINT;
        String deviceInfo = request.deviceInfo() != null ? request.deviceInfo() : "unknown-device";

        if (result.matched()) {
            credential.setLastUsedAt(Instant.now());
            credentialRepository.save(credential);
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);

            auditService.record(user.getId(), method, true, null, null, deviceInfo, null);
            authEventProducer.publishLoginSucceeded(user.getId(), method.name(), request.deviceId());
        } else {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= 5) {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedUntil(Instant.now().plusSeconds(15 * 60));
            }
            userRepository.save(user);

            String reason = result.failureReason() != null ? result.failureReason() : "BIOMETRIC_MISMATCH";
            auditService.record(user.getId(), method, false, reason, null, deviceInfo, null);
            authEventProducer.publishLoginFailed(user.getId(), user.getEmail(), method.name(), reason);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<BiometricCredential> listActiveCredentials(UUID userId) {
        return credentialRepository.findAllByUserIdAndIsActiveTrue(userId);
    }

    @Transactional
    public void deactivateCredential(UUID userId, UUID credentialId) {
        BiometricCredential credential = credentialRepository.findByIdAndUserId(credentialId, userId)
                .orElseThrow(() -> new IllegalArgumentException("CREDENTIAL_NOT_FOUND"));
        credential.setActive(false);
        credentialRepository.save(credential);
    }

    public record ChallengeRequest(String email, BiometricType type, String deviceId) {}
    public record ChallengeResponse(UUID challengeId, String nonce, Instant expiresAt) {}
}