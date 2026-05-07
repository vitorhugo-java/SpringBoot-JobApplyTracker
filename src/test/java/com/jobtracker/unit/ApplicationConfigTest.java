package com.jobtracker.unit;

import com.jobtracker.config.ApplicationConfig;
import com.jobtracker.entity.Role;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.RoleName;
import com.jobtracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationConfigTest {

    @Test
    void userDetailsService_shouldMapPersistedRolesToGrantedAuthorities() {
        UserRepository userRepository = mock(UserRepository.class);
        ApplicationConfig applicationConfig = new ApplicationConfig(userRepository);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("roles@example.com");
        user.setPasswordHash("hashed");

        Role userRole = new Role();
        userRole.setName(RoleName.USER);
        Role betaRole = new Role();
        betaRole.setName(RoleName.BETA);
        user.setRoles(Set.of(userRole, betaRole));

        when(userRepository.findByEmail("roles@example.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = applicationConfig.userDetailsService().loadUserByUsername("roles@example.com");

        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_BETA");
    }
}
