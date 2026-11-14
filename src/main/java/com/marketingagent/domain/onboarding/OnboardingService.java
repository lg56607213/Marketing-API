package com.marketingagent.domain.onboarding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.planner.PlanService;
import com.marketingagent.domain.ads.planner.PlannerDtos.GoalType;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanKeyword;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanProposal;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanRequest;
import com.marketingagent.domain.ads.planner.PlannerDtos.Scenario;
import com.marketingagent.domain.onboarding.OnboardingDtos.Alternative;
import com.marketingagent.domain.onboarding.OnboardingDtos.Profile;
import com.marketingagent.domain.onboarding.OnboardingDtos.ProfileRequest;
import com.marketingagent.domain.onboarding.OnboardingDtos.Proposal;
import com.marketingagent.domain.onboarding.OnboardingDtos.SiteCheck;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가입한 회원이 적은 업체 정보와 광고 예산·목표를 받아, 홈페이지를 확인하고 예산 세 가지 안을 비교해 돌려준다.
 *
 * <p>세 안은 같은 키워드 조사 결과에 예산만 달리 적용한 것이다. 그래서 차이는 오로지 돈을 더 쓰거나 덜 썼을 때의 효과이고,
 * "돈을 더 쓰면 전환 1건을 더 얻는 데 얼마가 드는가"를 그대로 비교할 수 있다.
 * <ol>
 *   <li>증액: 요청 예산의 1.5배</li>
 *   <li>요청대로</li>
 *   <li>감액: 요청 예산의 0.6배</li>
 * </ol>
 * 회원이 한 안을 고르면 그것이 현재 운영 계획이 되고, 브리핑과 자동 운영이 그 목표를 기준으로 삼는다.
 */
@Slf4j
@Service
public class OnboardingService {

    static final String INCREASE = "INCREASE";
    static final String REQUESTED = "REQUESTED";
    static final String DECREASE = "DECREASE";
    private static final double INCREASE_RATIO = 1.5;
    private static final double DECREASE_RATIO = 0.6;
    private static final long BUDGET_UNIT = 10_000;
    private static final long MIN_BUDGET = 10_000;

    private final BusinessProfileRepository profileRepository;
    private final PlanService planService;
    private final SiteInspector siteInspector;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public OnboardingService(BusinessProfileRepository profileRepository, PlanService planService,
            SiteInspector siteInspector, ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.profileRepository = profileRepository;
        this.planService = planService;
        this.siteInspector = siteInspector;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 마지막으로 받은 제안. 업체 정보를 아직 등록하지 않았으면 비어 있다. */
    public Optional<Proposal> current() {
        return profileRepository.findTopByOrderByIdAsc()
                .filter(profile -> profile.getProposalJson() != null)
                .map(profile -> withSelection(fromJson(profile.getProposalJson()), profile.getSelectedOption()));
    }

    /** 업체 정보를 저장하고 제안을 새로 만든다. 제안을 만들지 못해도 입력한 정보는 남는다. */
    public Proposal submit(ProfileRequest request) {
        String homepage = SiteInspector.normalize(request.homepageUrl());
        List<String> keywords = new ArrayList<>(new LinkedHashSet<>(
                request.keywords().stream().map(String::trim).filter(k -> !k.isEmpty()).toList()));
        if (keywords.isEmpty()) {
            throw new BadRequestException("핵심 키워드를 1개 이상 입력하세요.");
        }
        Profile profile = new Profile(request.companyName().trim(), homepage, request.businessDescription().trim(),
                keywords, request.monthlyBudget(), request.targetConversions(), positive(request.saleValue()),
                positive(request.siteMonthlyVisitors()), positive(request.siteMonthlyConversions()));

        tx.executeWithoutResult(status -> {
            BusinessProfile entity = profileRepository.findTopByOrderByIdAsc().orElseGet(BusinessProfile::new);
            entity.update(profile.companyName(), profile.homepageUrl(), profile.businessDescription(),
                    String.join(",", keywords), profile.monthlyBudget(), profile.targetConversions(),
                    profile.saleValue(), profile.siteMonthlyVisitors(), profile.siteMonthlyConversions());
            profileRepository.save(entity);
        });

        SiteCheck siteCheck = siteInspector.inspect(homepage);
        Proposal proposal = propose(profile, siteCheck);

        tx.executeWithoutResult(status -> profileRepository.findTopByOrderByIdAsc()
                .ifPresent(entity -> entity.proposed(toJson(proposal))));
        log.info("업체 등록·제안: {} / 예산 {}원 / 목표 {}건 / 추천 {}", profile.companyName(),
                profile.monthlyBudget(), profile.targetConversions(), proposal.recommendedKey());
        return proposal;
    }

    /** 세 안 중 하나를 골라 현재 운영 계획으로 삼는다. */
    public Proposal select(String key) {
        return tx.execute(status -> {
            BusinessProfile entity = profileRepository.findTopByOrderByIdAsc()
                    .filter(profile -> profile.getProposalJson() != null)
                    .orElseThrow(() -> new BadRequestException("먼저 업체 정보를 등록해 제안을 받으세요."));
            Proposal proposal = fromJson(entity.getProposalJson());
            Alternative chosen = proposal.alternatives().stream()
                    .filter(a -> a.key().equalsIgnoreCase(key))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("고를 수 없는 안입니다: " + key));
            planService.save(chosen.plan(), entity.getTargetConversions());
            entity.selected(chosen.key(), entity.getProposalJson());
            return withSelection(proposal, chosen.key());
        });
    }

