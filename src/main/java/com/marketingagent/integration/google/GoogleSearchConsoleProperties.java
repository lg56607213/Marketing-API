package com.marketingagent.integration.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 구글 서치콘솔 연동 설정.
 *
 * <p>서치콘솔은 OAuth 2.0 을 쓴다. 사용자가 한 번 동의해 리프레시 토큰을 발급받아 두면
 * 이후에는 그 토큰으로 액세스 토큰을 갱신해 조회한다.
 *
 * @param provider     google 이면 실제 호출, stub 이면 예시 데이터
 * @param siteUrl      서치콘솔에 등록된 속성 주소 (예: https://www.mytruck.kr/ 또는 sc-domain:mytruck.kr)
 * @param clientId     구글 클라우드 OAuth 클라이언트 ID
 * @param clientSecret 구글 클라우드 OAuth 클라이언트 시크릿
 * @param refreshToken 최초 동의 후 발급받은 리프레시 토큰
 */
@ConfigurationProperties("google.searchconsole")
public record GoogleSearchConsoleProperties(
        String provider,
        String siteUrl,
        String clientId,
        String clientSecret,
        String refreshToken
) {
    public boolean configured() {
        return notBlank(siteUrl) && notBlank(clientId) && notBlank(clientSecret) && notBlank(refreshToken);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
