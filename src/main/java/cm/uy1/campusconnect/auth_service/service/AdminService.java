package cm.uy1.campusconnect.auth_service.service;

import cm.uy1.campusconnect.auth_service.biometric.dto.InviteTeacherRequest;
import cm.uy1.campusconnect.auth_service.entity.EmailVerificationToken;
import cm.uy1.campusconnect.auth_service.entity.Role;
import cm.uy1.campusconnect.auth_service.entity.User;
import cm.uy1.campusconnect.auth_service.enums.RoleName;
import cm.uy1.campusconnect.auth_service.enums.UserStatus;
import cm.uy1.campusconnect.auth_service.exception.ApiException;
import cm.uy1.campusconnect.auth_service.exception.ErrorCode;
import cm.uy1.campusconnect.auth_service.repository.EmailVerificationTokenRepository;
import cm.uy1.campusconnect.auth_service.repository.RoleRepository;
import cm.uy1.campusconnect.auth_service.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository userRepository, RoleRepository roleRepository,
                        EmailVerificationTokenRepository tokenRepository, EmailService emailService,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void inviteTeacher(InviteTeacherRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, "Cet email est déjà utilisé.");
        }

        Role teacherRole = roleRepository.findByName(RoleName.TEACHER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(RoleName.TEACHER).description("Enseignant").build()));

        // On crée le prof avec un mot de passe temporaire (il le changera via le lien)
        User teacher = User.builder()
                .email(email)
                .phone(request.phone().trim())
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .status(UserStatus.PENDING)
                .emailVerified(false)
                .roles(new HashSet<>(Set.of(teacherRole)))
                .build();

        User saved = userRepository.save(teacher);

        // On génère un token d'invitation
        String tokenString = UUID.randomUUID().toString();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(tokenString)
                .user(saved)
                .expiryDate(Instant.now().plusSeconds(48 * 60 * 60)) // 48 heures
                .build();

        tokenRepository.save(token);

        // On envoie le mail
        emailService.sendInvitationEmail(saved.getEmail(), tokenString);
    }
}