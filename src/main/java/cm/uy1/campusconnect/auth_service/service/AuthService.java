package cm.uy1.campusconnect.auth_service.service;

import cm.uy1.campusconnect.auth_service.biometric.dto.LoginRequest;
import cm.uy1.campusconnect.auth_service.biometric.dto.RegisterRequest;
import cm.uy1.campusconnect.auth_service.biometric.dto.RegisterResponse;
import cm.uy1.campusconnect.auth_service.biometric.dto.TokenPairResponse;
import cm.uy1.campusconnect.auth_service.entity.EmailVerificationToken;
import cm.uy1.campusconnect.auth_service.entity.RefreshToken;
import cm.uy1.campusconnect.auth_service.entity.Role;
import cm.uy1.campusconnect.auth_service.entity.User;
import cm.uy1.campusconnect.auth_service.enums.AuthMethod;
import cm.uy1.campusconnect.auth_service.enums.RoleName;
import cm.uy1.campusconnect.auth_service.enums.UserStatus;
import cm.uy1.campusconnect.auth_service.exception.ApiException;
import cm.uy1.campusconnect.auth_service.exception.ErrorCode;
import cm.uy1.campusconnect.auth_service.repository.EmailVerificationTokenRepository;
import cm.uy1.campusconnect.auth_service.repository.RoleRepository;
import cm.uy1.campusconnect.auth_service.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuditService auditService;
    private final AuthEventProducer authEventProducer;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;

    /** Used to spend the same time on unknown accounts as on real ones (limits user enumeration by timing). */
    private final String dummyHash;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       AuditService auditService,
                       AuthEventProducer authEventProducer,
                       EmailVerificationTokenRepository tokenRepository,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.auditService = auditService;
        this.authEventProducer = authEventProducer;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    // ------------------------------------------------------------------ register

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String phone = request.phone().trim();

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already registered");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new ApiException(ErrorCode.PHONE_ALREADY_EXISTS, "Phone number already registered");
        }

        // Self-registration is always STUDENT. The role must already exist (seeded by Flyway / initializer).
        Role studentRole = roleRepository.findByName(RoleName.STUDENT)
                .orElseThrow(() -> new IllegalStateException("Role STUDENT is missing in database"));

        User user = User.builder()
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.PENDING)          // PENDING + email verification is the target design
                .emailVerified(false)
                .roles(new HashSet<>(Set.of(studentRole)))
                .build();

        User saved = saveNewUser(user);

        // Supprimer les anciens tokens s'il y en a
        tokenRepository.deleteByUser_Id(saved.getId());

        // ✅ GÉNÉRATION DU CODE À 6 CHIFFRES
        String tokenString = String.format("%06d", new java.util.Random().nextInt(1000000));

        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .token(tokenString)
                .user(saved)
                .expiryDate(Instant.now().plusSeconds(15 * 60)) // 15 minutes
                .build();
        tokenRepository.save(verificationToken);

        // Envoyer le mail
        emailService.sendVerificationEmail(saved.getEmail(), tokenString);

        UUID savedId = saved.getId();
        String savedEmail = saved.getEmail();
        safely(() -> authEventProducer.publishUserRegistered(savedId, savedEmail, RoleName.STUDENT.name()));
        return new RegisterResponse(savedId, savedEmail, saved.getStatus().name(), RoleName.STUDENT.name());
    }

    private User saveNewUser(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Concurrent registration with the same email/phone
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email or phone already registered");
        }
    }

    // ------------------------------------------------------------------ login

    /**
     * noRollbackFor: failed-attempt counters and audit rows must be committed even though we throw.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenPairResponse login(LoginRequest request, String ip, String deviceInfo) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            recordFailure(null, email, "INVALID_CREDENTIALS", ip, deviceInfo);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }

        // A temporary lock that has expired is lifted automatically.
        if (user.getStatus() == UserStatus.LOCKED
                && user.getLockedUntil() != null
                && !Instant.now().isBefore(user.getLockedUntil())) {
            user.setStatus(UserStatus.ACTIVE);
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            recordFailure(user, email, "ACCOUNT_LOCKED", ip, deviceInfo);
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, "Account temporarily locked");
        }

        if (user.getStatus() == UserStatus.PENDING) {
            recordFailure(user, email, "EMAIL_NOT_VERIFIED", ip, deviceInfo);
            throw new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVE, "Veuillez vérifier votre adresse email avant de vous connecter.");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            recordFailure(user, email, "ACCOUNT_NOT_ACTIVE", ip, deviceInfo);
            throw new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVE, "Account is not active");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedUntil(Instant.now().plus(LOCK_DURATION));
            }
            userRepository.save(user);
            recordFailure(user, email, "INVALID_CREDENTIALS", ip, deviceInfo);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }

        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        TokenPairResponse tokens = tokenService.issueTokenPair(user, deviceInfo);
        auditService.record(user.getId(), AuthMethod.PASSWORD, true, null, ip, deviceInfo, null);
        safely(() -> authEventProducer.publishLoginSucceeded(user.getId(), AuthMethod.PASSWORD.name(), deviceInfo));
        return tokens;
    }

    // ------------------------------------------------------------------ refresh / logout

    @Transactional(noRollbackFor = ApiException.class)
    public TokenPairResponse refresh(String rawRefreshToken) {
        RefreshToken consumed = tokenService.consumeValidRefreshToken(rawRefreshToken);

        User user = userRepository.findById(consumed.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Invalid refresh token"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            tokenService.revokeAllForUser(user.getId());
            throw new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVE, "Account is not active");
        }
        return tokenService.issueTokenPair(user, consumed.getDeviceId());
    }

    public void logout(String rawRefreshToken) {
        tokenService.revokeRefreshToken(rawRefreshToken);
    }

    // ------------------------------------------------------------------ helpers

    private void recordFailure(User user, String email, String reason, String ip, String deviceInfo) {
        UUID userId = user == null ? null : user.getId();
        auditService.record(userId, AuthMethod.PASSWORD, false, reason, ip, deviceInfo, null);
        safely(() -> authEventProducer.publishLoginFailed(userId, email, AuthMethod.PASSWORD.name(), reason));
    }

    /** A messaging failure (Kafka down) must never break authentication. */
    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Event publication failed: {}", e.getMessage());
        }
    }
}