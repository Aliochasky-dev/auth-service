package cm.uy1.campusconnect.auth_service.biometric;

import cm.uy1.campusconnect.auth_service.config.BiometricProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

@Component
public class FaceEmbeddingClient {

    private final RestClient restClient;

    public FaceEmbeddingClient(RestClient.Builder restClientBuilder, BiometricProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.getFaceService().getUrl()).build();
    }

    public EmbeddingExtractionResponse extractEnrollmentEmbedding(List<String> imagesBase64) {
        EmbeddingExtractionResponse response = restClient.post()
                .uri("/api/v1/face/enroll-embedding")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("imagesBase64", imagesBase64))
                .retrieve()
                .body(EmbeddingExtractionResponse.class);

        if (response == null || response.embedding() == null || response.embedding().length == 0) {
            throw new IllegalStateException("Réponse d'embedding invalide du service facial.");
        }
        return response;
    }

    public VerificationEmbeddingResponse verifyLivenessAndEmbed(
            List<String> imagesBase64, String nonce, String livenessGesture) {
        VerificationEmbeddingResponse response = restClient.post()
                .uri("/api/v1/face/verify-embedding")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "imagesBase64", imagesBase64,
                        "nonce", nonce,
                        "livenessGesture", livenessGesture != null ? livenessGesture : ""
                ))
                .retrieve()
                .body(VerificationEmbeddingResponse.class);

        if (response == null) {
            throw new IllegalStateException("Réponse vide du service facial.");
        }
        return response;
    }

    public static byte[] floatsToBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES * vector.length);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    public static float[] bytesToFloats(byte[] bytes) {
        if (bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException("Taille de vecteur d'embedding invalide.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] vector = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) {
            throw new IllegalArgumentException("Dimensions d'embeddings incompatibles.");
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return -1.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public record EmbeddingExtractionResponse(float[] embedding) {}

    public record VerificationEmbeddingResponse(
            boolean livenessPassed,
            String failureReason,
            float[] embedding
    ) {}
}
