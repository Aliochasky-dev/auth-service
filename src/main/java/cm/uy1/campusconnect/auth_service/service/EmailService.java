package cm.uy1.campusconnect.auth_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.verification.mail-enabled:true}")
    private boolean mailEnabled;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(String toEmail, String token) {
        if (!mailEnabled) {
            System.out.println("==================================================");
            System.out.println("MODE DEV (MAIL DÉSACTIVÉ)");
            System.out.println("Code de vérification pour " + toEmail + " : " + token);
            System.out.println("==================================================");
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("CampusConnect - Votre code de vérification");
        message.setText("Bonjour,\n\n" +
                "Merci de vous être inscrit sur CampusConnect.\n" +
                "Voici votre code de vérification : " + token + "\n\n" +
                "Ce code expirera dans 15 minutes.\n" +
                "Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.");

        mailSender.send(message);
    }
    public void sendInvitationEmail(String toEmail, String token) {
        String invitationUrl = "http://localhost:8080/api/v1/auth/accept-invitation?token=" + token;

        if (!mailEnabled) {
            System.out.println("==================================================");
            System.out.println("MODE DEV (MAIL DÉSACTIVÉ)");
            System.out.println("Lien d'invitation pour l'enseignant " + toEmail + " :");
            System.out.println(invitationUrl);
            System.out.println("==================================================");
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("CampusConnect - Invitation Enseignant");
        message.setText("Bonjour,\n\n" +
                "L'administration de CampusConnect vous a créé un compte Enseignant.\n" +
                "Cliquez sur le lien ci-dessous pour définir votre mot de passe et activer votre compte :\n" +
                invitationUrl + "\n\n" +
                "Ce lien expirera dans 48 heures.");

        mailSender.send(message);
    }
}