    // --- 제안 ---

    private Proposal propose(Profile profile, SiteCheck siteCheck) {
        long requested = profile.monthlyBudget();
        long increased = roundUp((long) (requested * INCREASE_RATIO));
        long decreased = Math.max(MIN_BUDGET, (long) (requested * DECREASE_RATIO) / BUDGET_UNIT * BUDGET_UNIT);

        PlanRequest research = new PlanRequest(profile.keywords(), GoalType.BUDGET, null, requested,
                profile.saleValue(), null, profile.siteMonthlyVisitors(), profile.siteMonthlyConversions(), null);
        List<PlanProposal> plans = planService.alternatives(research, List.of(increased, requested, decreased));

        long target = profile.targetConversions();
        Figures up = Figures.of(plans.get(0));
        Figures mid = Figures.of(plans.get(1));
        Figures down = Figures.of(plans.get(2));

        Long extraUp = marginal(up, mid);
        Long extraMid = marginal(mid, down);
        String recommendedKey = recommend(up, mid, down, target, extraUp, profile.saleValue());

        List<Alternative> alternatives = List.of(
                alternative(INCREASE, "증액", up, target, extraUp, recommendedKey, effectOfIncrease(up, mid, target, extraUp)),
                alternative(REQUESTED, "요청대로", mid, target, extraMid, recommendedKey, effectOfRequested(mid, target)),
                alternative(DECREASE, "감액", down, target, null, recommendedKey, effectOfDecrease(down, mid, target)));

        List<String> warnings = new ArrayList<>(new LinkedHashSet<>(plans.get(1).warnings()));
        return new Proposal(profile, siteCheck, alternatives, recommendedKey,
                recommendation(recommendedKey, up, mid, down, target, extraUp, profile.saleValue()), warnings, null,
                LocalDateTime.now());
    }

    /**
     * 목표를 채우는 안 중 가장 싼 것을 권한다. 어느 안도 목표를 못 채우면 요청대로 하되 목표가 무리임을 알린다.
     * 증액은 추가 전환 1건의 값이 그 1건의 매출보다 비싸면 권하지 않는다.
     */
    private String recommend(Figures up, Figures mid, Figures down, long target, Long extraUp, Long saleValue) {
        if (down.conversions() >= target) {
            return DECREASE;
        }
        if (mid.conversions() >= target) {
            return REQUESTED;
        }
        boolean increaseHelps = up.conversions() > mid.conversions();
        boolean increasePays = saleValue == null || extraUp == null || extraUp <= saleValue;
        if (up.conversions() >= target && increaseHelps && increasePays) {
            return INCREASE;
        }
        return REQUESTED;
    }

