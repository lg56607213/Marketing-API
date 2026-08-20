package com.marketingagent.domain.ads;

public enum BidStatus {
    /** 추천 생성됨. 사용자 승인 대기 */
    PENDING,
    /** 사용자가 거절 */
    REJECTED,
    /** 승인 후 네이버 반영 성공 */
    APPLIED,
    /** 승인했으나 반영 실패 */
    FAILED,
    /** 새 추천이 나와 밀려남 */
    SUPERSEDED
}
