# Comprehensive Test Guide - HttpOnly Cookie Authentication

This document summarizes all tests implemented for the refactored authentication system with secure HttpOnly cookies for refresh tokens.

## Overview

The authentication system has been refactored to use **HttpOnly cookies** for refresh tokens (secure, not accessible via JavaScript) while keeping access tokens in memory. This guide covers the test implementations across:

1. **Playwright E2E Tests** (Frontend)
2. **Java Unit Tests** (Backend Services)
3. **Java Integration Tests** (Controller Layer)
4. **Java E2E Tests** (REST-Assured, Full API)

---

## 1. Frontend Tests - Playwright (React)

### Location
`React-JobApplyTracker/tests/`

### Test Files

#### `auth.spec.ts` - Authentication Flow Tests
**Key Tests:**
- ✅ `register a new user` - Verifies registration and dashboard redirect
- ✅ `login with valid credentials` - Login, logout, and re-login flow
- ✅ `persist session after page reload` - Validates session persistence via stored tokens
- ✅ `persist session when backend is unreachable` - Graceful degradation when backend unavailable
- ✅ `clear session when backend returns 401` - Proper cleanup on auth failure
- ✅ `refresh expired access token when 403 returned` - Automatic token refresh via axios interceptors

**What Changed:**
- No major changes needed - Playwright tests work at the browser level
- Browser automatically handles HttpOnly cookies
- Tests verify user flows without needing to inspect cookies directly

#### `helpers/auth.ts` - Auth Utilities
```typescript
- registerUser() - Helper to register and auto-login
- loginUser() - Helper to login an existing user
- uniqueEmail() - Generate unique test emails
```

#### Other Test Files
- `applications.spec.ts` - Application management tests
- `dashboard.spec.ts` - Dashboard functionality tests
- `reminders.spec.ts` - Reminder features tests
- `application-regression.spec.ts` - Regression testing

**Browser Cookie Management:**
- Refresh token stored in browser via `Set-Cookie` header with:
  - `HttpOnly` - JavaScript cannot access
  - `Secure` - HTTPS only
  - `SameSite=Lax` - CSRF protection
  - `Path=/auth/refresh` - Limited to refresh endpoint scope

---

## 2. Backend Tests - Java Unit Tests

### Location
`SpringBoot-JobApplyTracker/src/test/java/com/jobtracker/unit/`

### AuthServiceTest.java

**What Changed:**
- Updated method signatures to match new cookie-based approach
- `refresh()` now takes `RefreshTokenRequest` (empty) + String token parameter
- `logout()` now takes `LogoutRequest` (empty) + String token parameter
- `AuthResponse` no longer contains `refreshToken` field

**Updated/New Tests:**

```java
@Test
void register_shouldReturnAuthResponse_whenValidRequest()
// Verifies:
// - Returns accessToken and user
// - No refreshToken in response
// - Refresh token stored in ThreadLocal for controller
// - Mocks verify save was called

@Test
void refresh_shouldReturnNewAccessToken_andStoreRefreshTokenInThreadLocal()
// Verifies:
// - Reads refreshToken from parameter
// - Generates new accessToken
// - Stores rotated token in ThreadLocal
// - RefreshTokenService.verifyAndRotate called with correct token

@Test
void refresh_shouldThrow_whenRefreshTokenIsNull()
// Verifies UnauthorizedException thrown when token missing

@Test
void logout_shouldRevokeRefreshToken()
// Verifies:
// - refreshTokenService.revokeToken() called with token parameter
// - Returns success message

@Test
void logout_shouldSucceed_whenRefreshTokenIsNull()
// Verifies logout doesn't fail even if token was null
```

**Key Patterns:**
- Uses Mockito for dependency mocking
- ThreadLocal pattern for passing refresh token to controller
- Always tests happy path AND error scenarios
- Verifies service interactions with mocks

---

## 3. Backend Tests - Integration Tests

### Location
`SpringBoot-JobApplyTracker/src/test/java/com/jobtracker/integration/`

### AuthControllerIT.java