    private String recommendation(String key, Figures up, Figures mid, Figures down, long target, Long extraUp,
            Long saleValue) {
        switch (key) {
            case DECREASE:
                return String.format(
                        "감액안을 권합니다. 월 %,d원만 써도 전환 약 %,d건으로 목표 %,d건을 채울 것으로 보입니다. "
                                + "요청하신 %,d원을 다 쓰면 전환은 약 %,d건으로 늘지만, 목표를 넘는 부분입니다. 먼저 적게 시작해 실제 전환율을 확인한 뒤 늘리는 편이 안전합니다.",
                        down.budget(), down.conversions(), target, mid.budget(), mid.conversions());
            case INCREASE:
                return String.format(
                        "증액안을 권합니다. 요청하신 월 %,d원으로는 전환 약 %,d건으로 목표 %,d건에 못 미치고, 월 %,d원으로 올리면 약 %,d건으로 목표를 채울 것으로 보입니다. "
                                + "추가로 얻는 전환 1건에 약 %,d원이 듭니다%s.",
                        mid.budget(), mid.conversions(), target, up.budget(), up.conversions(),
                        extraUp == null ? 0 : extraUp,
                        saleValue != null ? String.format("(1건당 매출 %,d원보다 낮음)", saleValue) : "");
            default:
                if (mid.conversions() >= target) {
                    return String.format(
                            "요청하신 대로 진행하기를 권합니다. 월 %,d원으로 전환 약 %,d건을 기대할 수 있어 목표 %,d건을 채울 것으로 보입니다. "
                                    + "감액하면 약 %,d건으로 목표에 못 미칩니다.",
                            mid.budget(), mid.conversions(), target, down.conversions());
                }
                StringBuilder text = new StringBuilder(String.format(
                        "요청하신 대로 시작하되 목표를 다시 잡기를 권합니다. 월 %,d원으로 기대할 수 있는 전환은 약 %,d건으로 목표 %,d건의 %.0f%%입니다. ",
                        mid.budget(), mid.conversions(), target, mid.conversions() * 100.0 / target));
                if (up.conversions() <= mid.conversions()) {
                    text.append("예산을 올려도 이 키워드들로는 더 살 클릭이 없어 전환이 늘지 않습니다. 목표를 채우려면 예산이 아니라 핵심 키워드를 넓혀야 합니다.");
                } else if (up.conversions() < target) {
                    text.append(String.format("월 %,d원으로 올려도 약 %,d건이라 목표에는 닿지 않습니다. 목표를 낮추거나 핵심 키워드를 넓히세요.",
                            up.budget(), up.conversions()));
                } else {
                    text.append(String.format("월 %,d원으로 올리면 약 %,d건으로 목표에 닿지만, 추가 전환 1건에 약 %,d원이 들어 1건당 매출 %,d원보다 비쌉니다. 올릴수록 손해입니다.",
                            up.budget(), up.conversions(), extraUp == null ? 0 : extraUp, saleValue == null ? 0 : saleValue));
                }
                return text.toString();
        }
    }

    private String effectOfIncrease(Figures up, Figures mid, long target, Long extra) {
        long more = up.conversions() - mid.conversions();
        if (more <= 0) {
            return String.format("예산을 월 %,d원으로 올려도 이 키워드들로는 더 살 클릭이 없습니다. 실제로 쓰이는 돈은 약 %,d원, 전환은 약 %,d건으로 요청대로 할 때와 같습니다.",
                    up.budget(), up.cost(), up.conversions());
        }
        return String.format("월 %,d원을 더 쓰면 전환이 약 %,d건에서 %,d건으로 늘어납니다(+%,d건, 목표의 %.0f%%). 추가로 얻는 전환 1건에 약 %,d원이 들어, "
                        + "평균(%,d원)보다 비쌉니다. 싼 클릭을 먼저 사고 나면 남는 클릭이 비싸지기 때문입니다.",
                up.cost() - mid.cost(), mid.conversions(), up.conversions(), more, goalRate(up, target),
                extra == null ? 0 : extra, up.cpa());
    }

