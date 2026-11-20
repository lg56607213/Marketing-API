package com.marketingagent.domain.goal;

import com.marketingagent.domain.goal.BusinessModel.GoalType;
import com.marketingagent.domain.goal.BusinessModel.Offering;
import com.marketingagent.domain.goal.BusinessModel.RevenueModel;
import com.marketingagent.domain.goal.BusinessModel.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * AI 없이도 상담이 되게 하는 정해진 질문 순서. 한 번에 하나만 묻고, 답을 읽지 못하면 예시를 들어 다시 묻는다.
 * AI 상담이 응답을 못 주거나 엉뚱한 값을 줄 때도 이쪽으로 이어 간다.
 *
 * <p>모른다는 답은 정해 둔 보수적인 가정으로 채우고, 가정이라고 분명히 말한다. 가정은 운영하면서 실제 값으로 바꾼다.
 */
@Component
public class RuleBasedInterviewer {

    /** 문의→계약 비율을 모를 때 쓰는 값. 업종마다 다르지만 너무 낙관하지 않도록 낮게 잡는다. */
    static final double ASSUMED_LEAD_TO_DEAL_PCT = 20.0;
    /** 이익 중 광고비 비율을 모를 때 시작 값 */
    static final double DEFAULT_AD_SHARE_PCT = 30.0;
    /** 직접 판매에서 남는 비율을 모를 때 */
    static final double ASSUMED_DIRECT_MARGIN_PCT = 30.0;

    private static final int MAX_OFFERINGS = 5;
    private static final int MAX_KEYWORDS = 5;

    /**
     * @param understood 답을 읽어 모델에 넣었는지
     * @param note       넣으면서 사용자에게 알려야 할 말(가정했다는 사실 등). 없으면 null
     */
    public record Answer(boolean understood, String note) {
        static Answer ok() {
            return new Answer(true, null);
        }

        static Answer ok(String note) {
            return new Answer(true, note);
        }

        static Answer retry(String note) {
            return new Answer(false, note);
        }
    }

