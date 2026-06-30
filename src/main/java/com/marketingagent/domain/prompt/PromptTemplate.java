package com.marketingagent.domain.prompt;

import com.marketingagent.common.BaseEntity;
import com.marketingagent.common.ContentType;
import com.marketingagent.domain.brand.Brand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "prompt_templates")
@Getter
@NoArgsConstructor
public class PromptTemplate extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentType contentType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String userPromptTemplate;

    public PromptTemplate(String name, Brand brand, ContentType contentType,
            String systemPrompt, String userPromptTemplate) {
        this.name = name;
        this.brand = brand;
        this.contentType = contentType;
        this.systemPrompt = systemPrompt;
        this.userPromptTemplate = userPromptTemplate;
    }

    public void update(String name, ContentType contentType, String systemPrompt, String userPromptTemplate) {
        this.name = name;
        this.contentType = contentType;
        this.systemPrompt = systemPrompt;
        this.userPromptTemplate = userPromptTemplate;
    }
}
