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

    public Brand(String name, String description, String toneAndManner, String cta,
            String forbiddenWords, String allowedWords, String seoRules, Long ownerId) {
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
