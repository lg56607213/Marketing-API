package com.marketingagent.domain.ads;

import com.marketingagent.domain.ads.dto.SyncResult;
import com.marketingagent.integration.naver.NaverSearchAdProperties;
import com.marketingagent.integration.naver.SearchAdClient;
import com.marketingagent.integration.naver.dto.NccAdgroup;
import com.marketingagent.integration.naver.dto.NccCampaign;
import com.marketingagent.integration.naver.dto.NccKeyword;
import com.marketingagent.integration.naver.dto.StatRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검색광고 계정의 캠페인/광고그룹/키워드와 일자별 성과를 내려받아 DB에 적재한다.
 * 같은 키를 다시 받으면 새 행을 만들지 않고 갱신한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdSyncService {

    private final SearchAdClient searchAdClient;
    private final NaverSearchAdProperties properties;
    private final AdCampaignRepository campaignRepository;
    private final AdGroupRepository adGroupRepository;
    private final AdKeywordRepository keywordRepository;
    private final KeywordStatDailyRepository statRepository;

    @Transactional
    public SyncResult sync(int days) {
        LocalDate until = LocalDate.now().minusDays(1);
        LocalDate since = until.minusDays(Math.max(days, 1) - 1L);

        int campaignCount = 0;
        int adgroupCount = 0;
        List<String> keywordIds = new ArrayList<>();

        for (NccCampaign campaign : searchAdClient.listCampaigns()) {
            upsertCampaign(campaign);
            campaignCount++;

            for (NccAdgroup adgroup : searchAdClient.listAdgroups(campaign.nccCampaignId())) {
                upsertAdgroup(adgroup);
                adgroupCount++;

                for (NccKeyword keyword : searchAdClient.listKeywords(adgroup.nccAdgroupId())) {
                    upsertKeyword(keyword);
                    keywordIds.add(keyword.nccKeywordId());
                }
            }
        }

        List<StatRow> rows = searchAdClient.dailyStats(keywordIds, since, until);
        rows.forEach(this::upsertStat);

        log.info("검색광고 동기화 완료: 캠페인 {}, 광고그룹 {}, 키워드 {}, 성과 {}건 ({} ~ {})",
                campaignCount, adgroupCount, keywordIds.size(), rows.size(), since, until);

        return new SyncResult(properties.provider(), campaignCount, adgroupCount,
                keywordIds.size(), rows.size(), since, until);
    }

    private void upsertCampaign(NccCampaign source) {
        campaignRepository.findByNccCampaignId(source.nccCampaignId())
                .ifPresentOrElse(
                        existing -> existing.update(source.name(), source.campaignTp(),
                                source.status(), source.dailyBudget()),
                        () -> campaignRepository.save(new AdCampaign(source.nccCampaignId(), source.name(),
                                source.campaignTp(), source.status(), source.dailyBudget())));
    }

    private void upsertAdgroup(NccAdgroup source) {
        adGroupRepository.findByNccAdgroupId(source.nccAdgroupId())
                .ifPresentOrElse(
                        existing -> existing.update(source.name(), source.status(),
                                source.bidAmt(), source.dailyBudget()),
                        () -> adGroupRepository.save(new AdGroup(source.nccAdgroupId(), source.nccCampaignId(),
                                source.name(), source.status(), source.bidAmt(), source.dailyBudget())));
    }

    private void upsertKeyword(NccKeyword source) {
        keywordRepository.findByNccKeywordId(source.nccKeywordId())
                .ifPresentOrElse(
                        existing -> existing.update(source.keyword(), source.status(),
                                source.bidAmt(), source.useGroupBidAmt()),
                        () -> keywordRepository.save(new AdKeyword(source.nccKeywordId(), source.nccAdgroupId(),
                                source.keyword(), source.status(), source.bidAmt(), source.useGroupBidAmt())));
    }

    private void upsertStat(StatRow row) {
        statRepository.findByNccKeywordIdAndStatDate(row.id(), row.statDate())
                .ifPresentOrElse(
                        existing -> existing.update(row.impCnt(), row.clkCnt(), row.salesAmt(),
                                row.ctr(), row.cpc(), row.avgRnk(), row.ccnt()),
                        () -> statRepository.save(new KeywordStatDaily(row.id(), row.statDate(),
                                row.impCnt(), row.clkCnt(), row.salesAmt(), row.ctr(), row.cpc(),
                                row.avgRnk(), row.ccnt())));
    }
}
