package com.marketingagent.domain.ads;

import com.marketingagent.domain.ads.account.AdAccount;
import com.marketingagent.domain.ads.account.AdAccountService;
import com.marketingagent.domain.ads.dto.SyncResult;
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
 * 같은 키를 다시 받으면 새 행을 만들지 않고 갱신한다. 모든 행은 계정 ID 로 구분된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdSyncService {

    private final AdAccountService accountService;
    private final AdCampaignRepository campaignRepository;
    private final AdGroupRepository adGroupRepository;
    private final AdKeywordRepository keywordRepository;
    private final KeywordStatDailyRepository statRepository;

    /** 로그인한 사용자가 자기 업체의 계정을 동기화한다. */
    @Transactional
    public SyncResult sync(Long accountId, int days) {
        return syncAccount(accountService.require(accountId), days);
    }

    /** 일 배치처럼 로그인 사용자가 없는 곳에서 쓴다. 호출부가 계정 소유를 이미 보장해야 한다. */
    @Transactional
    public SyncResult syncAccount(AdAccount account, int days) {
        SearchAdClient client = accountService.client(account);
        Long accountId = account.getId();
        LocalDate until = LocalDate.now().minusDays(1);
        LocalDate since = until.minusDays(Math.max(days, 1) - 1L);

        int campaignCount = 0;
        int adgroupCount = 0;
        List<String> keywordIds = new ArrayList<>();

        for (NccCampaign campaign : client.listCampaigns()) {
            upsertCampaign(accountId, campaign);
            campaignCount++;

            for (NccAdgroup adgroup : client.listAdgroups(campaign.nccCampaignId())) {
                upsertAdgroup(accountId, adgroup);
                adgroupCount++;

                for (NccKeyword keyword : client.listKeywords(adgroup.nccAdgroupId())) {
                    upsertKeyword(accountId, keyword);
                    keywordIds.add(keyword.nccKeywordId());
                }
            }
        }

        List<StatRow> rows = client.dailyStats(keywordIds, since, until);
        rows.forEach(row -> upsertStat(accountId, row));

        log.info("검색광고 동기화 완료: 계정 {}({}), 캠페인 {}, 광고그룹 {}, 키워드 {}, 성과 {}건 ({} ~ {})",
                account.getName(), accountId, campaignCount, adgroupCount, keywordIds.size(), rows.size(),
                since, until);

        return new SyncResult(account.getProvider(), campaignCount, adgroupCount,
                keywordIds.size(), rows.size(), since, until);
    }

    private void upsertCampaign(Long accountId, NccCampaign source) {
        campaignRepository.findByAdAccountIdAndNccCampaignId(accountId, source.nccCampaignId())
                .ifPresentOrElse(
                        existing -> existing.update(source.name(), source.campaignTp(),
                                source.status(), source.dailyBudget()),
                        () -> campaignRepository.save(new AdCampaign(accountId, source.nccCampaignId(),
                                source.name(), source.campaignTp(), source.status(), source.dailyBudget())));
    }

    private void upsertAdgroup(Long accountId, NccAdgroup source) {
        adGroupRepository.findByAdAccountIdAndNccAdgroupId(accountId, source.nccAdgroupId())
                .ifPresentOrElse(
                        existing -> existing.update(source.nccCampaignId(), source.name(), source.status(),
                                source.bidAmt(), source.dailyBudget()),
                        () -> adGroupRepository.save(new AdGroup(accountId, source.nccAdgroupId(),
                                source.nccCampaignId(), source.name(), source.status(), source.bidAmt(),
                                source.dailyBudget())));
    }

    private void upsertKeyword(Long accountId, NccKeyword source) {
        keywordRepository.findByAdAccountIdAndNccKeywordId(accountId, source.nccKeywordId())
                .ifPresentOrElse(
                        existing -> existing.update(source.keyword(), source.status(),
                                source.bidAmt(), source.useGroupBidAmt()),
                        () -> keywordRepository.save(new AdKeyword(accountId, source.nccKeywordId(),
                                source.nccAdgroupId(), source.keyword(), source.status(), source.bidAmt(),
                                source.useGroupBidAmt())));
    }

    private void upsertStat(Long accountId, StatRow row) {
        statRepository.findByAdAccountIdAndNccKeywordIdAndStatDate(accountId, row.id(), row.statDate())
                .ifPresentOrElse(
                        existing -> existing.update(row.impCnt(), row.clkCnt(), row.salesAmt(),
                                row.ctr(), row.cpc(), row.avgRnk(), row.ccnt()),
                        () -> statRepository.save(new KeywordStatDaily(accountId, row.id(), row.statDate(),
                                row.impCnt(), row.clkCnt(), row.salesAmt(), row.ctr(), row.cpc(),
                                row.avgRnk(), row.ccnt())));
    }
}
