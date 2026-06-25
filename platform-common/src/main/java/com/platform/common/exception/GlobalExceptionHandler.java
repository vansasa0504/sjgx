package com.platform.common.exception;

import com.platform.common.model.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        HttpStatus status = exception.code().endsWith("401") ? HttpStatus.UNAUTHORIZED
                : exception.code().endsWith("403") ? HttpStatus.FORBIDDEN
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Result.fail(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<Void>> handleThrowable(Throwable throwable) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail("SYS-500", "internal error"));
    }
}
