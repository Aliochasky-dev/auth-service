package cm.uy1.campusconnect.auth_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.biometric")
public class BiometricProperties {

    private long challengeTtlSeconds = 60;
    private double faceSimilarityThreshold = 0.65;
    private CryptoConfig crypto = new CryptoConfig();
    private FaceServiceConfig faceService = new FaceServiceConfig();
    private FingerprintConfig fingerprint = new FingerprintConfig();
    private KitConfig kit = new KitConfig();

    @Getter
    @Setter
    public static class CryptoConfig {
        private String keyV1Base64 = "";
        private int currentKeyVersion = 1;
    }

    @Getter
    @Setter
    public static class FaceServiceConfig {
        private String url = "http://localhost:8000";
    }

    @Getter
    @Setter
    public static class FingerprintConfig {
        private boolean devModeAllowUnsigned = false;
    }

    @Getter
    @Setter
    public static class KitConfig {
        private boolean enabled = false;
        private String endpointUrl = "";
    }
}