package cm.uy1.campusconnect.auth_service.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** Unified error body: { code, message, timestamp } (+ fieldErrors for validation errors). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Instant timestamp, Map<String, String> fieldErrors) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Instant.now(), null);
    }
}