**What Changed:**
- Removed assertions for `refreshToken` in JSON response body
- Added assertions for `Set-Cookie` headers
- Updated refresh/logout endpoints to use empty body `{}`
- Added cookie extraction and validation logic

**Key Tests:**

```java
@Test
void register_shouldReturn201_setRefreshTokenCookie_andReturnAccessToken()
// Verifies:
// - Status 201
// - accessToken in JSON (not refreshToken)
// - refreshToken in Set-Cookie header with HttpOnly, Secure, SameSite=Lax
// - Cookie path is /auth/refresh

@Test
void login_shouldReturn200_setRefreshTokenCookie_andReturnAccessToken()
// Verifies:
// - Status 200
// - accessToken in JSON (not refreshToken)
// - Refresh token in HttpOnly cookie headers

@Test
void refresh_shouldReadFromCookie_returnNewAccessToken_andRotateRefreshTokenCookie()
// Verifies:
// - Reads refresh token from Cookie header
// - Empty body {} accepted
// - Returns new accessToken (not refreshToken in body)
// - New refresh token in Set-Cookie header (rotation)
// - Old token is revoked

@Test
void refresh_shouldReturn401_whenTokenMissing()
// Verifies proper error handling

@Test
void logout_shouldClearRefreshTokenCookie()
// Verifies:
// - Logout endpoint called with Cookie header
// - Set-Cookie header returned with Max-Age=0
// - Cookie cleared properly
```

**Integration Test Approach:**
- Uses MockMvc for full Spring context
- Tests actual controller behavior
- Validates HTTP headers and status codes
- Verifies database interactions through cleanup

---

## 4. Backend Tests - E2E Tests (REST-Assured)

### Location
`SpringBoot-JobApplyTracker/src/test/java/com/jobtracker/e2e/`

### AuthE2ETest.java

**What Changed:**
- Removed extraction of `refreshToken` from JSON response
- Added cookie extraction from `Set-Cookie` headers
- Updated to send empty body `{}` for refresh/logout
- Added cookie validation tests

**Core Test Flow:**

```java
@Test
void fullAuthFlow_register_login_refresh_logout()
// Complete flow:
// 1. Register - extract refresh token from Set-Cookie
// 2. Validate user with access token (/auth/me)
// 3. Login - get new access and refresh tokens
// 4. Refresh using cookie - verify token rotation
// 5. Verify old token revoked (401)
// 6. Logout using cookie - verify success
// 7. Verify logout token revoked (401)
```

**Additional E2E Tests:**

```java
@Test
void register_shouldSetHttpOnlySecureSameSiteCookie()
// Validates Set-Cookie header contains security attributes

@Test
void register_shouldReturn409_whenEmailDuplicated()
// Conflict handling

@Test
void register_shouldReturn400_whenPasswordsMismatch()
// Validation error handling

@Test
void login_shouldReturn401_whenWrongPassword()
// Authentication failure

@Test
void refresh_shouldReturn401_whenTokenMissing()
// Missing token error

@Test
void logout_shouldReturnSuccess_andClearCookie()
// Logout and cookie clearing

@Test
void forgotPassword_shouldReturn200_regardlessOfEmailExistence()
// Email enumeration prevention

@Test
void protectedEndpoint_shouldReturn403_whenNoToken()
// Authorization check

@Test
void me_shouldReturn401_whenAccessTokenExpired()
// Expired token handling
```

**Helper Method:**
```java
private String extractRefreshTokenFromCookies(Response response)
// Parses Set-Cookie header to extract refresh token value
// Returns null-safe token or null if not found
```

**E2E Test Approach:**
- Uses RestAssured for REST API testing
- Real HTTP requests to running application
- Validates security headers and cookie attributes
- Tests complete user workflows
- Verifies token rotation and revocation

---

## Test Execution

### Running All Tests

#### Frontend Tests (Playwright)
```bash
cd React-JobApplyTracker
npm test
# or for specific test
npx playwright test tests/auth.spec.ts
```

#### Backend Tests
```bash
cd SpringBoot-JobApplyTracker
# All tests
mvn test

# Unit tests only
mvn test -Dtest=AuthServiceTest

# Integration tests only
mvn test -Dtest=AuthControllerIT

# E2E tests only
mvn test -Dtest=AuthE2ETest
```