    /** 지금 묻고 있는 칸에 대한 답을 읽어 모델에 넣는다. */
    public Answer apply(BusinessModel m, Slot slot, String text, List<String> suggestedKeywords) {
        String answer = text == null ? "" : text.trim();
        if (answer.isEmpty()) {
            return Answer.retry("답을 적어 주세요.");
        }
        return switch (slot.type()) {
            case INDUSTRY -> {
                m.setIndustry(limit(answer, 100));
                yield Answer.ok();
            }
            case REVENUE_MODEL -> revenueModel(answer).map(model -> {
                m.setRevenueModel(model);
                return Answer.ok();
            }).orElse(Answer.retry("1, 2, 3 중에서 골라 주세요. 판매·대여면 1, 중개 수수료면 2, 정기 결제면 3입니다."));
            case OFFERINGS -> {
                List<String> names = split(answer, MAX_OFFERINGS);
                if (names.isEmpty()) {
                    yield Answer.retry("상품이나 서비스 이름을 하나 이상 적어 주세요.");
                }
                List<Offering> offerings = new ArrayList<>();
                names.forEach(name -> offerings.add(new Offering(limit(name, 50))));
                m.setOfferings(offerings);
                yield Answer.ok(names.size() > 1
                        ? "상품마다 비중은 같다고 보고 평균으로 계산합니다. 비중이 크게 다르면 알려 주세요." : null);
            }
            case OFFERING_REVENUE -> KoreanNumbers.money(answer).map(won -> {
                m.getOfferings().get(slot.index()).setRevenuePerDeal(won);
                return Answer.ok();
            }).orElse(Answer.retry("금액으로 알려 주세요. 예: 35만원, 1,200,000원"));
            case OFFERING_PROFIT -> profit(m, slot.index(), answer);
            case LEAD_TO_DEAL -> {
                if (KoreanNumbers.unknown(answer)) {
                    m.setLeadToDealPct(ASSUMED_LEAD_TO_DEAL_PCT);
                    m.setLeadToDealSource(BusinessModel.SOURCE_ASSUMED);
                    yield Answer.ok(String.format("문의 10건 중 %d건이 계약된다고 가정하고 시작할게요. "
                            + "운영하면서 실제 계약 결과를 알려 주시면 그 값으로 바꿉니다.", Math.round(ASSUMED_LEAD_TO_DEAL_PCT / 10)));
                }
                Optional<Double> pct = KoreanNumbers.percent(answer);
                if (pct.isEmpty()) {
                    yield Answer.retry("문의 10건 중 몇 건인지, 또는 %로 알려 주세요. 예: 10건 중 3건, 30%. 모르시면 '모름'.");
                }
                m.setLeadToDealPct(pct.get());
                m.setLeadToDealSource(BusinessModel.SOURCE_USER);
                yield Answer.ok();
            }
            case DEALS_PER_CUSTOMER -> {
                if (KoreanNumbers.unknown(answer)) {
                    m.setDealsPerCustomer(1.0);
                    m.setDealsPerCustomerSource(BusinessModel.SOURCE_ASSUMED);
                    yield Answer.ok("재계약은 없다고 보고 1로 계산합니다. 손해 보지 않는 쪽으로 잡은 값입니다.");
                }
                Optional<Double> count = KoreanNumbers.count(answer);
                if (count.isEmpty() || count.get() < 1) {
                    yield Answer.retry("1 이상의 숫자로 알려 주세요. 처음 한 번뿐이면 1입니다.");
                }
                m.setDealsPerCustomer(Math.min(count.get(), 120));
                m.setDealsPerCustomerSource(BusinessModel.SOURCE_USER);
                yield Answer.ok();
            }
            case GOAL -> goal(m, answer);
            case AD_SHARE -> adShare(m, answer);
            case BUDGET_LIMIT -> {
                if (KoreanNumbers.none(answer) || KoreanNumbers.unknown(answer)) {
                    m.setBudgetLimit(0L);
                    yield Answer.ok();
                }
                yield KoreanNumbers.money(answer).map(won -> {
                    m.setBudgetLimit(won);
                    return Answer.ok();
                }).orElse(Answer.retry("금액으로 알려 주세요. 예: 200만원. 정해진 상한이 없으면 '없음'."));
            }
            case KEYWORDS -> {
                if (KoreanNumbers.yes(answer) && !suggestedKeywords.isEmpty()) {
                    m.setSeedKeywords(new ArrayList<>(suggestedKeywords.subList(0, Math.min(MAX_KEYWORDS, suggestedKeywords.size()))));
                    yield Answer.ok();
                }
                List<String> keywords = split(answer, MAX_KEYWORDS);
                if (keywords.isEmpty()) {
                    yield Answer.retry("고객이 검색할 말을 쉼표로 나눠 적어 주세요. 예: 제주 렌트카, 장기렌트 견적");
                }
                m.setSeedKeywords(new ArrayList<>(keywords));
                yield Answer.ok();
            }
        };
    }

