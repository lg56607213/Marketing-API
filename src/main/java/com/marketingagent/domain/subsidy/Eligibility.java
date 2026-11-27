package com.marketingagent.domain.subsidy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 공고 1건의 자격요건. AI 가 공고문에서 한 번만 뽑아 저장한다. 공고에 없는 값은 null(추측 금지).
 *
 * @param supportType 자금 · 보증 · R&D · 사업화 · 마케팅 · 교육 · 인력 · 수출 · 기타
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Eligibility(
        List<String> regions,
        @JsonProperty("entity_types") List<String> entityTypes,
        @JsonProperty("min_biz_age_years") Double minBizAgeYears,
        @JsonProperty("max_biz_age_years") Double maxBizAgeYears,
        @JsonProperty("preliminary_founder_ok") Boolean preliminaryFounderOk,
        @JsonProperty("target_industries") List<String> targetIndustries,
        @JsonProperty("excluded_industries") List<String> excludedIndustries,
        @JsonProperty("max_revenue_krw") Long maxRevenueKrw,
        @JsonProperty("max_employees") Integer maxEmployees,
        @JsonProperty("required_certs") List<String> requiredCerts,
        List<String> preferred,
        @JsonProperty("ceo_age_max") Integer ceoAgeMax,
        @JsonProperty("support_type") String supportType,
        @JsonProperty("amount_text") String amountText,
        String summary
) {}
