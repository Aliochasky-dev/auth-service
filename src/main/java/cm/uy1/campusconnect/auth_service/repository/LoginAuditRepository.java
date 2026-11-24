package cm.uy1.campusconnect.auth_service.repository;



import cm.uy1.campusconnect.auth_service.entity.LoginAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoginAuditRepository extends JpaRepository<LoginAudit, UUID> {
}