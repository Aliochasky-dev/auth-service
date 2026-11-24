package cm.uy1.campusconnect.auth_service.service;


import cm.uy1.campusconnect.auth_service.entity.LoginAudit;
import cm.uy1.campusconnect.auth_service.enums.AuthMethod;
import cm.uy1.campusconnect.auth_service.repository.LoginAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private final LoginAuditRepository loginAuditRepository;

    public AuditService(LoginAuditRepository loginAuditRepository) {
        this.loginAuditRepository = loginAuditRepository;
    }

    @Transactional
    public void record(
            UUID userId,
            AuthMethod method,
            boolean success,
            String failureReason,
            String ipAddress,
            String deviceInfo,
            Double similarityScore) {
        LoginAudit entry = LoginAudit.builder()
                .userId(userId)
                .method(method)
                .success(success)
                .failureReason(failureReason)
                .ipAddress(ipAddress)
                .deviceInfo(deviceInfo)
                .similarityScore(similarityScore)
                .build();
        loginAuditRepository.save(entry);
    }
}