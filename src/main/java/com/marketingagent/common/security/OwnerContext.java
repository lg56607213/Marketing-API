package com.marketingagent.common.security;

import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 지금 어느 회원의 광고 계정으로 일하고 있는지 알려준다.
 *
 * <p>웹 요청에서는 로그인한 회원이 곧 소유자다. 일 배치처럼 로그인 없이 도는 작업은
 * {@link #runAs} 로 소유자를 명시한다. 광고 데이터 조회·저장과 네이버 API 호출이 모두 이 값을 따른다.
 */
public final class OwnerContext {

    /** 소유자가 없는 상태. 이 값으로는 어떤 광고 데이터도 보이지 않는다. */
    public static final long NO_OWNER = 0L;

    private static final ThreadLocal<Long> OVERRIDE = new ThreadLocal<>();

    private OwnerContext() {
    }

    /** 현재 소유자의 회원 ID. 없으면 null. */
    public static Long currentOwnerId() {
        Long override = OVERRIDE.get();
        if (override != null) {
            return override;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.getId();
        }
        return null;
    }

    /** 지정한 회원의 계정으로 작업을 실행하고 결과를 돌려준다. 끝나면 원래 상태로 되돌린다. */
    public static <T> T callAs(long ownerId, Supplier<T> work) {
        Long previous = OVERRIDE.get();
        OVERRIDE.set(ownerId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                OVERRIDE.remove();
            } else {
                OVERRIDE.set(previous);
            }
        }
    }

    public static void runAs(long ownerId, Runnable work) {
        callAs(ownerId, () -> {
            work.run();
            return null;
        });
    }
}
