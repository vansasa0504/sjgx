package com.platform.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {
    @Test
    void mapsBusinessExceptionToStatusAndResult() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleBusinessException(new BusinessException("AUTH-403", "permission denied"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("AUTH-403", response.getBody().code());
    }
}