    /** 칸마다 묻는 말. 마케팅 용어를 쓰지 않고 예시를 붙인다. */
    public String question(BusinessModel m, Slot slot, List<String> suggestedKeywords) {
        RevenueModel model = m.getRevenueModel();
        return switch (slot.type()) {
            case INDUSTRY -> "어떤 사업을 하고 계신가요? 한 줄로 알려 주세요.\n(예: 렌터카 대여, 렌트·리스 중개, 꽃배달 쇼핑몰)";
            case REVENUE_MODEL -> "돈은 어떻게 버시나요?\n1) 직접 팔거나 빌려줌 (판매·대여)\n2) 중개하고 수수료를 받음\n"
                    + "3) 매달 정기 결제를 받음 (구독·월 납입)\n번호나 말로 답해 주세요.";
            case OFFERINGS -> "광고로 팔고 싶은 상품이나 서비스를 알려 주세요. 여러 개면 쉼표로 나눠 주세요.\n"
                    + "(예: 단기 렌트, 장기 렌트)";
            case OFFERING_REVENUE -> {
                String name = m.getOfferings().get(slot.index()).getName();
                yield switch (model) {
                    case COMMISSION -> String.format("[%s] 계약 1건이 성사되면 받는 수수료는 얼마인가요?\n(예: 80만원)", name);
                    case SUBSCRIPTION -> String.format("[%s] 고객 1명이 한 달에 내는 금액은 얼마인가요?\n(예: 월 5만원)", name);
                    default -> String.format("[%s] 계약 1건에 고객이 내는 돈(평균 매출)은 얼마인가요?\n(예: 35만원)", name);
                };
            }
            case OFFERING_PROFIT -> {
                String name = m.getOfferings().get(slot.index()).getName();
                yield model == RevenueModel.COMMISSION
                        ? String.format("[%s] 그 수수료에서 딜러 수당·인건비 등을 빼고 실제로 남는 돈은요?\n"
                                + "금액이나 비율로 답해 주세요. (예: 60만원, 70%%) 거의 다 남으면 '전부'.", name)
                        : String.format("[%s] 그중 원가·인건비 등을 빼고 실제로 남는 돈은요?\n"
                                + "금액이나 비율로 답해 주세요. (예: 10만원, 30%%) 모르시면 '모름'.", name);
            }
            case LEAD_TO_DEAL -> "광고를 보고 문의(전화·상담 신청)가 10건 들어오면 보통 몇 건이 계약되나요?\n"
                    + "(예: 10건 중 3건, 30%) 모르시면 '모름'이라고 해 주세요. 운영하면서 실제 값으로 맞춰 갑니다.";
            case DEALS_PER_CUSTOMER -> model == RevenueModel.SUBSCRIPTION
                    ? "고객 한 분이 평균 몇 달 동안 결제를 이어가나요?\n(예: 12개월) 모르시면 '모름'."
                    : "고객 한 분이 평균 몇 번 계약하나요? 재계약·재구매까지 포함해서요.\n(처음 한 번뿐이면 1, 모르시면 '모름')";
            case GOAL -> "광고로 한 달에 무엇을 얼마나 얻고 싶으세요? 하나만 골라 숫자와 함께 알려 주세요.\n"
                    + "- 매출 (예: 매출 2천만원)\n- 이익 (예: 이익 500만원)\n- 계약 건수 (예: 계약 20건)\n- 문의 건수 (예: 문의 50건)";
            case AD_SHARE -> "계약 1건으로 남는 돈 중 몇 %까지 광고비로 써도 괜찮으세요?\n"
                    + "보통 20~40%를 씁니다. 잘 모르시면 '추천대로'라고 해 주세요. 30%로 시작합니다.";
            case BUDGET_LIMIT -> "한 달 광고비로 쓸 수 있는 최대 금액이 정해져 있나요?\n(예: 200만원) 없으면 '없음'.";
            case KEYWORDS -> suggestedKeywords.isEmpty()
                    ? "고객이 네이버에서 검색할 말을 1~5개 알려 주세요.\n(예: 제주 렌트카, 장기렌트 견적)"
                    : "고객이 검색할 핵심 키워드를 정할게요. 지금 이 계정에서 광고비를 많이 쓰는 키워드는\n「"
                            + String.join(", ", suggestedKeywords.subList(0, Math.min(MAX_KEYWORDS, suggestedKeywords.size())))
                            + "」입니다.\n이걸로 볼까요? ('네' 또는 직접 입력)";
        };
    }

    private Answer profit(BusinessModel m, int index, String answer) {
        Offering offering = m.getOfferings().get(index);
        long revenue = offering.getRevenuePerDeal();
        String compact = answer.replace(" ", "");
        if (compact.contains("전부") || compact.equals("다") || compact.contains("다남")) {
            offering.setProfitPerDeal(revenue);
            offering.setProfitSource(BusinessModel.SOURCE_USER);
            return Answer.ok();
        }
        if (KoreanNumbers.unknown(answer)) {
            double margin = m.getRevenueModel() == RevenueModel.COMMISSION ? 100.0 : ASSUMED_DIRECT_MARGIN_PCT;
            offering.setProfitPerDeal(Math.round(revenue * margin / 100.0));
            offering.setProfitSource(BusinessModel.SOURCE_ASSUMED);
            return Answer.ok(String.format("매출의 %d%%가 남는다고 가정할게요. 실제와 다르면 언제든 고쳐 주세요.", Math.round(margin)));
        }
        boolean percent = answer.contains("%") || answer.contains("프로") || answer.contains("퍼센트");
        if (percent) {
            Optional<Double> pct = KoreanNumbers.percent(answer);
            if (pct.isPresent()) {
                offering.setProfitPerDeal(Math.round(revenue * pct.get() / 100.0));
                offering.setProfitSource(BusinessModel.SOURCE_USER);
                return Answer.ok();
            }
        }
        Optional<Long> won = KoreanNumbers.money(answer);
        if (won.isPresent()) {
            if (won.get() > revenue) {
                return Answer.retry(String.format("남는 돈이 매출(%,d원)보다 클 수는 없어요. 다시 알려 주세요.", revenue));
            }
            offering.setProfitPerDeal(won.get());
            offering.setProfitSource(BusinessModel.SOURCE_USER);
            return Answer.ok();
        }
        return Answer.retry("금액이나 비율로 알려 주세요. 예: 10만원, 30%. 모르시면 '모름'.");
    }

