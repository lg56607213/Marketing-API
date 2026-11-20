package com.marketingagent.domain.ads.account;

/**
 * 광고 계정이 지워지기 직전에 발행된다. 계정에 딸린 데이터를 가진 모듈은 이 이벤트를 받아 자기 데이터를 지운다.
 * 같은 트랜잭션 안에서 동기로 처리되므로 하나라도 실패하면 계정 삭제 전체가 되돌려진다.
 */
public record AdAccountDeleted(Long accountId) {}
