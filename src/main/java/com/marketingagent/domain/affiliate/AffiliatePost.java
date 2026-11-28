package com.marketingagent.domain.affiliate;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 브랜드커넥트 상품 1개로 만든 블로그 글 초안. 발행은 사용자가 직접 한다. */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "affiliate_posts")
public class AffiliatePost extends BaseEntity {

    public static final String WRITING = "WRITING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    @Column(nullable = false)
    private Long companyId;

    private Long createdBy;

    @Column(nullable = false, length = 200)
    private String productName;

    @Column(nullable = false, length = 1000)
    private String linkUrl;

    @Column(columnDefinition = "TEXT")
    private String productJson;

    @Column(columnDefinition = "TEXT")
    private String productNotes;

    @Column(columnDefinition = "TEXT")
    private String review;

    @Column(columnDefinition = "TEXT")
    private String photoCaptions;

    @Column(length = 100)
    private String keyword;

    @Column(length = 300)
    private String title;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String postJson;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 500)
    private String error;

    public AffiliatePost(Long companyId, Long createdBy) {
        this.companyId = companyId;
        this.createdBy = createdBy;
        this.status = WRITING;
    }

    public void input(String productName, String linkUrl, String productNotes, String review, String photoCaptions,
                      String keyword) {
        this.productName = productName;
        this.linkUrl = linkUrl;
        this.productNotes = productNotes;
        this.review = review;
        this.photoCaptions = photoCaptions;
        this.keyword = keyword;
        this.status = WRITING;
        this.error = null;
    }

    public void retry() {
        this.status = WRITING;
        this.error = null;
    }

    public void product(String productJson) {
        this.productJson = productJson;
    }

    public void written(String title, String postJson) {
        this.title = title;
        this.postJson = postJson;
        this.status = DONE;
        this.error = null;
    }

    public void failed(String error) {
        this.status = FAILED;
        this.error = error == null ? null : error.substring(0, Math.min(500, error.length()));
    }
}
