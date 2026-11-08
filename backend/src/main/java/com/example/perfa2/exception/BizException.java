package com.example.perfa2.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class BizException extends RuntimeException {

    private final String code;

    private final HttpStatus status;

    private final Map<String, String> fieldErrors;

    public BizException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public BizException(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public BizException(String message) {
        this(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
