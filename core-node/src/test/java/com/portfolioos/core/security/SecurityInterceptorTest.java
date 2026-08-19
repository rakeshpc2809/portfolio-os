package com.portfolioos.core.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class SecurityInterceptorTest {

    @Test
    void testPreHandleOptionsRequestReturnsTrue() throws Exception {
        SecurityInterceptor interceptor = new SecurityInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/sync/snapshot");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertTrue(result, "OPTIONS preflight requests must bypass token checks");
    }

    @Test
    void testPreHandleValidDevToken() throws Exception {
        SecurityInterceptor interceptor = new SecurityInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sync/snapshot");
        request.addHeader("X-Api-Auth-Token", "dev_secret_key_123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertTrue(result);
    }

    @Test
    void testPreHandleInvalidTokenReturns401() throws Exception {
        SecurityInterceptor interceptor = new SecurityInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sync/snapshot");
        request.addHeader("X-Api-Auth-Token", "invalid_token_999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertFalse(result);
        assertEquals(401, response.getStatus());
    }
}
