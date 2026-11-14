package com.marketingagent.integration.naver;

/** 지금 일하고 있는 회원의 네이버 자격증명을 내준다. */
public interface NaverCredentialsProvider {

    /**
     * @throws com.marketingagent.common.exception.BadRequestException 연결된 계정이 없을 때
     */
    NaverCredentials current();

    /**
     * 키워드 검색량·입찰가 추정처럼 특정 광고 계정의 데이터가 아닌 조회에 쓸 자격증명.
     * 회원이 자기 계정을 연결했으면 그것을, 아니면 운영자 계정을 쓴다. 광고 계정의 내용을 읽거나 바꾸는 호출에는 쓰면 안 된다.
     *
     * @throws com.marketingagent.common.exception.BadRequestException 쓸 수 있는 계정이 하나도 없을 때
     */
    default NaverCredentials research() {
        return current();
    }
}
