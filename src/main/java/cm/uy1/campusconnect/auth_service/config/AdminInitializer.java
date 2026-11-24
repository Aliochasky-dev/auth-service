package cm.uy1.campusconnect.auth_service.config;

import cm.uy1.campusconnect.auth_service.entity.Role;
import cm.uy1.campusconnect.auth_service.entity.User;
import cm.uy1.campusconnect.auth_service.enums.RoleName;
import cm.uy1.campusconnect.auth_service.enums.UserStatus;
import cm.uy1.campusconnect.auth_service.repository.RoleRepository;
import cm.uy1.campusconnect.auth_service.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.phone}")
    private String adminPhone;

    public AdminInitializer(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // On vérifie si l'admin existe déjà
        if (userRepository.findByEmailIgnoreCase(adminEmail).isEmpty()) {

            // On s'assure que le rôle ADMIN existe
            Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                    .orElseGet(() -> roleRepository.save(Role.builder().name(RoleName.ADMIN).description("Administrateur").build()));

            // On crée l'utilisateur Admin
            User admin = User.builder()
                    .email(adminEmail)
                    .phone(adminPhone)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .status(UserStatus.ACTIVE) // Déjà actif
                    .emailVerified(true)       // Déjà vérifié
                    .roles(new HashSet<>(Set.of(adminRole)))
                    .build();

            userRepository.save(admin);
            System.out.println("✅ COMPTE ADMIN CRÉÉ AVEC SUCCÈS : " + adminEmail);
        } else {
            System.out.println("ℹ️ Le compte Admin existe déjà.");
        }
    }
}