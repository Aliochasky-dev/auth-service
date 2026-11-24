package cm.uy1.campusconnect.auth_service.biometric.dto;


public record VerifyResult(
        boolean matched,
        Double similarityScore,
        String failureReason
) {
    public static VerifyResult success(Double score) {
        return new VerifyResult(true, score, null);
    }

    public static VerifyResult failure(String reason, Double score) {
        return new VerifyResult(false, score, reason);
    }
}
