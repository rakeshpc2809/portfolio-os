package com.portfolioos.core.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class SecurityInterceptorTest {

    private static final String TEST_TOKEN = "test_valid_auth_token_xyz";

    @Test
    void testPreHandleOptionsRequestReturnsTrue() throws Exception {
        SecurityInterceptor interceptor = new SecurityInterceptor(TEST_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/sync/snapshot");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertTrue(result, "OPTIONS preflight requests must bypass token checks");
    }

    @Test
    void testPreHandleValidConfiguredToken() throws Exception {
        SecurityInterceptor interceptor = new SecurityInterceptor(TEST_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sync/snapshot");
        request.addHeader("X-Api-Auth-Token", TEST_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertTrue(result, "Valid API_AUTH_TOKEN header must pass authentication");
    }

    @Test
    void testPreHandleInvalidTokenReturns401() throws Exception {
        SecurityInterceptor interceptor = new SecurityInterceptor(TEST_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sync/snapshot");
        request.addHeader("X-Api-Auth-Token", "invalid_token_999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertFalse(result);
        assertEquals(401, response.getStatus());
    }

    @Test
    void testPreHandleBackupSheetsEndpointsRequireAuth() throws Exception {
        SecurityInterceptor interceptor = new SecurityInterceptor(TEST_TOKEN);
        MockHttpServletRequest unauthRequest = new MockHttpServletRequest("POST", "/api/v1/backup/sheets/sync");
        MockHttpServletResponse unauthResponse = new MockHttpServletResponse();

        boolean unauthResult = interceptor.preHandle(unauthRequest, unauthResponse, new Object());
        assertFalse(unauthResult, "Unauthenticated calls to /api/v1/backup/sheets/sync must be rejected");
        assertEquals(401, unauthResponse.getStatus());

        MockHttpServletRequest authRequest = new MockHttpServletRequest("POST", "/api/v1/backup/sheets/sync");
        authRequest.addHeader("X-Api-Auth-Token", TEST_TOKEN);
        MockHttpServletResponse authResponse = new MockHttpServletResponse();

        boolean authResult = interceptor.preHandle(authRequest, authResponse, new Object());
        assertTrue(authResult, "Authenticated calls with valid token must pass to backup sheets endpoints");
    }

    @Test
    void testMissingTokenThrowsIllegalStateExceptionAtStartup() {
        assertThrows(IllegalStateException.class, () -> new SecurityInterceptor(null, null));
        assertThrows(IllegalStateException.class, () -> new SecurityInterceptor("", ""));
        assertThrows(IllegalStateException.class, () -> new SecurityInterceptor("   ", "   "));
    }
}
