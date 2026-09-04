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

    @Test
    void testPreHandleBackupSheetsEndpointsRequireAuth() throws Exception {
        SecurityInterceptor interceptor = new SecurityInterceptor();
        MockHttpServletRequest unauthRequest = new MockHttpServletRequest("POST", "/api/v1/backup/sheets/sync");
        MockHttpServletResponse unauthResponse = new MockHttpServletResponse();

        boolean unauthResult = interceptor.preHandle(unauthRequest, unauthResponse, new Object());
        assertFalse(unauthResult, "Unauthenticated calls to /api/v1/backup/sheets/sync must be rejected");
        assertEquals(401, unauthResponse.getStatus());

        MockHttpServletRequest authRequest = new MockHttpServletRequest("POST", "/api/v1/backup/sheets/sync");
        String expectedToken = System.getenv("API_AUTH_TOKEN") != null ? System.getenv("API_AUTH_TOKEN") : "dev_secret_key_123";
        authRequest.addHeader("X-Api-Auth-Token", expectedToken);
        MockHttpServletResponse authResponse = new MockHttpServletResponse();

        boolean authResult = interceptor.preHandle(authRequest, authResponse, new Object());
        assertTrue(authResult, "Authenticated calls with valid token must pass to backup sheets endpoints");
    }
}
