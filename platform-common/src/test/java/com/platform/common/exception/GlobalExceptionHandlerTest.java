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

        assertEquals(HttpStatus.NOT_FOUND,
                handler.handleBusinessException(new BusinessException("CATALOG_APP-404", "missing")).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,
                handler.handleBusinessException(new BusinessException("CATALOG_APP-409", "reviewed")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                handler.handleBusinessException(new BusinessException("AUTH-404", "api key missing")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                handler.handleBusinessException(new BusinessException("QUALITY-404", "rule missing")).getStatusCode());
    }
}
