package com.jobtracker.util;

import com.jobtracker.entity.User;
import com.jobtracker.exception.UnauthorizedException;
import com.jobtracker.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecurityUtils {

    private final UserRepository userRepository;

    public SecurityUtils(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String userId = jwt.getClaimAsString("user_id");
            if (userId != null && !userId.isBlank()) {
                return userRepository.findById(UUID.fromString(userId))
                        .orElseThrow(() -> new UnauthorizedException("User not found"));
            }
            return findByEmail(jwt.getSubject());
        }
        if (principal instanceof UserDetails userDetails) {
            return findByEmail(userDetails.getUsername());
        }
        return findByEmail(authentication.getName());
    }

    public UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
    }
}
