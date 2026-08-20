package com.marketingagent.domain.ads;

public enum BidStrategy {
    /** 성과(CPA/CTR) 기반 점진 조정. 1회 변동폭 제한을 적용한다. */
    PERFORMANCE,
    /** 목표 평균노출순위 기반. 네이버 추정 입찰가가 목표이므로 변동폭 제한을 적용하지 않는다. */
    TARGET_RANK
}
