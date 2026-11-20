package com.marketingagent.domain.goal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 목표 상담에서 파악한 사업 구조. 업종을 코드로 정해 두지 않고 대화로 채운다.
 *
 * <p>계산에 필요한 것은 업종이 무엇이든 같다. 계약 1건으로 버는 돈(매출)과 남는 돈(이익), 문의가 계약이 되는 비율,
 * 한 고객이 계약하는 횟수, 그리고 광고로 얻고 싶은 것. 숫자마다 사용자가 알려 준 값인지, 모르는 값이라 가정한 것인지,
 * 운영하면서 잰 값인지를 함께 남긴다. 가정한 숫자는 계획을 보수적으로 잡는 근거가 된다.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BusinessModel {

    public static final String SOURCE_USER = "USER";
    public static final String SOURCE_ASSUMED = "ASSUMED";
    public static final String SOURCE_MEASURED = "MEASURED";

    /** 업종을 사람이 쓴 그대로. 예: 렌터카 대여, 렌트·리스 중개 */
    private String industry;

    /** 돈을 버는 방식 */
    private RevenueModel revenueModel;

    /** 광고로 팔 상품·서비스 */
    private List<Offering> offerings = new ArrayList<>();

    /** 광고로 들어온 문의 중 계약이 되는 비율(%) */
    private Double leadToDealPct;
    private String leadToDealSource;
    /** 실제 계약 결과로 바꾸기 전, 사용자가 알려 줬거나 가정한 값. 결과가 적을 때 이 값 쪽으로 당겨 잡는다 */
    private Double leadToDealPriorPct;

    /** 한 고객이 평균 몇 번 계약하는지(재계약 포함). 정기 결제면 평균 유지 개월 수 */
    private Double dealsPerCustomer;
    private String dealsPerCustomerSource;

    private GoalType goalType;
    /** 매출·이익이면 원, 계약·문의면 건수 */
    private Long goalAmount;

    /** 계약 1건으로 남는 돈 중 광고비로 쓸 수 있는 비율(%) */
    private Double adSharePct;
    private String adShareSource;

    /** 한 달 광고비 상한(원). 0 이면 상한 없음, null 이면 아직 묻지 않음 */
    private Long budgetLimit;

    /** 고객이 검색할 핵심 키워드 */
    private List<String> seedKeywords = new ArrayList<>();

    public enum RevenueModel {
        /** 직접 팔거나 빌려준다 */
        DIRECT,
        /** 중개하고 수수료를 받는다 */
        COMMISSION,
        /** 매달 정기 결제를 받는다 */
        SUBSCRIPTION
    }

    public enum GoalType {
        REVENUE, PROFIT, DEALS, LEADS
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Offering {
        private String name;
        /** 계약 1건의 매출. 수수료 사업이면 받는 수수료, 정기 결제면 한 달 결제액 */
        private Long revenuePerDeal;
        /** 계약 1건으로 실제로 남는 돈 */
        private Long profitPerDeal;
        private String profitSource;

        public Offering() {
        }

        public Offering(String name) {
            this.name = name;
        }
    }

    /** 계산을 시작하기 전에 채워야 할 다음 항목. 다 채워졌으면 null. 묻는 순서도 이 순서다. */
    public Slot nextMissing() {
        if (isBlank(industry)) {
            return new Slot(SlotType.INDUSTRY, null);
        }
        if (revenueModel == null) {
            return new Slot(SlotType.REVENUE_MODEL, null);
        }
        if (offerings == null || offerings.isEmpty()) {
            return new Slot(SlotType.OFFERINGS, null);
        }
        for (int i = 0; i < offerings.size(); i++) {
            if (offerings.get(i).getRevenuePerDeal() == null) {
                return new Slot(SlotType.OFFERING_REVENUE, i);
            }
            if (offerings.get(i).getProfitPerDeal() == null) {
                return new Slot(SlotType.OFFERING_PROFIT, i);
            }
        }
        if (leadToDealPct == null) {
            return new Slot(SlotType.LEAD_TO_DEAL, null);
        }
        if (dealsPerCustomer == null) {
            return new Slot(SlotType.DEALS_PER_CUSTOMER, null);
        }
        if (goalType == null || goalAmount == null) {
            return new Slot(SlotType.GOAL, null);
        }
        if (adSharePct == null) {
            return new Slot(SlotType.AD_SHARE, null);
        }
        if (budgetLimit == null) {
            return new Slot(SlotType.BUDGET_LIMIT, null);
        }
        if (seedKeywords == null || seedKeywords.isEmpty()) {
            return new Slot(SlotType.KEYWORDS, null);
        }
        return null;
    }

    /** 아직 채우지 않은 항목 이름을 묻는 순서대로. AI 상담원에게 무엇이 남았는지 알려 줄 때 쓴다. */
    public List<String> remainingItems() {
        List<String> items = new ArrayList<>();
        if (isBlank(industry)) {
            items.add("industry");
        }
        if (revenueModel == null) {
            items.add("revenueModel");
        }
        if (offerings == null || offerings.isEmpty()) {
            items.add("offerings");
        } else {
            for (Offering o : offerings) {
                if (o.getRevenuePerDeal() == null) {
                    items.add("offerings[" + o.getName() + "].revenuePerDeal");
                }
                if (o.getProfitPerDeal() == null) {
                    items.add("offerings[" + o.getName() + "].profitPerDeal");
                }
            }
        }
        if (leadToDealPct == null) {
            items.add("leadToDealPct");
        }
        if (dealsPerCustomer == null) {
            items.add("dealsPerCustomer");
        }
        if (goalType == null || goalAmount == null) {
            items.add("goalType+goalAmount");
        }
        if (adSharePct == null) {
            items.add("adSharePct");
        }
        if (budgetLimit == null) {
            items.add("budgetLimit");
        }
        if (seedKeywords == null || seedKeywords.isEmpty()) {
            items.add("seedKeywords");
        }
        return items;
    }

    /** 사용자가 모른다고 해서 가정으로 채운 항목이 있는지. 있으면 시험 운영부터 권한다. */
    public boolean hasAssumptions() {
        return SOURCE_ASSUMED.equals(leadToDealSource)
                || SOURCE_ASSUMED.equals(dealsPerCustomerSource)
                || (offerings != null && offerings.stream().anyMatch(o -> SOURCE_ASSUMED.equals(o.getProfitSource())));
    }

    public enum SlotType {
        INDUSTRY, REVENUE_MODEL, OFFERINGS, OFFERING_REVENUE, OFFERING_PROFIT, LEAD_TO_DEAL, DEALS_PER_CUSTOMER,
        GOAL, AD_SHARE, BUDGET_LIMIT, KEYWORDS
    }

    /** @param index 상품별 항목이면 몇 번째 상품인지 */
    public record Slot(SlotType type, Integer index) {}

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
