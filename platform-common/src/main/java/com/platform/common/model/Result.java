package com.platform.common.model;

public record Result<T>(boolean success, String code, String message, T data) {
    public static <T> Result<T> ok(T data) {
        return new Result<>(true, "0", "ok", data);
    }

    public static <T> Result<T> fail(String code, String message) {
        return new Result<>(false, code, message, null);
    }
}
