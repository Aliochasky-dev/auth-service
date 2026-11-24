package cm.uy1.campusconnect.auth_service.biometric;

import cm.uy1.campusconnect.auth_service.biometric.dto.EnrollRequest;
import cm.uy1.campusconnect.auth_service.biometric.dto.EnrollResult;
import cm.uy1.campusconnect.auth_service.biometric.dto.VerifyRequest;
import cm.uy1.campusconnect.auth_service.biometric.dto.VerifyResult;
import cm.uy1.campusconnect.auth_service.config.BiometricProperties;
import cm.uy1.campusconnect.auth_service.entity.BiometricCredential;
import cm.uy1.campusconnect.auth_service.enums.BiometricType;
import cm.uy1.campusconnect.auth_service.enums.ProviderType;
import cm.uy1.campusconnect.auth_service.security.CryptoService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
public class PhoneBiometricProvider implements BiometricEnroller, BiometricVerifier {

    private final BiometricProperties properties;
    private final CryptoService cryptoService;
    private final FaceEmbeddingClient faceEmbeddingClient;

    public PhoneBiometricProvider(
            BiometricProperties properties,
            CryptoService cryptoService,
            FaceEmbeddingClient faceEmbeddingClient) {
        this.properties = properties;
        this.cryptoService = cryptoService;
        this.faceEmbeddingClient = faceEmbeddingClient;
    }

    @Override
    public ProviderType provider() {
        return ProviderType.PHONE;
    }

    @Override
    public boolean supports(BiometricType type) {
        return type == BiometricType.FINGERPRINT || type == BiometricType.FACE;
    }

    @Override
    public EnrollResult enroll(UUID userId, EnrollRequest request) {
        Instant now = Instant.now();

        if (request.type() == BiometricType.FINGERPRINT) {
            byte[] pubKeyBytes = decodeBase64(request.publicKeyBase64(), "publicKeyBase64");
            validateEcPublicKey(pubKeyBytes); // ✅ Réactivé pour la sécurité
            return new EnrollResult(null, null, pubKeyBytes, cryptoService.currentKeyVersion(), now);
        }

        List<String> images = request.imagesBase64();
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("Au moins une image faciale est requise pour l'enrôlement.");
        }

        // ✅ VRAI APPEL AU SERVICE D'IA
        float[] embedding = faceEmbeddingClient.extractEnrollmentEmbedding(images).embedding();

        byte[] plainVectorBytes = FaceEmbeddingClient.floatsToBytes(embedding);
        int keyVersion = cryptoService.currentKeyVersion();
        byte[] encryptedTemplate = cryptoService.encrypt(plainVectorBytes, keyVersion);

        return new EnrollResult(null, encryptedTemplate, null, keyVersion, now);
    }

    private void validateEcPublicKey(byte[] pubKeyBytes) {
        if (pubKeyBytes == null || pubKeyBytes.length == 0) {
            throw new IllegalArgumentException("Clé publique vide.");
        }
        if (pubKeyBytes.length < 64 || pubKeyBytes.length > 256) {
            throw new IllegalArgumentException("Taille de clé publique invalide.");
        }
        try {
            KeyFactory kf = KeyFactory.getInstance("EC");
            kf.generatePublic(new X509EncodedKeySpec(pubKeyBytes));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Clé publique EC invalide.");
        }
    }

    @Override
    public VerifyResult verify(UUID userId, BiometricCredential credential, VerifyRequest request) {
        if (request.type() == BiometricType.FINGERPRINT) {
            // La signature cryptographique est maintenant obligatoire (plus de dev mode par défaut)
            if (request.signatureBase64() == null || request.signatureBase64().isBlank()) {
                return VerifyResult.failure("SIGNATURE_REQUIRED", null);
            }
            boolean validSig = verifySignature(credential.getPublicKey(), request.nonce(), request.signatureBase64());
            return validSig
                    ? VerifyResult.success(1.0)
                    : VerifyResult.failure("BIOMETRIC_MISMATCH", 0.0);
        }

        if (request.imagesBase64() == null || request.imagesBase64().isEmpty()) {
            return VerifyResult.failure("LIVENESS_FAILED", null);
        }

        // ✅ VRAI APPEL AU SERVICE D'IA
        var faceResp = faceEmbeddingClient.verifyLivenessAndEmbed(
                request.imagesBase64(), request.nonce(), request.livenessGesture());

        if (!faceResp.livenessPassed()) {
            String reason = faceResp.failureReason() != null && !faceResp.failureReason().isBlank()
                    ? faceResp.failureReason()
                    : "LIVENESS_FAILED";
            return VerifyResult.failure(reason, null);
        }

        byte[] decryptedBytes = cryptoService.decrypt(credential.getTemplateEncrypted(), credential.getKeyVersion());
        float[] storedVector = FaceEmbeddingClient.bytesToFloats(decryptedBytes);

        // ✅ VRAI CALCUL DE SIMILARITÉ
        double similarity = FaceEmbeddingClient.cosineSimilarity(storedVector, faceResp.embedding());

        if (similarity >= properties.getFaceSimilarityThreshold()) {
            return VerifyResult.success(similarity);
        }
        return VerifyResult.failure("BIOMETRIC_MISMATCH", similarity);
    }

    private boolean verifySignature(byte[] publicKeyBytes, String nonce, String signatureBase64) {
        if (publicKeyBytes == null || publicKeyBytes.length == 0
                || signatureBase64 == null || signatureBase64.isBlank()) {
            return false;
        }
        try {
            byte[] sigBytes = Base64.getDecoder().decode(signatureBase64);
            KeyFactory kf = KeyFactory.getInstance("EC");
            PublicKey pubKey = kf.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initVerify(pubKey);
            sig.update(nonce.getBytes(StandardCharsets.UTF_8));
            return sig.verify(sigBytes);
        } catch (Exception ex) {
            return false;
        }
    }

    private byte[] decodeBase64(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Champ obligatoire manquant : " + fieldName);
        }
        return Base64.getDecoder().decode(value);
    }
}