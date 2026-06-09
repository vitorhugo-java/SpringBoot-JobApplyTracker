package com.jobtracker.mapper;

import com.jobtracker.dto.auth.UserResponse;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.RoleName;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;

@Component
public class AuthMapper {

    public UserResponse toUserResponse(User user) {
        LinkedHashSet<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .sorted()
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getReminderTime(),
                roles,
                roles.contains(RoleName.BETA.name()),
                user.isPrivacyPolicyAccepted());
    }
}
