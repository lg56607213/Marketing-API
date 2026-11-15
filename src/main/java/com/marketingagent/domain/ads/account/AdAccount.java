package com.marketingagent.domain.ads.account;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 업체가 등록한 네이버 검색광고 계정. 한 업체가 여러 개를 가질 수 있고,
 * 캠페인·키워드·성과·입찰 추천·리포트는 모두 계정 단위로 나뉜다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "ad_accounts")
public class AdAccount extends BaseEntity {

    public static final String PROVIDER_NAVER = "naver";
    public static final String PROVIDER_STUB = "stub";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String name;

    /** naver 면 실제 호출, stub 이면 예시 데이터 */
    @Column(nullable = false, length = 20)
    private String provider;

    @Column(length = 50)
    private String customerId;

    @Column(length = 200)
    private String apiKey;

    /** 암호화된 비밀키. 응답으로 내보내지 않는다. */
    @Column(length = 500)
    private String secretKeyEnc;

    /** 계정 기본 목표 CPA. 캠페인에 목표가 없을 때 쓴다. null 또는 0이면 미지정. */
    private Long targetCpa;

    public AdAccount(Long companyId, String name, String provider, String customerId,
            String apiKey, String secretKeyEnc, Long targetCpa) {
        this.companyId = companyId;
        this.name = name;
        this.provider = provider;
        this.customerId = customerId;
        this.apiKey = apiKey;
        this.secretKeyEnc = secretKeyEnc;
        this.targetCpa = targetCpa;
    }

    public void update(String name, Long targetCpa) {
        this.name = name;
        this.targetCpa = targetCpa;
    }

    public void updateCredentials(String customerId, String apiKey, String secretKeyEnc) {
        this.customerId = customerId;
        this.apiKey = apiKey;
        this.secretKeyEnc = secretKeyEnc;
    }

    public boolean isStub() {
        return PROVIDER_STUB.equalsIgnoreCase(provider);
    }

    public boolean hasCredentials() {
        return notBlank(customerId) && notBlank(apiKey) && notBlank(secretKeyEnc);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
