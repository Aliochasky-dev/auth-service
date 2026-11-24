package cm.uy1.campusconnect.auth_service.repository;

import cm.uy1.campusconnect.auth_service.entity.EmailVerificationToken;
import cm.uy1.campusconnect.auth_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByToken(String token);
    Optional<EmailVerificationToken> findByUser(User user); // <-- Ajouté
    void deleteByUser_Id(UUID userId);
}