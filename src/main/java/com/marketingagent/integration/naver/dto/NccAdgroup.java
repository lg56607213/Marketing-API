package com.marketingagent.integration.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NccAdgroup(
        String nccAdgroupId,
        String nccCampaignId,
        String name,
        String status,
        Long bidAmt,
        Long dailyBudget,
        Boolean userLock
) {}
