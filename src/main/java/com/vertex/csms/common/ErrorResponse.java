package com.vertex.csms.common;

import java.time.LocalDateTime;

/** Error body required by SRS 3.5: {code, message, field, timestamp}. */
public record ErrorResponse(String code, String message, String field, LocalDateTime timestamp) {

    public static ErrorResponse of(String code, String message, String field) {
        return new ErrorResponse(code, message, field, LocalDateTime.now());
    }
}
