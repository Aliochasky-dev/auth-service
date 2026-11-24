package cm.uy1.campusconnect.auth_service.service;

import cm.uy1.campusconnect.auth_service.biometric.dto.LoginRequest;
import cm.uy1.campusconnect.auth_service.entity.User;
import cm.uy1.campusconnect.auth_service.enums.UserStatus;
import cm.uy1.campusconnect.auth_service.exception.ApiException;
import cm.uy1.campusconnect.auth_service.repository.EmailVerificationTokenRepository;
import cm.uy1.campusconnect.auth_service.repository.RoleRepository;
import cm.uy1.campusconnect.auth_service.repository.UserRepository;
import cm.uy1.campusconnect.auth_service.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;
    @Mock private AuditService auditService;
    @Mock private AuthEventProducer authEventProducer;
    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_ShouldThrowException_WhenAccountIsPending() {
        // Arrange
        LoginRequest request = new LoginRequest("etudiant.test@uy1.cm", "Password123!");
        User user = User.builder()
                .email("pending@test.cm")
                .status(UserStatus.PENDING)
                .build();

        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(user));
        // On simule l'encodage du mot de passe pour le constructeur de AuthService
        when(passwordEncoder.encode(anyString())).thenReturn("dummyHash");

        // Act & Assert
        assertThrows(ApiException.class, () -> {
            authService.login(request, "127.0.0.1", "test-device");
        });
    }
}