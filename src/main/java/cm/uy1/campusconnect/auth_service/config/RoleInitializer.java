package cm.uy1.campusconnect.auth_service.config;

import cm.uy1.campusconnect.auth_service.entity.Role;
import cm.uy1.campusconnect.auth_service.enums.RoleName;
import cm.uy1.campusconnect.auth_service.repository.RoleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Makes sure STUDENT, LECTURER and ADMIN exist at startup (idempotent). */
@Component
public class RoleInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;

    public RoleInitializer(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (RoleName name : RoleName.values()) {
            if (roleRepository.findByName(name).isEmpty()) {
                roleRepository.save(Role.builder()
                        .name(name)
                        .description("Rôle " + name.name())
                        .build());
            }
        }
    }
}
