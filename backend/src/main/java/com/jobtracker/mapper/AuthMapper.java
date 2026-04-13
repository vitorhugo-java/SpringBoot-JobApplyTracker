package com.jobtracker.mapper;

import com.jobtracker.dto.auth.UserResponse;
import com.jobtracker.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
