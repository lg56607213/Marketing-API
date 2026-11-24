package com.marketingagent.domain.user.dto;

import com.marketingagent.domain.user.User;
import java.time.LocalDateTime;

/** 최고 관리자의 가입 승인 화면에 보여 줄 계정 정보 */
public record SignupSummary(Long id, String email, String companyName, String role, String status,
                            LocalDateTime createdAt) {
    public static SignupSummary from(User user, String companyName) {
        return new SignupSummary(user.getId(), user.getEmail(), companyName, user.getRole().name(),
                user.getStatus().name(), user.getCreatedAt());
    }
}
