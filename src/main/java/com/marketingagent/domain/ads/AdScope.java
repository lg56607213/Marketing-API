package com.marketingagent.domain.ads;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 계정의 키워드 -> 광고그룹 -> 캠페인 관계를 메모리에 올려 둔 것.
 * 성과는 키워드 단위로 쌓이므로, 캠페인별로 보려면 이 관계로 걸러야 한다.
 */
public class AdScope {

    private final Map<String, AdKeyword> keywords = new HashMap<>();
    private final Map<String, AdGroup> adgroups = new HashMap<>();
    private final Map<String, AdCampaign> campaigns = new HashMap<>();

    public AdScope(List<AdCampaign> campaigns, List<AdGroup> adgroups, List<AdKeyword> keywords) {
        campaigns.forEach(c -> this.campaigns.putIfAbsent(c.getNccCampaignId(), c));
        adgroups.forEach(g -> this.adgroups.putIfAbsent(g.getNccAdgroupId(), g));
        keywords.forEach(k -> this.keywords.putIfAbsent(k.getNccKeywordId(), k));
    }

    public static AdScope load(Long accountId, AdCampaignRepository campaignRepository,
            AdGroupRepository adGroupRepository, AdKeywordRepository keywordRepository) {
        return new AdScope(campaignRepository.findByAdAccountIdOrderByNameAsc(accountId),
                adGroupRepository.findByAdAccountIdOrderByNameAsc(accountId),
                keywordRepository.findByAdAccountId(accountId));
    }

    public AdKeyword keyword(String nccKeywordId) {
        return keywords.get(nccKeywordId);
    }

    public Iterable<AdKeyword> keywords() {
        return keywords.values();
    }

    public AdGroup adgroupOf(AdKeyword keyword) {
        return keyword == null ? null : adgroups.get(keyword.getNccAdgroupId());
    }

    /** 키워드가 속한 캠페인 ID. 관계를 찾지 못하면 null. */
    public String campaignIdOf(String nccKeywordId) {
        AdGroup group = adgroupOf(keywords.get(nccKeywordId));
        return group == null ? null : group.getNccCampaignId();
    }

    public AdCampaign campaign(String nccCampaignId) {
        return nccCampaignId == null ? null : campaigns.get(nccCampaignId);
    }

    public String campaignName(String nccCampaignId) {
        AdCampaign campaign = campaign(nccCampaignId);
        return campaign == null ? null : campaign.getName();
    }

    /** campaignId 가 null 이면 계정 전체를 뜻하므로 항상 참이다. */
    public boolean inCampaign(String nccKeywordId, String campaignId) {
        return campaignId == null || campaignId.equals(campaignIdOf(nccKeywordId));
    }
}
