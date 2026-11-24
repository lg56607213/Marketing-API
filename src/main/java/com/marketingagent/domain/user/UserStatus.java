package com.marketingagent.domain.user;

/** 계정 이용 상태. ACTIVE 만 로그인하고 API 를 쓸 수 있다. */
public enum UserStatus {
    /** 회원가입 후 최고 관리자의 승인을 기다리는 중 */
    PENDING,
    /** 이용 중 */
    ACTIVE,
    /** 가입 거절 또는 이용 중지 */
    REJECTED
}
