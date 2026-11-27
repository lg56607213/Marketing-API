package com.marketingagent.domain.subsidy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 정부지원사업 매칭 설정. 키가 비어 있는 소스는 수집을 건너뛰고 작업 기록에 남긴다.
 *
 * @param enabled              매일 배치를 돌릴지
 * @param bizinfoKey           기업마당 API 인증키(crtfcKey)
 * @param bizinfoUrl           기업마당 API 주소
 * @param dataGoKrKey          공공데이터포털 서비스키(K-Startup)
 * @param kstartupUrl          K-Startup 공고 조회 주소(활용신청 후 operation 이름 확인)
 * @param extractModel         공고 자격요건 추출 모델(공고 1건당 1회)
 * @param judgeModel           애매한 건 재판단 모델
 * @param maxAnalyzePerDay     하루 공고 분석 상한(AI 비용 상한)
 * @param maxJudgePerProfile   사업체당 하루 재판단 상한
 * @param maxPages             소스별 수집 최대 페이지(페이지당 100건)
 */
@ConfigurationProperties(prefix = "subsidy")
public record SubsidyProperties(
        boolean enabled,
        String bizinfoKey,
        String bizinfoUrl,
        String dataGoKrKey,
        String kstartupUrl,
        String extractModel,
        String judgeModel,
        int maxAnalyzePerDay,
        int maxJudgePerProfile,
        int maxPages
) {
    public boolean hasBizinfo() {
        return bizinfoKey != null && !bizinfoKey.isBlank();
    }

    public boolean hasKstartup() {
        return dataGoKrKey != null && !dataGoKrKey.isBlank();
    }
}
