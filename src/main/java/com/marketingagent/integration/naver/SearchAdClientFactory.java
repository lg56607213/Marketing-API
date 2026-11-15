package com.marketingagent.integration.naver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.common.crypto.SecretCipher;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.account.AdAccount;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 광고 계정에 맞는 클라이언트를 만든다.
 * 계정마다 자격증명이 다르므로 클라이언트를 전역 빈 하나로 두지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SearchAdClientFactory {

    private final NaverSearchAdProperties properties;
    private final ObjectMapper objectMapper;
    private final SecretCipher secretCipher;

    /** Stub 은 승인된 입찰가 등을 메모리에 기억하므로 계정별로 하나씩 유지한다. */
    private final Map<Long, StubSearchAdClient> stubs = new ConcurrentHashMap<>();

    public SearchAdClient forAccount(AdAccount account) {
        if (account.isStub()) {
            return stubs.computeIfAbsent(account.getId(), StubSearchAdClient::new);
        }
        if (!account.hasCredentials()) {
            throw new BadRequestException("광고 계정 [" + account.getName()
                    + "] 의 자격증명이 비어 있습니다. 고객 ID, 액세스 라이선스, 비밀키를 입력하세요.");
        }
        return new NaverSearchAdClient(new NaverCredentials(properties.baseUrl(), account.getCustomerId(),
                account.getApiKey(), secretCipher.decrypt(account.getSecretKeyEnc())), objectMapper);
    }

    public void forget(Long accountId) {
        stubs.remove(accountId);
    }
}
