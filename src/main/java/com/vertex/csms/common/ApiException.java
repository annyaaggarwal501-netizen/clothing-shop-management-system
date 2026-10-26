package com.vertex.csms.common;

import org.springframework.http.HttpStatus;

/**
 * A business error with an SRS error code (Appendix: ERR-/WARN-/INFO-<MODULE>-<nnn>).
 * Thrown anywhere in a service; GlobalExceptionHandler turns it into {code, message, field, timestamp}.
 * Because it is a RuntimeException, a @Transactional method that throws it rolls back everything.
 */
public class ApiException extends RuntimeException {

    private final String code;
    private final String field;
    private final HttpStatus status;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException badRequest(String code, String message, String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, field);
    }

    public static ApiException conflict(String code, String message, String field) {
        return new ApiException(HttpStatus.CONFLICT, code, message, field);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public String getCode() { return code; }
    public String getField() { return field; }
    public HttpStatus getStatus() { return status; }
}
