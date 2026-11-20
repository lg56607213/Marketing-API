package com.marketingagent.common.security;

import java.util.function.Supplier;

/**
 * 로그인 없이 도는 작업(일 배치, 자동 운영)이 특정 광고 계정 하나를 다룰 수 있게 열어 주는 범위.
 *
 * <p>웹 요청에서는 로그인한 사용자의 업체 소유인지로 계정 접근을 확인한다. 배치에는 로그인한 사용자가 없으므로
 * 배치가 직접 고른 계정 하나만 이 범위 안에서 허용한다. 범위 밖이거나 다른 계정 ID 이면 평소처럼 소유 확인을 거친다.
 */
public final class SystemTask {

    /** 자동 운영처럼 사람이 아닌 주체가 승인했음을 기록할 때 쓰는 행위자 ID. */
    public static final long SYSTEM_ACTOR = 0L;

    private static final ThreadLocal<Long> ACCOUNT = new ThreadLocal<>();

    private SystemTask() {
    }

    /** 이 계정에 대해 시스템 작업 범위가 열려 있는지. */
    public static boolean allows(Long accountId) {
        Long current = ACCOUNT.get();
        return current != null && current.equals(accountId);
    }

    /** 지금 시스템 작업 범위 안인지. */
    public static boolean active() {
        return ACCOUNT.get() != null;
    }

    public static <T> T callFor(long accountId, Supplier<T> work) {
        Long previous = ACCOUNT.get();
        ACCOUNT.set(accountId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                ACCOUNT.remove();
            } else {
                ACCOUNT.set(previous);
            }
        }
    }

    public static void runFor(long accountId, Runnable work) {
        callFor(accountId, () -> {
            work.run();
            return null;
        });
    }
}
