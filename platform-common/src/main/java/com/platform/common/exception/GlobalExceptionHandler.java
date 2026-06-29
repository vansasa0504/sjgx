package com.platform.common.exception;

import com.platform.common.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        String code = exception.code();
        HttpStatus status = code.endsWith("401") ? HttpStatus.UNAUTHORIZED
                : code.endsWith("403") ? HttpStatus.FORBIDDEN
                : code.endsWith("404") && !code.startsWith("AUTH") ? HttpStatus.NOT_FOUND
                : code.endsWith("409") || "BILL_STATE_INVALID".equals(code) ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        if (status.is5xxServerError()) {
            LOG.error("business exception [{}]: {}", exception.code(), exception.getMessage(), exception);
        } else {
            LOG.warn("business exception [{}]: {}", exception.code(), exception.getMessage());
        }
        return ResponseEntity.status(status).body(Result.fail(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<Void>> handleThrowable(Throwable throwable) {
        LOG.error("unhandled exception", throwable);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail("SYS-500", "internal error"));
    }
}
