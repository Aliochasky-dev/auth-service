package cm.uy1.campusconnect.auth_service.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        ErrorCode code = ex.getCode();
        return ResponseEntity.status(code.status()).body(ApiError.of(code.name(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(new ApiError(ErrorCode.VALIDATION_ERROR.name(), "Invalid request data",
                        java.time.Instant.now(), fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.status())
                .body(ApiError.of(ErrorCode.INVALID_REQUEST.name(), "Malformed JSON request body"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status())
                .body(ApiError.of(ErrorCode.FORBIDDEN.name(), "Access denied"));
    }

    /**
     * Legacy exceptions still thrown by code not yet migrated to ApiException
     * (e.g. BiometricService). Mapped by their message code; unknown messages are never echoed back.
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiError> handleLegacy(RuntimeException ex) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        ErrorCode code = switch (msg) {
            case "INVALID_CREDENTIALS" -> ErrorCode.INVALID_CREDENTIALS;
            case "EMAIL_ALREADY_EXISTS" -> ErrorCode.EMAIL_ALREADY_EXISTS;
            case "ACCOUNT_LOCKED" -> ErrorCode.ACCOUNT_LOCKED;
            case "ACCOUNT_NOT_ACTIVE" -> ErrorCode.ACCOUNT_NOT_ACTIVE;
            case "INVALID_REFRESH_TOKEN" -> ErrorCode.TOKEN_INVALID;
            case "REFRESH_TOKEN_EXPIRED" -> ErrorCode.TOKEN_EXPIRED;
            default -> null;
        };
        if (code != null) {
            return ResponseEntity.status(code.status()).body(ApiError.of(code.name(), code.name()));
        }
        log.warn("Unmapped {}: {}", ex.getClass().getSimpleName(), msg);
        ErrorCode fallback = ex instanceof IllegalArgumentException ? ErrorCode.INVALID_REQUEST : ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(fallback.status()).body(ApiError.of(fallback.name(), "Request could not be processed"));
    }

    /** Catch-all. Spring MVC errors (404, 405, 415...) keep their own status. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        if (ex instanceof ErrorResponse er) {
            ErrorCode code = er.getStatusCode().value() == 404 ? ErrorCode.NOT_FOUND : ErrorCode.INVALID_REQUEST;
            return ResponseEntity.status(er.getStatusCode()).body(ApiError.of(code.name(), "Request error"));
        }
        log.error("Unexpected error", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiError.of(ErrorCode.INTERNAL_ERROR.name(), "Internal server error"));
    }
}
