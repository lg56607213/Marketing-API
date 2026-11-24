package com.marketingagent.domain.user.dto;

/**
 * @param token      승인 대기(PENDING)로 가입한 경우에는 null 이다. 승인된 뒤 로그인해야 토큰을 받는다.
 * @param status     PENDING / ACTIVE / REJECTED
 * @param superAdmin 모든 업체의 가입 신청을 승인할 수 있는 운영자인지
 */
public record AuthResponse(String token, String email, String role, Long companyId, String companyName,
                           String status, boolean superAdmin) {}