    private String effectOfRequested(Figures mid, long target) {
        String spend = mid.cost() < mid.budget() * 0.9
                ? String.format("월 %,d원 중 약 %,d원이 쓰일 것으로 보이고", mid.budget(), mid.cost())
                : String.format("월 %,d원으로", mid.budget());
        return String.format("%s 클릭 약 %,d회, 전환 약 %,d건(전환율에 따라 %,d~%,d건)을 기대합니다. 목표 %,d건의 %.0f%%이고 전환 1건당 약 %,d원입니다.",
                spend, mid.clicks(), mid.conversions(), mid.low(), mid.high(), target, goalRate(mid, target), mid.cpa());
    }

    private String effectOfDecrease(Figures down, Figures mid, long target) {
        long fewer = mid.conversions() - down.conversions();
        if (fewer <= 0) {
            return String.format("월 %,d원으로 줄여도 전환은 약 %,d건으로 같습니다. 요청하신 예산에는 쓰이지 않는 여유분이 있었다는 뜻입니다.",
                    down.budget(), down.conversions());
        }
        return String.format("월 %,d원으로 줄이면 전환이 약 %,d건에서 %,d건으로 줄어듭니다(-%,d건, 목표의 %.0f%%). 대신 가장 싼 클릭만 남아 "
                        + "전환 1건당 비용은 약 %,d원에서 %,d원으로 내려갑니다.",
                down.budget(), mid.conversions(), down.conversions(), fewer, goalRate(down, target), mid.cpa(), down.cpa());
    }

    private Alternative alternative(String key, String label, Figures f, long target, Long extra,
            String recommendedKey, String effect) {
        PlanProposal plan = f.plan();
        Scenario base = plan.scenarios().get(1);
        return new Alternative(key, label, f.budget(), plan.dailyBudget(), f.cost(), f.clicks(), f.conversions(),
                f.low(), f.high(), Math.round(goalRate(f, target) * 10) / 10.0, f.cpa(), base.revenue(),
                base.roasPct(), extra, (int) plan.keywords().stream().filter(PlanKeyword::selected).count(),
                key.equals(recommendedKey), effect, plan);
    }

    /** 돈을 더 써서 얻는 전환 1건의 값. 전환이 늘지 않으면 null. */
    private Long marginal(Figures higher, Figures lower) {
        long more = higher.conversions() - lower.conversions();
        return more <= 0 ? null : Math.max(0, (higher.cost() - lower.cost()) / more);
    }

    private double goalRate(Figures f, long target) {
        return target <= 0 ? 0.0 : f.conversions() * 100.0 / target;
    }

    private static long roundUp(long value) {
        return (value + BUDGET_UNIT - 1) / BUDGET_UNIT * BUDGET_UNIT;
    }

    private static Long positive(Long value) {
        return value != null && value > 0 ? value : null;
    }

    private Proposal withSelection(Proposal p, String selected) {
        return new Proposal(p.profile(), p.siteCheck(), p.alternatives(), p.recommendedKey(), p.recommendation(),
                p.warnings(), selected, p.createdAt());
    }

    private String toJson(Proposal proposal) {
        try {
            return objectMapper.writeValueAsString(proposal);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("제안을 저장하지 못했습니다", e);
        }
    }

    private Proposal fromJson(String json) {
        try {
            return objectMapper.readValue(json, Proposal.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 제안을 읽지 못했습니다", e);
        }
    }

    /** 계획 한 건에서 비교에 쓰는 숫자만 뽑은 것. */
    private record Figures(PlanProposal plan, long budget, long cost, long clicks, long conversions, long low,
            long high, long cpa) {

        static Figures of(PlanProposal plan) {
            long cost = plan.keywords().stream().filter(PlanKeyword::selected)
                    .mapToLong(PlanKeyword::expectedCost).sum();
            return new Figures(plan, plan.monthlyBudget(), cost, plan.expectedClicks(), plan.expectedConversions(),
                    plan.scenarios().get(0).conversions(), plan.scenarios().get(2).conversions(), plan.targetCpa());
        }
    }
}
