package com.marketingagent.common.security;

public interface AuthenticatedUser {
    Long getId();

    /** 사용자가 속한 업체. 모든 데이터 접근은 이 값으로 격리한다. */
    Long getCompanyId();
}
