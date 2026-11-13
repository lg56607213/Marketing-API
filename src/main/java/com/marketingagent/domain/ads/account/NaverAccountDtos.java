package com.marketingagent.domain.ads.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class NaverAccountDtos {

    private NaverAccountDtos() {
    }

    /** 연결 요청. 세 값 모두 검색광고 관리자 > 도구 > API 사용 관리에서 발급한다. */
    public record ConnectRequest(
            @NotBlank @Pattern(regexp = "\\d{1,20}", message = "고객 ID 는 숫자만 입력하세요") String customerId,
            @NotBlank @Size(max = 300) String apiKey,
            @NotBlank @Size(max = 300) String secretKey
    ) {

        @Override
        public String toString() {
            return "ConnectRequest[customerId=" + customerId + "]";
        }
    }

    /**
     * 연결 상태. 비밀키는 어떤 경우에도 돌려주지 않는다.
     *
     * @param provider     stub(가짜 데이터) 또는 naver(실계정)
     * @param connected    이 회원이 네이버 계정을 연결했는지
     * @param apiKeyMasked 액세스 라이선스 끝 4자리만 남긴 값
     */
    public record AccountStatus(
            String provider,
            boolean connected,
            String customerId,
            String apiKeyMasked,
            LocalDateTime connectedAt,
            String message
    ) {}
}
