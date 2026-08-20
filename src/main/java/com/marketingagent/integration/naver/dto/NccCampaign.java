package com.marketingagent.integration.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NccCampaign(
        String nccCampaignId,
        String name,
        String campaignTp,
        String status,
        Long dailyBudget,
        Boolean userLock
) {}
