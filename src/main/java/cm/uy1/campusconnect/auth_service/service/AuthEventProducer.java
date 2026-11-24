package cm.uy1.campusconnect.auth_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String userRegisteredTopic;
    private final String biometricEnrolledTopic;
    private final String loginSucceededTopic;
    private final String loginFailedTopic;

    public AuthEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.user-registered:user.registered}") String userRegisteredTopic,
            @Value("${app.kafka.topics.biometric-enrolled:user.biometric.enrolled}") String biometricEnrolledTopic,
            @Value("${app.kafka.topics.login-succeeded:user.login.succeeded}") String loginSucceededTopic,
            @Value("${app.kafka.topics.login-failed:user.login.failed}") String loginFailedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.userRegisteredTopic = userRegisteredTopic;
        this.biometricEnrolledTopic = biometricEnrolledTopic;
        this.loginSucceededTopic = loginSucceededTopic;
        this.loginFailedTopic = loginFailedTopic;
    }

    public void publishUserRegistered(UUID userId, String email, String role) {
        send(userRegisteredTopic, userId.toString(), Map.of(
                "eventType", "USER_REGISTERED",
                "userId", userId.toString(),
                "email", email,
                "role", role,
                "timestamp", Instant.now().toString()
        ));
    }

    public void publishBiometricEnrolled(UUID userId, String type, String provider, String deviceId) {
        send(biometricEnrolledTopic, userId.toString(), Map.of(
                "eventType", "BIOMETRIC_ENROLLED",
                "userId", userId.toString(),
                "type", type,
                "provider", provider,
                "deviceId", deviceId,
                "timestamp", Instant.now().toString()
        ));
    }

    public void publishLoginSucceeded(UUID userId, String method, String deviceId) {
        send(loginSucceededTopic, userId != null ? userId.toString() : "unknown", Map.of(
                "eventType", "LOGIN_SUCCEEDED",
                "userId", userId != null ? userId.toString() : "",
                "method", method,
                "deviceId", deviceId != null ? deviceId : "",
                "timestamp", Instant.now().toString()
        ));
    }

    public void publishLoginFailed(UUID userId, String email, String method, String reason) {
        String key = userId != null ? userId.toString() : email;
        send(loginFailedTopic, key, Map.of(
                "eventType", "LOGIN_FAILED",
                "userId", userId != null ? userId.toString() : "",
                "email", email != null ? email : "",
                "method", method,
                "reason", reason != null ? reason : "",
                "timestamp", Instant.now().toString()
        ));
    }

    private void send(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, payload);
        } catch (Exception ignored) {
            // Évite de bloquer l'auth si le broker Kafka est temporairement indisponible en dev
        }
    }
}