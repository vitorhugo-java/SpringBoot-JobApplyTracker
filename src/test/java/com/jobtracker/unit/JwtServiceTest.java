package com.jobtracker.unit;

import com.jobtracker.config.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey",
                "TestSecretKeyThatIsAtLeast256BitsLongForTestingPurposesOnly123456");
        ReflectionTestUtils.setField(jwtService, "accessTokenExpirationMs", 900000L);
    }

    @Test
    void generateToken_shouldReturnNonNullToken() {
        UserDetails userDetails = buildUserDetails("user@example.com");
        String token = jwtService.generateToken(userDetails);
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void extractUsername_shouldReturnCorrectEmail() {
        UserDetails userDetails = buildUserDetails("user@example.com");
        String token = jwtService.generateToken(userDetails);
        assertThat(jwtService.extractUsername(token)).isEqualTo("user@example.com");
    }

    @Test
    void isTokenValid_shouldReturnTrueForValidToken() {
        UserDetails userDetails = buildUserDetails("user@example.com");
        String token = jwtService.generateToken(userDetails);
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void isTokenValid_shouldReturnFalseForDifferentUser() {
        UserDetails user1 = buildUserDetails("user1@example.com");
        UserDetails user2 = buildUserDetails("user2@example.com");
        String token = jwtService.generateToken(user1);
        assertThat(jwtService.isTokenValid(token, user2)).isFalse();
    }

    @Test
    void isTokenValid_shouldThrowForExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "accessTokenExpirationMs", -1000L);
        UserDetails userDetails = buildUserDetails("user@example.com");
        String token = jwtService.generateToken(userDetails);
        assertThatThrownBy(() -> jwtService.isTokenValid(token, userDetails))
                .isInstanceOf(Exception.class);
    }

    @Test
    void extractUsername_shouldThrowForInvalidToken() {
        assertThatThrownBy(() -> jwtService.extractUsername("invalid.token.here"))
                .isInstanceOf(Exception.class);
    }

    private UserDetails buildUserDetails(String email) {
        return new User(email, "password", Collections.emptyList());
    }
}
