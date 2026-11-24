package cm.uy1.campusconnect.auth_service.security;


import cm.uy1.campusconnect.auth_service.config.BiometricProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class CryptoService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final BiometricProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<Integer, SecretKey> keysByVersion = new HashMap<>();

    public CryptoService(BiometricProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initKeys() {
        String keyV1 = properties.getCrypto().getKeyV1Base64();
        if (keyV1 != null && !keyV1.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(keyV1.trim());
            keysByVersion.put(1, new SecretKeySpec(decoded, ALGORITHM));
        }
    }

    public int currentKeyVersion() {
        return properties.getCrypto().getCurrentKeyVersion();
    }

    public byte[] encrypt(byte[] plainBytes, int keyVersion) {
        SecretKey key = resolveKey(keyVersion);
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainBytes);

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return buffer.array();
        } catch (Exception ex) {
            throw new IllegalStateException("Erreur lors du chiffrement AES-256-GCM.", ex);
        }
    }

    public byte[] decrypt(byte[] encryptedPayload, int keyVersion) {
        if (encryptedPayload == null || encryptedPayload.length <= IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Données chiffrées invalides.");
        }
        SecretKey key = resolveKey(keyVersion);
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encryptedPayload);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(cipherText);
        } catch (Exception ex) {
            throw new IllegalStateException("Erreur lors du déchiffrement AES-256-GCM.", ex);
        }
    }

    private SecretKey resolveKey(int keyVersion) {
        SecretKey key = keysByVersion.get(keyVersion);
        if (key == null) {
            throw new IllegalStateException("Clé absente pour key_version=" + keyVersion);
        }
        return key;
    }
}