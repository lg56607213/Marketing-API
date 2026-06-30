package com.marketingagent.domain.content;

import com.marketingagent.common.BaseEntity;
import com.marketingagent.common.ContentStatus;
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
@Table(name = "contents")
@Getter
@NoArgsConstructor
public class Content extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentType contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentStatus status = ContentStatus.DRAFT;

    private String title;
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    private String aiModel;
    private Long authorId;

    public Content(Brand brand, ContentType contentType, String title, String topic,
            String body, String aiModel, Long authorId) {
        this.brand = brand;
        this.contentType = contentType;
        this.status = ContentStatus.DRAFT;
        this.title = title;
        this.topic = topic;
        this.body = body;
        this.aiModel = aiModel;
        this.authorId = authorId;
    }

    public void approve() {
        if (this.status != ContentStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT content can be approved");
        }
        this.status = ContentStatus.APPROVED;
    }

    public void reject() {
        if (this.status != ContentStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT content can be rejected");
        }
        this.status = ContentStatus.REJECTED;
    }

    public void publish() {
        if (this.status != ContentStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED content can be published");
        }
        this.status = ContentStatus.PUBLISHED;
    }

    public void updateBody(String body) {
        this.body = body;
    }
}
