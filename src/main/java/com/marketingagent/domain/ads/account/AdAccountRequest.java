package com.marketingagent.domain.ads.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * @param provider  naver 또는 stub. 비우면 naver. 등록 후에는 바꿀 수 없다.
 * @param secretKey 수정할 때 비워 두면 기존 값을 유지한다.
 * @param targetCpa 계정 기본 목표 CPA. 비우거나 0이면 미지정.
 */
public record AdAccountRequest(
        @NotBlank @Size(max = 100) String name,
        String provider,
        @Size(max = 50) String customerId,
        @Size(max = 200) String apiKey,
        @Size(max = 200) String secretKey,
        @PositiveOrZero Long targetCpa
) {}
