package com.marketingagent.common.exception;

/** 본인 확인은 됐지만 지금은 허용하지 않는 요청. 예: 승인 대기 중인 계정의 로그인 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
