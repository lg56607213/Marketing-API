package com.marketingagent.domain.user.dto;

import com.marketingagent.domain.user.User;
import java.time.LocalDateTime;

public record UserSummary(Long id, String email, String role, LocalDateTime createdAt) {
    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getRole().name(), user.getCreatedAt());
    }
}
