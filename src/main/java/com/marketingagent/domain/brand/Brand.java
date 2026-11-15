package com.marketingagent.domain.brand;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "brands")
@Getter
@NoArgsConstructor
public class Brand extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String toneAndManner;

    @Column(columnDefinition = "TEXT")
    private String cta;

    @Column(columnDefinition = "TEXT")
    private String forbiddenWords;

    @Column(columnDefinition = "TEXT")
    private String allowedWords;

    @Column(columnDefinition = "TEXT")
    private String seoRules;

    private Long ownerId;

    /** 이 브랜드를 가진 업체. 같은 업체 사용자끼리는 브랜드를 함께 쓴다. */
    @Column(name = "company_id")
    private Long companyId;

    public void assignCompany(Long companyId) {
        this.companyId = companyId;
    }

    public Brand(String name, String description, String toneAndManner, String cta,
            String forbiddenWords, String allowedWords, String seoRules, Long ownerId, Long companyId) {
        this.companyId = companyId;
        this.name = name;
        this.description = description;
        this.toneAndManner = toneAndManner;
        this.cta = cta;
        this.forbiddenWords = forbiddenWords;
        this.allowedWords = allowedWords;
        this.seoRules = seoRules;
        this.ownerId = ownerId;
    }

    public void update(String name, String description, String toneAndManner, String cta,
            String forbiddenWords, String allowedWords, String seoRules) {
        this.name = name;
        this.description = description;
        this.toneAndManner = toneAndManner;
        this.cta = cta;
        this.forbiddenWords = forbiddenWords;
        this.allowedWords = allowedWords;
        this.seoRules = seoRules;
    }
}
