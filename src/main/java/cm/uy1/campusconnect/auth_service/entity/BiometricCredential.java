package cm.uy1.campusconnect.auth_service.entity;
import cm.uy1.campusconnect.auth_service.enums.BiometricType;
import cm.uy1.campusconnect.auth_service.enums.ProviderType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "biometric_credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BiometricCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BiometricType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private ProviderType provider = ProviderType.PHONE;

    @Column(name = "template_encrypted")
    private byte[] templateEncrypted;

    @Column(name = "public_key")
    private byte[] publicKey;

    @Column(name = "key_version", nullable = false)
    @Builder.Default
    private int keyVersion = 1;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @PrePersist
    void onEnroll() {
        if (this.enrolledAt == null) {
            this.enrolledAt = Instant.now();
        }
    }
}