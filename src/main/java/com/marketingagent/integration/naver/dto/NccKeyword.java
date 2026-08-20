package com.marketingagent.integration.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NccKeyword(
        String nccKeywordId,
        String nccAdgroupId,
        String keyword,
        String status,
        Long bidAmt,
        Boolean useGroupBidAmt,
        Boolean userLock
) {}