    private Answer goal(BusinessModel m, String answer) {
        String s = answer.replace(" ", "");
        GoalType type = null;
        if (s.contains("이익") || s.contains("순익") || s.contains("순이익") || s.contains("수익")) {
            type = GoalType.PROFIT;
        } else if (s.contains("매출")) {
            type = GoalType.REVENUE;
        } else if (s.contains("문의") || s.contains("상담") || s.contains("인입") || s.contains("전화") || s.contains("예약")) {
            type = GoalType.LEADS;
        } else if (s.contains("계약") || s.contains("판매") || s.contains("건")) {
            type = GoalType.DEALS;
        }
        boolean moneyGoal = type == GoalType.REVENUE || type == GoalType.PROFIT
                || (type == null && (s.contains("원") || s.contains("만") || s.contains("억")));
        if (moneyGoal) {
            Optional<Long> won = KoreanNumbers.money(answer);
            if (won.isEmpty()) {
                return Answer.retry("금액을 함께 알려 주세요. 예: 매출 2천만원");
            }
            m.setGoalType(type == null ? GoalType.REVENUE : type);
            m.setGoalAmount(won.get());
            return Answer.ok();
        }
        Optional<Double> count = KoreanNumbers.count(answer);
        if (type == null || count.isEmpty()) {
            return Answer.retry("무엇을 얼마나인지 함께 알려 주세요. 예: 매출 2천만원, 계약 20건, 문의 50건");
        }
        m.setGoalType(type);
        m.setGoalAmount(Math.round(count.get()));
        return Answer.ok();
    }

    private Answer adShare(BusinessModel m, String answer) {
        String s = answer.replace(" ", "");
        if (s.contains("추천") || KoreanNumbers.unknown(answer) || KoreanNumbers.yes(answer)) {
            m.setAdSharePct(DEFAULT_AD_SHARE_PCT);
            m.setAdShareSource(BusinessModel.SOURCE_ASSUMED);
            return Answer.ok("이익의 30%를 광고비로 쓰는 것으로 시작합니다.");
        }
        boolean percent = s.contains("%") || s.contains("프로") || s.contains("퍼센트");
        if (!percent) {
            // "5만원까지" 처럼 금액으로 답하면 계약 1건 평균 이익에 대한 비율로 바꾼다.
            Optional<Long> won = KoreanNumbers.money(answer);
            double avgProfit = m.getOfferings().stream().mapToLong(Offering::getProfitPerDeal).average().orElse(0);
            if (won.isPresent() && avgProfit > 0) {
                double pct = Math.min(100.0, Math.round(won.get() * 1000.0 / avgProfit) / 10.0);
                m.setAdSharePct(pct);
                m.setAdShareSource(BusinessModel.SOURCE_USER);
                return Answer.ok(String.format("계약 1건 평균 이익의 %s%%입니다.", GoalEconomics.trim(pct)));
            }
        }
        Optional<Double> pct = KoreanNumbers.percent(answer);
        if (pct.isEmpty()) {
            return Answer.retry("%로 알려 주세요. 예: 30%. 잘 모르시면 '추천대로'.");
        }
        m.setAdSharePct(pct.get());
        m.setAdShareSource(BusinessModel.SOURCE_USER);
        return Answer.ok();
    }

    static Optional<RevenueModel> revenueModel(String answer) {
        String s = answer.replace(" ", "");
        if (s.startsWith("2") || s.contains("수수료") || s.contains("중개") || s.contains("에이전트") || s.contains("딜러")
                || s.contains("대리점")) {
            return Optional.of(RevenueModel.COMMISSION);
        }
        if (s.startsWith("3") || s.contains("구독") || s.contains("정기") || s.contains("월납") || s.contains("월결제")) {
            return Optional.of(RevenueModel.SUBSCRIPTION);
        }
        if (s.startsWith("1") || s.contains("판매") || s.contains("대여") || s.contains("직접") || s.contains("팔")
                || s.contains("빌려")) {
            return Optional.of(RevenueModel.DIRECT);
        }
        return Optional.empty();
    }

    static List<String> split(String answer, int max) {
        return Arrays.stream(answer.split("[,，/·\\n]|그리고|및"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .limit(max)
                .toList();
    }

    private static String limit(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
