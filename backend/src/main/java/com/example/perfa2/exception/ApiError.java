package com.example.perfa2.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 统一错误响应体：{ code, message, fieldErrors }。
 * fieldErrors 仅在字段级校验失败时出现。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, String> fieldErrors) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }
}
