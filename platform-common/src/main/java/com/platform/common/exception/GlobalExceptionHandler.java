package com.platform.common.exception;

import com.platform.common.model.Result;

public final class GlobalExceptionHandler {
    private GlobalExceptionHandler() {
    }

    public static Result<Void> handle(Throwable throwable) {
        if (throwable instanceof BusinessException businessException) {
            return Result.fail(businessException.code(), businessException.getMessage());
        }
        return Result.fail("SYS-500", "internal error");
    }
}
