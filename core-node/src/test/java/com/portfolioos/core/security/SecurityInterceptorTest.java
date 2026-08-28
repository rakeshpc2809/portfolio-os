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
    void testPreHandleValidConfiguredToken() throws Exception {
        SecurityInterceptor interceptor = new SecurityInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sync/snapshot");
        String expectedToken = System.getenv("API_AUTH_TOKEN") != null ? System.getenv("API_AUTH_TOKEN") : "dev_secret_key_123";
        request.addHeader("X-Api-Auth-Token", expectedToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertTrue(result, "Valid API_AUTH_TOKEN header must pass authentication");
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