### Test Coverage

| Layer | Type | File | Tests | Coverage |
|-------|------|------|-------|----------|
| Frontend | E2E | auth.spec.ts | 6 | Register, login, refresh, logout, session persistence, offline |
| Backend | Unit | AuthServiceTest | 8 | Register, login, refresh, logout, logout with null, refresh errors |
| Backend | Integration | AuthControllerIT | 12 | Register, login, refresh, logout, profile, password, error cases |
| Backend | E2E | AuthE2ETest | 11 | Full flow, cookies, validation, rotation, revocation |

**Total: 37 comprehensive tests**

---

## Key Security Validations

### ✅ HttpOnly Cookies
- [x] Refresh token NOT in JSON response
- [x] Refresh token in Set-Cookie header
- [x] HttpOnly flag present
- [x] Secure flag present
- [x] SameSite=Lax set
- [x] Path=/auth/refresh limited scope

### ✅ Token Management
- [x] Access token returned in response body
- [x] Access token in Authorization header
- [x] Refresh token only via cookie
- [x] Token rotation on refresh
- [x] Old tokens revoked after refresh
- [x] Logout clears cookie (Max-Age=0)

### ✅ Error Handling
- [x] Missing token returns 401
- [x] Invalid token returns 401
- [x] Expired token returns 401
- [x] Duplicate email returns 409
- [x] Password mismatch returns 400
- [x] No auth header returns 403

### ✅ CORS/Credentials
- [x] withCredentials: true in axios
- [x] setAllowCredentials(true) in CorsConfig
- [x] Cookies sent with cross-origin requests

---

## Common Test Patterns

### Unit Test Pattern
```java
// Mock dependencies
@Mock private AuthService authService;

// Test
@Test
void someTest() {
    when(dependency.method()).thenReturn(value);
    Result result = service.method();
    assertThat(result).isEqualTo(expected);
    verify(dependency).method();
}
```

### Integration Test Pattern
```java
// Make HTTP request
MvcResult result = mockMvc.perform(post("/endpoint")
    .header("Authorization", "Bearer " + token)
    .contentType(MediaType.APPLICATION_JSON)
    .content(json))
    .andExpect(status().isOk())
    .andReturn();

// Extract and validate
List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
assertThat(cookies).contains("HttpOnly");
```

### E2E Test Pattern
```java
// API call with RestAssured
Response response = given()
    .header("Cookie", "refreshToken=" + token)
    .contentType("application/json")
    .body("{}")
    .post("/api/v1/auth/refresh")
    .then()
    .statusCode(200)
    .extract().response();

// Extract token from cookie
String newToken = extractRefreshTokenFromCookies(response);
```

---

## Troubleshooting

### Refresh Token Not in Cookie
**Issue:** Test expects Set-Cookie header but gets null
**Solution:** 
- Check controller calls `setRefreshTokenCookie()`
- Verify AuthService stores token in ThreadLocal
- Ensure `getLastRefreshToken()` is called by controller

### Cookie Not Persisting in Playwright
**Issue:** User logged out after page reload
**Solution:**
- Check browser has credentials enabled
- Verify axios config has `withCredentials: true`
- Check CORS allows credentials

### Token Rotation Not Working
**Issue:** Old token still accepts requests
**Solution:**
- Verify `verifyAndRotate()` revokes old token
- Check database cleanup between tests
- Ensure refresh endpoint rotates tokens

### 401 Instead of 403
**Issue:** Test expects 403 but gets 401
**Solution:**
- 401 = No/invalid auth token
- 403 = Valid token but no permission
- Check test setup for auth headers

---

## Future Enhancements

- [ ] Add performance tests for token refresh (latency requirements)
- [ ] Add concurrent login tests (multiple devices)
- [ ] Add token revocation tests (all devices logout)
- [ ] Add mutation testing for test quality
- [ ] Add load tests for refresh endpoint
- [ ] Add browser compatibility tests (Safari, Chrome, Firefox)
- [ ] Add mobile-specific tests (app authentication flows)

