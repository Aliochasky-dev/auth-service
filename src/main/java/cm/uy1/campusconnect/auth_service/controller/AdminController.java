package cm.uy1.campusconnect.auth_service.controller;

import cm.uy1.campusconnect.auth_service.biometric.dto.InviteTeacherRequest;
import cm.uy1.campusconnect.auth_service.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/invite-teacher")
    public ResponseEntity<String> inviteTeacher(@RequestBody @Valid InviteTeacherRequest request) {
        adminService.inviteTeacher(request);
        return ResponseEntity.ok("Invitation envoyée avec succès à " + request.email());
    }
}