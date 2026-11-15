package com.marketingagent.domain.ads.account;

import java.time.LocalDateTime;

/** 비밀키는 담지 않는다. 액세스 라이선스도 앞 몇 글자만 보여 준다. */
public record AdAccountResponse(
        Long id,
        String name,
        String provider,
        String customerId,
        String apiKeyPreview,
        boolean credentialsSet,
        Long targetCpa,
        LocalDateTime createdAt
) {
    public static AdAccountResponse from(AdAccount account) {
        String apiKey = account.getApiKey();
        String preview = apiKey == null || apiKey.isBlank() ? null
                : apiKey.substring(0, Math.min(6, apiKey.length())) + "...";
        return new AdAccountResponse(account.getId(), account.getName(), account.getProvider(),
                account.getCustomerId(), preview, account.isStub() || account.hasCredentials(),
                account.getTargetCpa(), account.getCreatedAt());
    }
}
