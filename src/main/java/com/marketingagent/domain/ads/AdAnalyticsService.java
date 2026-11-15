package com.marketingagent.domain.ads;

import com.marketingagent.domain.ads.account.AdAccount;
import com.marketingagent.domain.ads.account.AdAccountService;
import com.marketingagent.domain.ads.dto.AdSummary;
import com.marketingagent.domain.ads.dto.DailyPoint;
import com.marketingagent.domain.ads.dto.KeywordAggregate;
import com.marketingagent.domain.ads.dto.KeywordPerformance;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 적재된 성과 데이터를 조회용으로 집계한다.
 * CTR/CPC/CVR 은 일자별 평균이 아니라 기간 합계에서 다시 계산한다.
 * 계정은 필수이고, 캠페인을 주면 그 캠페인에 속한 키워드만 본다.
 */
@Service
@RequiredArgsConstructor
public class AdAnalyticsService {

    private final AdAccountService accountService;
    private final KeywordStatDailyRepository statRepository;
    private final AdKeywordRepository keywordRepository;
    private final AdGroupRepository adGroupRepository;
    private final AdCampaignRepository campaignRepository;

    @Transactional(readOnly = true)
    public List<AdCampaign> campaigns(Long accountId) {
        AdAccount account = accountService.require(accountId);
        return campaignRepository.findByAdAccountIdOrderByNameAsc(account.getId());
    }

    @Transactional(readOnly = true)
    public List<AdGroup> adgroups(Long accountId, String campaignId) {
        AdAccount account = accountService.require(accountId);
        String campaign = accountService.normalizeCampaign(account, campaignId);
        return campaign == null
                ? adGroupRepository.findByAdAccountIdOrderByNameAsc(account.getId())
                : adGroupRepository.findByAdAccountIdAndNccCampaignIdOrderByNameAsc(account.getId(), campaign);
    }

    /** 캠페인별 목표 CPA 를 정한다. 0 또는 null 이면 지정을 해제해 계정 기본값을 따르게 한다. */
    @Transactional
    public AdCampaign changeTargetCpa(Long accountId, String campaignId, Long targetCpa) {
        AdAccount account = accountService.require(accountId);
        AdCampaign campaign = campaignRepository
                .findByAdAccountIdAndNccCampaignId(account.getId(), campaignId)
                .orElseThrow(() -> new com.marketingagent.common.exception.ResourceNotFoundException(
                        "캠페인을 찾을 수 없습니다: " + campaignId));
        campaign.changeTargetCpa(targetCpa);
        return campaign;
    }

    @Transactional(readOnly = true)
    public List<KeywordPerformance> keywordPerformance(Long accountId, String campaignId,
            LocalDate since, LocalDate until) {
        AdAccount account = accountService.require(accountId);
        String campaign = accountService.normalizeCampaign(account, campaignId);
        AdScope scope = scope(account.getId());

        return statRepository.aggregateByKeyword(account.getId(), since, until).stream()
                .filter(row -> scope.inCampaign(row.nccKeywordId(), campaign))
                .map(row -> toPerformance(row, scope))
                .sorted(Comparator.comparingLong(KeywordPerformance::salesAmt).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public AdSummary summary(Long accountId, String campaignId, LocalDate since, LocalDate until) {
        AdAccount account = accountService.require(accountId);
        String campaign = accountService.normalizeCampaign(account, campaignId);
        AdScope scope = scope(account.getId());

        List<KeywordAggregate> rows = statRepository.aggregateByKeyword(account.getId(), since, until).stream()
                .filter(row -> scope.inCampaign(row.nccKeywordId(), campaign))
                .toList();

        long impCnt = rows.stream().mapToLong(r -> nz(r.impCnt())).sum();
        long clkCnt = rows.stream().mapToLong(r -> nz(r.clkCnt())).sum();
        long salesAmt = rows.stream().mapToLong(r -> nz(r.salesAmt())).sum();
        long ccnt = rows.stream().mapToLong(r -> nz(r.ccnt())).sum();

        List<DailyPoint> daily = campaign == null
                ? statRepository.aggregateByDate(account.getId(), since, until)
                : statRepository.aggregateByDateForCampaign(account.getId(), campaign, since, until);

        return new AdSummary(since, until, impCnt, clkCnt,
                ratio(clkCnt * 100.0, impCnt), ratio(salesAmt, clkCnt),
                salesAmt, ccnt, ratio(ccnt * 100.0, clkCnt), ratio(salesAmt, ccnt),
                rows.size(), daily);
    }

    private AdScope scope(Long accountId) {
        return AdScope.load(accountId, campaignRepository, adGroupRepository, keywordRepository);
    }

    private KeywordPerformance toPerformance(KeywordAggregate row, AdScope scope) {
        AdKeyword keyword = scope.keyword(row.nccKeywordId());
        AdGroup adgroup = scope.adgroupOf(keyword);
        String campaignId = adgroup != null ? adgroup.getNccCampaignId() : null;

        long impCnt = nz(row.impCnt());
        long clkCnt = nz(row.clkCnt());
        long salesAmt = nz(row.salesAmt());
        long ccnt = nz(row.ccnt());

        return new KeywordPerformance(
                row.nccKeywordId(),
                keyword != null ? keyword.getKeyword() : row.nccKeywordId(),
                adgroup != null ? adgroup.getName() : "-",
                campaignId,
                campaignId != null ? scope.campaignName(campaignId) : null,
                keyword != null ? keyword.getStatus() : null,
                keyword != null ? keyword.getBidAmt() : null,
                impCnt,
                clkCnt,
                ratio(clkCnt * 100.0, impCnt),
                ratio(salesAmt, clkCnt),
                salesAmt,
                ccnt,
                ratio(ccnt * 100.0, clkCnt),
                weightedRank(row.rnkWeighted(), impCnt));
    }

    /** 노출가중 평균순위. 노출이 없던 날의 0 이 섞여 순위가 좋아 보이지 않도록 노출수로 가중한다. */
    private double weightedRank(Double rnkWeighted, long impCnt) {
        if (rnkWeighted == null || impCnt == 0) {
            return 0.0;
        }
        return round2(rnkWeighted / impCnt);
    }

    /** 분모가 0이면 0을 돌려준다. 노출·클릭이 없는 키워드에서 나누기가 터지지 않도록. */
    private double ratio(double numerator, long denominator) {
        return denominator == 0 ? 0.0 : round2(numerator / denominator);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private long nz(Long value) {
        return value != null ? value : 0L;
    }
}
