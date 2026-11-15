package com.marketingagent.common.security;

import com.marketingagent.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static Long id() {
        return principal().getId();
    }

    /** 로그인한 사용자의 업체 ID. 업체가 없는 계정은 어떤 데이터에도 접근할 수 없다. */
    public static Long companyId() {
        Long companyId = principal().getCompanyId();
        if (companyId == null) {
            throw new UnauthorizedException("업체에 속하지 않은 계정입니다. 관리자에게 문의하세요.");
        }
        return companyId;
    }

    private static AuthenticatedUser principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UnauthorizedException("No authenticated user found");
        }
        return user;
    }
}
