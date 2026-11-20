package com.marketingagent.domain.ads.account;

import com.marketingagent.integration.naver.SearchAdClient;

/**
 * 접근 확인을 마친 광고 계정과 그 계정의 네이버 클라이언트.
 * 서비스의 public 메서드에서 {@link AdAccountService#scope} 로 한 번 받아 내부 계산에 넘긴다.
 *
 * <p>클라이언트는 처음 쓸 때 만든다. 저장된 데이터만 읽는 계산은 자격증명이 비어 있어도 돌아가야 하기 때문이다.
 */
public final class AccountScope {

    private final AdAccount account;
    private final AdAccountService accountService;
    private SearchAdClient client;

    AccountScope(AdAccount account, AdAccountService accountService) {
        this.account = account;
        this.accountService = accountService;
    }

    public Long id() {
        return account.getId();
    }

    public AdAccount account() {
        return account;
    }

    public SearchAdClient client() {
        if (client == null) {
            client = accountService.client(account);
        }
        return client;
    }
}
