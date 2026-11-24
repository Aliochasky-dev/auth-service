package cm.uy1.campusconnect.auth_service.repository;

import cm.uy1.campusconnect.auth_service.entity.Role;
import cm.uy1.campusconnect.auth_service.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByName(RoleName name);
}