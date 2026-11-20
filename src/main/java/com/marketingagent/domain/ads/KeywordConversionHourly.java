package com.marketingagent.domain.ads;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 키워드 단위 시간대별 전환. 전환방식(직접/간접)과 전환유형(구매, 회원가입 등)별로 나뉜다.
 * 간접전환은 며칠 뒤에 잡히므로 최근 일자는 반복해서 다시 받는다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "keyword_conversion_hourly",
        uniqueConstraints = @UniqueConstraint(name = "uk_keyword_conversion_hourly",
                columnNames = {"ad_account_id", "stat_date", "stat_hour", "ncc_adgroup_id", "ncc_keyword_id", "device",
                        "conv_method", "conv_type"}),
        indexes = @Index(name = "ix_keyword_conversion_hourly_keyword", columnList = "ncc_keyword_id, stat_date"))
public class KeywordConversionHourly extends BaseEntity {

    /** 어느 광고 계정의 데이터인지. 업체마다, 계정마다 따로 쌓인다. */
    @Column(name = "ad_account_id", nullable = false)
    private Long adAccountId;


    public static final int METHOD_DIRECT = 1;
    public static final String TYPE_PURCHASE = "purchase";

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    /** 0~23 */
    @Column(name = "stat_hour", nullable = false)
    private int statHour;

    @Column(name = "ncc_campaign_id", nullable = false, length = 100)
    private String nccCampaignId;

    @Column(name = "ncc_adgroup_id", nullable = false, length = 100)
    private String nccAdgroupId;

    @Column(name = "ncc_keyword_id", nullable = false, length = 100)
    private String nccKeywordId;

    /** P(PC) 또는 M(모바일) */
    @Column(nullable = false, length = 10)
    private String device;

    /** 1 직접전환, 2 간접전환 */
    @Column(name = "conv_method", nullable = false)
    private int convMethod;

    /** purchase, sign_up, add_to_cart, lead, custom001 등 */
    @Column(name = "conv_type", nullable = false, length = 50)
    private String convType;

    private long convCnt;

    /** 전환매출액 */
    private long convAmt;

    public KeywordConversionHourly(Long adAccountId, LocalDate statDate, int statHour, String nccCampaignId, String nccAdgroupId,
            String nccKeywordId, String device, int convMethod, String convType, long convCnt, long convAmt) {
        this.adAccountId = adAccountId;
        this.statDate = statDate;
        this.statHour = statHour;
        this.nccCampaignId = nccCampaignId;
        this.nccAdgroupId = nccAdgroupId;
        this.nccKeywordId = nccKeywordId;
        this.device = device;
        this.convMethod = convMethod;
        this.convType = convType;
        this.convCnt = convCnt;
        this.convAmt = convAmt;
    }
}
