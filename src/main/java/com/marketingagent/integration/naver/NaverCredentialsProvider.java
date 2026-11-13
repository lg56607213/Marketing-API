package com.marketingagent.integration.naver;

/** 지금 일하고 있는 회원의 네이버 자격증명을 내준다. */
public interface NaverCredentialsProvider {

    /**
     * @throws com.marketingagent.common.exception.BadRequestException 연결된 계정이 없을 때
     */
    NaverCredentials current();
}
