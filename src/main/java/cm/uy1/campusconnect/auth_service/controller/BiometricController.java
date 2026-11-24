package cm.uy1.campusconnect.auth_service.controller;


import cm.uy1.campusconnect.auth_service.biometric.dto.EnrollResponse;
import cm.uy1.campusconnect.auth_service.service.BiometricService;
import cm.uy1.campusconnect.auth_service.biometric.dto.EnrollRequest;
import cm.uy1.campusconnect.auth_service.biometric.dto.VerifyRequest;
import cm.uy1.campusconnect.auth_service.biometric.dto.VerifyResult;
import cm.uy1.campusconnect.auth_service.entity.BiometricCredential;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/biometric")
public class BiometricController {

    private final BiometricService biometricService;

    public BiometricController(BiometricService biometricService) {
        this.biometricService = biometricService;
    }

    @PostMapping("/challenge")
    public ResponseEntity<BiometricService.ChallengeResponse> challenge(
            @Valid @RequestBody BiometricService.ChallengeRequest request) {
        return ResponseEntity.ok(biometricService.createChallenge(request));
    }
    @PostMapping("/enroll")
    public ResponseEntity<EnrollResponse> enroll(
            @AuthenticationPrincipal UUID userId, // <--- On demande directement un UUID
            @RequestBody @Valid EnrollRequest request) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(biometricService.enroll(userId, request));
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifyResult> verify(@Valid @RequestBody VerifyRequest request) {
        VerifyResult result = biometricService.verifyBiometric(request);
        if (!result.matched()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/credentials")
    public ResponseEntity<List<CredentialSummary>> list(@AuthenticationPrincipal UUID userId) {
        List<CredentialSummary> items = biometricService.listActiveCredentials(userId).stream()
                .map(CredentialSummary::from)
                .toList();
        return ResponseEntity.ok(items);
    }

    @DeleteMapping("/credentials/{id}")
    public ResponseEntity<Void> deactivate(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID credentialId) {
        biometricService.deactivateCredential(userId, credentialId);
        return ResponseEntity.noContent().build();
    }

    public record CredentialSummary(
            UUID id,
            String type,
            String provider,
            String deviceId,
            boolean isActive,
            Instant enrolledAt,
            Instant lastUsedAt
    ) {
        static CredentialSummary from(BiometricCredential c) {
            return new CredentialSummary(
                    c.getId(),
                    c.getType().name(),
                    c.getProvider().name(),
                    c.getDeviceId(),
                    c.isActive(),
                    c.getEnrolledAt(),
                    c.getLastUsedAt()
            );
        }
    }
}
