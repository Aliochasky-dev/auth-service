package cm.uy1.campusconnect.auth_service.controller;

import cm.uy1.campusconnect.auth_service.biometric.dto.*;
import cm.uy1.campusconnect.auth_service.entity.EmailVerificationToken;
import cm.uy1.campusconnect.auth_service.entity.User;
import cm.uy1.campusconnect.auth_service.enums.UserStatus;
import cm.uy1.campusconnect.auth_service.exception.ApiException;
import cm.uy1.campusconnect.auth_service.exception.ErrorCode;
import cm.uy1.campusconnect.auth_service.repository.EmailVerificationTokenRepository;
import cm.uy1.campusconnect.auth_service.repository.UserRepository;
import cm.uy1.campusconnect.auth_service.service.AuthService;
import cm.uy1.campusconnect.auth_service.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** Keep within the column size of refresh_tokens.device_id / login_audit.device_info. */
    private static final int MAX_DEVICE_INFO_LENGTH = 128;

    private final AuthService authService;
    private final UserRepository userRepository; // <-- À AJOUTER
    private final EmailVerificationTokenRepository tokenRepository; // <-- À AJOUTER
    private final EmailService emailService; // <-- À AJOUTER
    private final PasswordEncoder passwordEncoder;


    public AuthController(AuthService authService,
                          UserRepository userRepository,
                          EmailVerificationTokenRepository tokenRepository,
                          EmailService emailService,PasswordEncoder passwordEncoder) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenPairResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        String deviceInfo = resolveDeviceInfo(httpRequest);
        return ResponseEntity.ok(authService.login(request, ip, deviceInfo));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** Device information comes from the User-Agent header: the client never has to send it. */
    private String resolveDeviceInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown-device";
        }
        String ua = userAgent.trim();
        return ua.length() > MAX_DEVICE_INFO_LENGTH ? ua.substring(0, MAX_DEVICE_INFO_LENGTH) : ua;
    }
    // --- À AJOUTER DANS AuthController ---

    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam("token") String token) {
        // 1. Chercher le token
        var verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Lien de vérification invalide"));

        // 2. Vérifier s'il n'est pas expiré
        if (verificationToken.isExpired()) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Le lien de vérification a expiré");
        }

        // 3. Activer l'utilisateur
        User user = verificationToken.getUser();
        if (user.getStatus() == UserStatus.PENDING) {
            user.setStatus(UserStatus.ACTIVE);
            user.setEmailVerified(true);
            userRepository.save(user);
        }

        // 4. Supprimer le token pour qu'il ne soit plus réutilisable
        tokenRepository.delete(verificationToken);

        return ResponseEntity.ok("Adresse email confirmée avec succès. Vous pouvez maintenant vous connecter.");
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email != null) {
            userRepository.findByEmailIgnoreCase(email.trim()).ifPresent(user -> {
                if (user.getStatus() == UserStatus.PENDING) {
                    tokenRepository.deleteByUser_Id(user.getId());
                    String tokenString = UUID.randomUUID().toString();
                    var token = EmailVerificationToken.builder()
                            .token(tokenString).user(user)
                            .expiryDate(Instant.now().plusSeconds(24 * 60 * 60)).build();
                    tokenRepository.save(token);
                    emailService.sendVerificationEmail(user.getEmail(), tokenString);
                }
            });
        }
        return ResponseEntity.accepted().build();
    }
    // Ajoute cette route quelque part dans ton AuthController
    @PostMapping("/verify-code")
    public ResponseEntity<String> verifyCode(@RequestBody @Valid VerifyCodeRequest request) {
        String email = request.email().trim();
        String code = request.code().trim();

        // 1. On cherche l'utilisateur et le token de manière "silencieuse"
        var userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            // Si l'email n'existe pas, on renvoie la même erreur que si le code était faux
            throw new IllegalArgumentException("Code de vérification invalide ou expiré.");
        }

        User user = userOpt.get();
        var tokenOpt = tokenRepository.findByUser(user);

        // 2. On vérifie le code et l'expiration en une seule fois
        if (tokenOpt.isEmpty()) {
            throw new IllegalArgumentException("Code de vérification invalide ou expiré.");
        }

        EmailVerificationToken verificationToken = tokenOpt.get();

        if (!verificationToken.getToken().equals(code) || verificationToken.isExpired()) {
            // Même message si le code est faux ou s'il est expiré
            throw new IllegalArgumentException("Code de vérification invalide ou expiré.");
        }

        // 3. Si tout est bon, on active le compte
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        userRepository.save(user);

        // On supprime le token pour qu'il ne puisse pas être réutilisé
        tokenRepository.delete(verificationToken);

        return ResponseEntity.ok("Compte activé avec succès !");
    }
    @PostMapping("/accept-invitation")
    public ResponseEntity<String> acceptInvitation(@RequestBody @Valid AcceptInvitationRequest request) {
        // 1. Chercher le token
        EmailVerificationToken token = tokenRepository.findByToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("Lien d'invitation invalide."));

        if (token.isExpired()) {
            throw new IllegalArgumentException("Ce lien d'invitation a expiré.");
        }

        // 2. Mettre à jour l'utilisateur
        User user = token.getUser();
        if (user.getStatus() != UserStatus.PENDING) {
            throw new IllegalStateException("Ce compte est déjà actif.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        userRepository.save(user);

        // 3. Supprimer le token
        tokenRepository.delete(token);

        return ResponseEntity.ok("Compte Enseignant activé avec succès !");
    }
}
