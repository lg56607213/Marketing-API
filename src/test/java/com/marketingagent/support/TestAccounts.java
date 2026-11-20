package com.marketingagent.support;

import com.marketingagent.common.security.SystemTask;
import com.marketingagent.domain.ads.account.AdAccount;
import com.marketingagent.domain.ads.account.AdAccountRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 테스트마다 서로 섞이지 않는 예시 데이터 광고 계정을 준다.
 * 같은 키를 다시 쓰면 같은 계정을 돌려주고, 작업은 그 계정의 시스템 작업 범위 안에서 실행한다.
 */
@Component
public class TestAccounts {

    private final AdAccountRepository accountRepository;
    private final Map<Long, Long> accounts = new ConcurrentHashMap<>();

    public TestAccounts(AdAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** 키에 해당하는 예시 계정 ID. 업체 ID 도 키로 둬서 업체끼리도 섞이지 않게 한다. */
    public Long id(long key) {
        return accounts.computeIfAbsent(key, k -> accountRepository.save(
                new AdAccount(k, "테스트 계정 " + k, AdAccount.PROVIDER_STUB, null, null, null, null)).getId());
    }

    public void runAs(long key, Consumer<Long> work) {
        Long id = id(key);
        SystemTask.runFor(id, () -> work.accept(id));
    }

    public <T> T callAs(long key, Function<Long, T> work) {
        Long id = id(key);
        return SystemTask.callFor(id, () -> work.apply(id));
    }
}
