package com.marketingagent.domain.ads;

import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.dto.KeywordAggregate;
import com.marketingagent.domain.ads.dto.KeywordConversionAggregate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 광고비 대비 효율로 키워드별 적정 입찰가를 계산한다.
 *
 * <p>생각은 단순하다. 클릭 한 번이 벌어다 주는 돈보다 클릭 한 번에 더 쓰면 손해다.
 * <pre>
 *   클릭당 쓸 수 있는 돈 = 키워드 전환율 × 전환 1건에 쓸 수 있는 돈
 *   전환 1건에 쓸 수 있는 돈 = 목표 전환당 비용, 또는 (전환 1건 매출 ÷ 목표 광고수익률)
 *   적정 입찰가 = 현재 입찰가 × (클릭당 쓸 수 있는 돈 ÷ 실제 클릭 단가)
 * </pre>
 * 키워드 전환율은 클릭이 적을 때 우연에 흔들리므로 계정 평균 쪽으로 당겨 잡는다({@link EfficiencyMath#shrunkRate}).
 * 한 번에 바꾸는 폭과 입찰가 상·하한은 {@link BidPolicyProperties} 를 따른다.
 * 이 서비스는 계산과 추천 저장만 하고 네이버에는 쓰지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EfficiencyBidService {

    /** 효율을 재는 기간. 전환은 드물게 일어나므로 입찰 추천의 기본 구간보다 길게 본다. */
    public static final int WINDOW_DAYS = 28;
    /** 계정 전체 클릭이 이보다 적으면 계산하지 않는다. */
    public static final long MIN_ACCOUNT_CLICKS = 100;
    /** 키워드 클릭이 이보다 적으면 그 키워드는 건드리지 않는다. */
    public static final long MIN_KEYWORD_CLICKS = 20;
    /** 계정 평균 전환율을 섞는 가상 클릭수. */
    static final double PRIOR_CLICKS = 50;
    /** 이 비율보다 작은 변경은 하지 않는다. 매일 조금씩 흔드는 것을 막는다. */
    private static final double DEAD_BAND = 0.05;
    /** 평균 순위가 이보다 좋으면 올려도 클릭당 비용만 오른다. */
    private static final double TOP_RANK = 2.0;
    /** 키워드 자신의 객단가를 쓰려면 필요한 전환수. */
    private static final long MIN_CONVERSIONS_FOR_OWN_VALUE = 3;
    /** 전환 없이 전환 1건에 쓸 수 있는 돈의 이 배수 이상 쓴 키워드는 낭비로 본다. */
    private static final double WASTE_MULTIPLE = 3.0;
    private static final long MIN_WASTE_CLICKS = 30;
    private static final long BID_UNIT = 10;
    /**
     * 입찰가를 바꾼 뒤 다시 바꾸기까지 기다리는 날수. 효율은 28일 평균으로 재는데 입찰가를 바꾼 효과는 며칠에 걸쳐
     * 드러나므로, 기다리지 않으면 같은 근거로 매일 20%씩 연달아 내리거나 올리게 된다.
     */
    public static final int COOLDOWN_DAYS = 7;

    private final KeywordStatDailyRepository statRepository;
    private final KeywordConversionHourlyRepository conversionRepository;
    private final AdKeywordRepository keywordRepository;
    private final AdGroupRepository adGroupRepository;
    private final BidRecommendationRepository recommendationRepository;
    private final BidPolicyProperties policy;

    /**
     * 효율 목표.
     *
     * @param targetCpa     목표 전환당 비용(원). 0 이면 쓰지 않는다
     * @param targetRoasPct 목표 광고수익률(%). 0 이면 쓰지 않는다. 전환매출이 잡히는 계정에서만 의미가 있다
     * @param source        목표를 어디서 가져왔는지. 추천 근거에 적는다
     */
    public record Targets(long targetCpa, double targetRoasPct, String source) {}

    /**
     * 키워드 한 개의 효율 계산 결과.
     *
     * @param cvrPct      계정 평균을 섞은 전환율(%)
     * @param maxCpc      클릭당 쓸 수 있는 돈
     * @param actualCpc   실제 클릭 단가
     * @param suggestedBid 권하는 입찰가. 바꿀 필요가 없으면 currentBid 와 같다
     * @param verdict     올리기 / 내리기 / 유지 / 지켜보는 중 / 중지 검토 / 데이터 부족
     */
    public record KeywordEfficiency(
            String nccKeywordId,
            String keyword,
            long impressions,
            long clicks,
            long cost,
            long conversions,
            long revenue,
            long cpa,
            double roasPct,
            double cvrPct,
            long actualCpc,
            long maxCpc,
            long currentBid,
            long suggestedBid,
            String verdict,
            String reason
    ) {}

    /**
     * @param mode 계산 기준: ROAS 또는 CPA
     * @param accountCvrPct 계정 평균 전환율(%)
     */
    public record Analysis(LocalDate since, LocalDate until, String mode, String targetLabel, double accountCvrPct,
            List<KeywordEfficiency> keywords) {}

    /** 최근 실적으로 키워드별 효율과 적정 입찰가를 계산한다. 아무것도 저장하지 않는다. */
    @Transactional(readOnly = true, noRollbackFor = BadRequestException.class)
    public Analysis analyze(Targets targets) {
        return analyze(targets, LocalDate.now().minusDays(1));
    }

    @Transactional(readOnly = true, noRollbackFor = BadRequestException.class)
    public Analysis analyze(Targets targets, LocalDate until) {
        LocalDate since = until.minusDays(WINDOW_DAYS - 1L);
        List<KeywordAggregate> rows = statRepository.aggregateByKeyword(since, until);

        long accountClicks = rows.stream().mapToLong(r -> nz(r.clkCnt())).sum();
        long accountConversions = rows.stream().mapToLong(r -> nz(r.ccnt())).sum();
        if (accountClicks < MIN_ACCOUNT_CLICKS) {
            throw new BadRequestException(String.format(
                    "최근 %d일 클릭이 %,d회뿐이라 효율을 계산하기 이릅니다. %,d회 이상 쌓이면 계산합니다.",
                    WINDOW_DAYS, accountClicks, MIN_ACCOUNT_CLICKS));
        }
        if (accountConversions == 0) {
            throw new BadRequestException(String.format(
                    "최근 %d일 동안 전환이 한 건도 잡히지 않아 어떤 키워드가 돈을 버는지 알 수 없습니다. 사이트의 전환 추적부터 확인하세요.",
                    WINDOW_DAYS));
        }

        Map<String, Long> revenues = new HashMap<>();
        for (KeywordConversionAggregate row : conversionRepository.aggregateByKeyword(since, until)) {
            revenues.merge(row.nccKeywordId(), nz(row.convAmt()), Long::sum);
        }
        long accountRevenue = revenues.values().stream().mapToLong(Long::longValue).sum();
        double accountRate = accountConversions / (double) accountClicks;
        double accountValue = accountRevenue / (double) accountConversions;

        boolean byRoas = targets.targetRoasPct() > 0 && accountRevenue > 0;
        if (!byRoas && targets.targetCpa() <= 0) {
            throw new BadRequestException(targets.targetRoasPct() > 0
                    ? "목표 광고수익률이 있지만 전환매출이 잡히지 않습니다. 구매 금액이 전환으로 넘어오는지 확인하거나 목표 전환당 비용을 정하세요."
                    : "효율의 기준이 될 목표가 없습니다. 운영 계획을 받거나 자동 운영 설정에서 목표 전환당 비용을 정하세요.");
        }
        String targetLabel = byRoas
                ? String.format("목표 광고수익률 %.0f%% (%s)", targets.targetRoasPct(), targets.source())
                : String.format("목표 전환당 비용 %,d원 (%s)", targets.targetCpa(), targets.source());

        Map<String, AdKeyword> keywords = keywordRepository.findAll().stream()
                .collect(Collectors.toMap(AdKeyword::getNccKeywordId, Function.identity(), (a, b) -> a));

        Set<String> recentlyChanged = recommendationRepository
                .findByStatusAndDecidedAtAfter(BidStatus.APPLIED, LocalDate.now().minusDays(COOLDOWN_DAYS).atStartOfDay())
                .stream()
                .map(BidRecommendation::getNccKeywordId)
                .collect(Collectors.toSet());

        List<KeywordEfficiency> result = new ArrayList<>();
        for (KeywordAggregate row : rows) {
            AdKeyword keyword = keywords.get(row.nccKeywordId());
            if (keyword == null) {
                continue;
            }
            long impressions = nz(row.impCnt());
            long clicks = nz(row.clkCnt());
            long cost = nz(row.salesAmt());
            long conversions = nz(row.ccnt());
            long revenue = revenues.getOrDefault(row.nccKeywordId(), 0L);
            long currentBid = currentBid(keyword);
            long cpa = conversions == 0 ? 0 : cost / conversions;
            double roas = cost == 0 ? 0.0 : round2(revenue * 100.0 / cost);

            double rate = EfficiencyMath.shrunkRate(conversions, clicks, accountRate, PRIOR_CLICKS);
            double valuePerConversion;
            if (byRoas) {
                double own = conversions >= MIN_CONVERSIONS_FOR_OWN_VALUE && revenue > 0
                        ? revenue / (double) conversions : accountValue;
                valuePerConversion = EfficiencyMath.allowableCpa(own, targets.targetRoasPct());
            } else {
                valuePerConversion = targets.targetCpa();
            }
            long maxCpc = EfficiencyMath.maxCpc(rate, valuePerConversion);
            long actualCpc = clicks == 0 ? 0 : cost / clicks;

            String math = String.format("전환율 %.2f%%(클릭 %,d·전환 %,d, 계정 평균 %.2f%% 반영) × 전환 1건에 쓸 수 있는 돈 %,d원 = 클릭당 %,d원까지",
                    rate * 100, clicks, conversions, accountRate * 100, Math.round(valuePerConversion), maxCpc);

            long suggested = currentBid;
            String verdict;
            String reason;
            if ("PAUSED".equals(keyword.getStatus())) {
                verdict = "중지됨";
                reason = String.format("꺼 둔 키워드입니다. 꺼지기 전 %d일 실적: 클릭 %,d회, 광고비 %,d원, 전환 %,d건.",
                        WINDOW_DAYS, clicks, cost, conversions);
            } else if (clicks < MIN_KEYWORD_CLICKS) {
                verdict = "데이터 부족";
                reason = String.format("클릭이 %,d회라 아직 판단하지 않습니다. %,d회부터 계산합니다.", clicks, MIN_KEYWORD_CLICKS);
            } else if (recentlyChanged.contains(row.nccKeywordId())) {
                verdict = "지켜보는 중";
                reason = String.format("최근 %d일 안에 입찰가를 바꿨습니다. 바꾼 효과가 실적에 반영될 때까지 다시 바꾸지 않습니다. (%s, 실제 %,d원)",
                        COOLDOWN_DAYS, math, actualCpc);
            } else if (conversions == 0 && clicks >= MIN_WASTE_CLICKS && cost >= valuePerConversion * WASTE_MULTIPLE) {
                suggested = clamp(Math.round(currentBid * (maxCpc / (double) Math.max(actualCpc, 1))), currentBid);
                verdict = "중지 검토";
                reason = String.format("전환 없이 %,d원을 썼습니다. 전환 1건에 쓸 수 있는 돈의 %.1f배입니다.",
                        cost, cost / valuePerConversion);
            } else {
                double ratio = maxCpc / (double) Math.max(actualCpc, 1);
                double avgRank = impressions == 0 || row.rnkWeighted() == null ? 0.0 : row.rnkWeighted() / impressions;
                long proposed = clamp(Math.round(currentBid * ratio), currentBid);
                boolean small = Math.abs(proposed - currentBid) < currentBid * DEAD_BAND;
                if (small) {
                    verdict = "유지";
                    reason = String.format("%s 쓸 수 있고 실제로는 %,d원을 씁니다. 차이가 작아 그대로 둡니다.", math, actualCpc);
                } else if (proposed > currentBid && avgRank > 0 && avgRank <= TOP_RANK) {
                    verdict = "유지";
                    reason = String.format("%s 쓸 수 있어 여유가 있지만, 이미 평균 %.1f위라 올려도 클릭 단가만 오릅니다.", math, avgRank);
                } else {
                    suggested = proposed;
                    verdict = proposed > currentBid ? "올리기" : "내리기";
                    reason = String.format("%s 쓸 수 있는데 실제 클릭 단가는 %,d원입니다. 입찰가 %,d원 → %,d원.",
                            math, actualCpc, currentBid, proposed);
                }
            }
            result.add(new KeywordEfficiency(row.nccKeywordId(), keyword.getKeyword(), impressions, clicks, cost,
                    conversions, revenue, cpa, roas, round2(rate * 100), actualCpc, maxCpc, currentBid, suggested,
                    verdict, reason));
        }
        result.sort(Comparator.comparingLong(KeywordEfficiency::cost).reversed());
        return new Analysis(since, until, byRoas ? "ROAS" : "CPA", targetLabel, round2(accountRate * 100), result);
    }

    /**
     * 효율 계산 결과를 입찰가 추천으로 저장한다. 기존 대기 건은 밀어낸다.
     * 반영은 {@link BidRecommendationService#approve} 를 거쳐야 하고, 거기서 가드레일이 한 번 더 적용된다.
     */
    @Transactional
    public List<BidRecommendation> generate(Targets targets) {
        Analysis analysis = analyze(targets);

        recommendationRepository.findByStatusOrderByIdDesc(BidStatus.PENDING)
                .forEach(BidRecommendation::markSuperseded);

        List<BidRecommendation> created = new ArrayList<>();
        for (KeywordEfficiency k : analysis.keywords()) {
            if (k.suggestedBid() == k.currentBid()) {
                continue;
            }
            double ctr = k.impressions() == 0 ? 0.0 : round2(k.clicks() * 100.0 / k.impressions());
            String reason = "[" + analysis.targetLabel() + "] " + k.reason();
            created.add(new BidRecommendation(k.nccKeywordId(), k.keyword(), k.currentBid(), k.suggestedBid(),
                    reason.length() > 500 ? reason.substring(0, 500) : reason, analysis.since(), analysis.until(),
                    k.impressions(), k.clicks(), k.cost(), k.conversions(), ctr, 0.0, BidStrategy.EFFICIENCY));
        }
        recommendationRepository.saveAll(created);
        log.info("효율 기반 입찰가 추천 {}건 생성 ({})", created.size(), analysis.targetLabel());
        return created;
    }

    /** 현재 입찰가. 키워드가 그룹 입찰가를 따르면 그룹 값을 쓴다. */
    private long currentBid(AdKeyword keyword) {
        if (Boolean.TRUE.equals(keyword.getUseGroupBidAmt()) || keyword.getBidAmt() == null) {
            return adGroupRepository.findByNccAdgroupId(keyword.getNccAdgroupId())
                    .map(group -> group.getBidAmt() != null ? group.getBidAmt() : policy.minBid())
                    .orElse(policy.minBid());
        }
        return keyword.getBidAmt();
    }

    /** 1회 변동폭과 상·하한을 적용하고 네이버 입찰 단위(10원)로 맞춘다. */
    private long clamp(long proposed, long currentBid) {
        // 변동폭 한도를 10원 단위 안쪽으로 맞춰 둔다. 그래야 반영 단계의 가드레일이 값을 다시 깎아 단위가 깨지는 일이 없다.
        long maxUp = (long) Math.floor(currentBid * (1 + policy.maxChangeRate()) / BID_UNIT) * BID_UNIT;
        long maxDown = (long) Math.ceil(currentBid * (1 - policy.maxChangeRate()) / BID_UNIT) * BID_UNIT;
        long rounded = Math.round(proposed / (double) BID_UNIT) * BID_UNIT;
        long bounded = maxDown > maxUp ? currentBid : Math.min(Math.max(rounded, maxDown), maxUp);
        return Math.min(Math.max(bounded, policy.minBid()), policy.maxBid());
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static long nz(Long value) {
        return value != null ? value : 0L;
    }
}
