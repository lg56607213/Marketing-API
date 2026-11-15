package com.marketingagent.domain.ads;

import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.common.exception.ResourceNotFoundException;
import com.marketingagent.domain.ads.account.AdAccount;
import com.marketingagent.domain.ads.account.AdAccountService;
import com.marketingagent.domain.ads.dto.BidApplyResult;
import com.marketingagent.domain.ads.dto.KeywordAggregate;
import com.marketingagent.integration.naver.SearchAdClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입찰가 조정 추천을 만들고, 사용자가 승인한 건만 네이버에 반영한다.
 *
 * <p>설계 원칙: 자동 반영은 없다. 추천 생성과 반영은 완전히 분리되어 있으며,
 * 반영은 반드시 {@link #approve} 를 통해서만 일어난다.
 *
 * <p>평가 기준은 캠페인마다 따로 잡는다. 한 계정에 전환 단가가 다른 사이트가 섞여 있어도
 * 캠페인만 나눠 두면 각자의 목표 CPA 와 평균 CTR 로 판단한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidRecommendationService {

    private final AdAccountService accountService;
    private final BidRecommendationRepository recommendationRepository;
    private final KeywordStatDailyRepository statRepository;
    private final AdKeywordRepository keywordRepository;
    private final AdGroupRepository adGroupRepository;
    private final AdCampaignRepository campaignRepository;
    private final BidPolicyProperties policy;

    /**
     * 최근 성과를 분석해 추천을 생성한다. 같은 범위의 기존 대기 건은 SUPERSEDED 처리해 중복 승인을 막는다.
     * 이 메서드는 네이버에 아무것도 쓰지 않는다.
     *
     * @param campaignId null 이면 계정의 모든 캠페인을 각자의 기준으로 평가한다
     */
    @Transactional
    public List<BidRecommendation> generate(Long accountId, String campaignId) {
        AdAccount account = accountService.require(accountId);
        String campaign = accountService.normalizeCampaign(account, campaignId);
        AdScope scope = scope(account.getId());

        LocalDate until = LocalDate.now().minusDays(1);
        LocalDate since = until.minusDays(Math.max(policy.lookbackDays(), 1) - 1L);

        List<KeywordAggregate> aggregates = statRepository.aggregateByKeyword(account.getId(), since, until)
                .stream()
                .filter(row -> scope.inCampaign(row.nccKeywordId(), campaign))
                .toList();
        if (aggregates.isEmpty()) {
            throw new BadRequestException("분석할 성과 데이터가 없습니다. 먼저 검색광고 동기화를 실행하세요.");
        }

        supersedePending(account.getId(), campaign);

        // 캠페인을 못 찾은 키워드는 빈 문자열 키로 묶어 계정 기본값으로 평가한다.
        Map<String, List<KeywordAggregate>> byCampaign = aggregates.stream()
                .collect(Collectors.groupingBy(row -> Objects.toString(scope.campaignIdOf(row.nccKeywordId()), "")));

        List<BidRecommendation> created = new ArrayList<>();
        for (Map.Entry<String, List<KeywordAggregate>> entry : byCampaign.entrySet()) {
            String nccCampaignId = entry.getKey().isEmpty() ? null : entry.getKey();
            Baseline baseline = baseline(entry.getValue(), scope.campaign(nccCampaignId), account);

            for (KeywordAggregate row : entry.getValue()) {
                AdKeyword keyword = scope.keyword(row.nccKeywordId());
                if (keyword == null) {
                    continue;
                }
                evaluate(account.getId(), nccCampaignId, row, keyword, scope, baseline, since, until)
                        .ifPresent(created::add);
            }
            log.info("입찰가 추천 기준: 계정 {} 캠페인 {} -> 목표 CPA {}원 ({}), 평균 CTR {}%",
                    account.getId(), nccCampaignId, baseline.targetCpa(), baseline.targetSource(),
                    round2(baseline.avgCtr()));
        }

        created.sort(Comparator.comparingLong((BidRecommendation r) -> Math.abs(r.changeAmount())).reversed());
        recommendationRepository.saveAll(created);
        log.info("입찰가 추천 {}건 생성 (계정 {}, {} ~ {})", created.size(), account.getId(), since, until);
        return created;
    }

    /**
     * 목표 평균노출순위에 맞춘 추천을 만든다.
     * 네이버의 추정 입찰가를 그대로 목표로 삼으므로 1회 변동폭 제한(maxChangeRate)은 적용하지 않는다.
     * 추정치 자체가 목표값이라 20%씩 나눠 접근하면 며칠이 걸리고 그 사이 추정치가 다시 바뀐다.
     * 입찰가 상·하한은 그대로 적용된다.
     *
     * @param position 목표 평균노출순위
     * @param device   PC 또는 MOBILE. 노출·비용 비중이 큰 기기를 기준으로 삼는다.
     */
    @Transactional
    public List<BidRecommendation> generateForTargetRank(Long accountId, String campaignId,
            int position, String device) {
        AdAccount account = accountService.require(accountId);
        String campaign = accountService.normalizeCampaign(account, campaignId);
        AdScope scope = scope(account.getId());

        List<AdKeyword> keywords = new ArrayList<>();
        for (AdKeyword keyword : scope.keywords()) {
            if (scope.inCampaign(keyword.getNccKeywordId(), campaign)) {
                keywords.add(keyword);
            }
        }
        if (keywords.isEmpty()) {
            throw new BadRequestException("키워드가 없습니다. 먼저 검색광고 동기화를 실행하세요.");
        }

        Map<String, Long> estimates = accountService.client(account).estimateBidForPosition(
                keywords.stream().map(AdKeyword::getKeyword).distinct().toList(), position, device);

        supersedePending(account.getId(), campaign);

        LocalDate until = LocalDate.now().minusDays(1);
        LocalDate since = until.minusDays(Math.max(policy.lookbackDays(), 1) - 1L);
        Map<String, KeywordAggregate> stats = statRepository.aggregateByKeyword(account.getId(), since, until)
                .stream()
                .collect(Collectors.toMap(KeywordAggregate::nccKeywordId, Function.identity(), (a, b) -> a));

        List<BidRecommendation> created = new ArrayList<>();
        for (AdKeyword keyword : keywords) {
            Long estimate = estimates.get(keyword.getKeyword());
            if (estimate == null) {
                log.warn("추정 입찰가를 받지 못했습니다: {}", keyword.getKeyword());
                continue;
            }

            long currentBid = currentBid(keyword, scope);
            long targetBid = bound(estimate);
            if (targetBid == currentBid) {
                continue;
            }

            KeywordAggregate row = stats.get(keyword.getNccKeywordId());
            long impCnt = row != null ? nz(row.impCnt()) : 0;
            long clkCnt = row != null ? nz(row.clkCnt()) : 0;
            long salesAmt = row != null ? nz(row.salesAmt()) : 0;
            long ccnt = row != null ? nz(row.ccnt()) : 0;
            double ctr = impCnt == 0 ? 0.0 : clkCnt * 100.0 / impCnt;
            double avgRnk = row != null ? weightedRank(row.rnkWeighted(), impCnt) : 0.0;

            String reason = String.format(
                    "%s 기준 평균노출순위 %d위 목표. 네이버 추정 입찰가 %,d원. (최근 %d일: 노출 %,d · 클릭 %,d · 광고비 %,d원 · 실제 평균순위 %.2f)",
                    device, position, estimate, policy.lookbackDays(), impCnt, clkCnt, salesAmt, avgRnk);

            created.add(new BidRecommendation(account.getId(), scope.campaignIdOf(keyword.getNccKeywordId()),
                    keyword.getNccKeywordId(), keyword.getKeyword(),
                    currentBid, targetBid, reason, since, until,
                    impCnt, clkCnt, salesAmt, ccnt, round2(ctr), avgRnk, BidStrategy.TARGET_RANK));
        }

        created.sort(Comparator.comparingLong((BidRecommendation r) -> Math.abs(r.changeAmount())).reversed());
        recommendationRepository.saveAll(created);
        log.info("목표순위 {}위({}) 기준 추천 {}건 생성 (계정 {})", position, device, created.size(), account.getId());
        return created;
    }

    /**
     * 사용자가 승인한 추천을 네이버에 반영한다. 여기서만 쓰기가 일어난다.
     *
     * @param actorId 승인한 사용자
     */
    @Transactional
    public BidApplyResult approve(Long id, Long actorId) {
        BidRecommendation recommendation = requireOwned(id);
        AdAccount account = accountService.require(recommendation.getAdAccountId());

        if (!recommendation.isPending()) {
            throw new BadRequestException("이미 처리된 추천입니다: " + recommendation.getStatus());
        }

        long appliedToday = recommendationRepository.countByAdAccountIdAndStatusAndDecidedAtAfter(
                account.getId(), BidStatus.APPLIED, LocalDate.now().atStartOfDay());
        if (appliedToday >= policy.maxDailyApplies()) {
            throw new BadRequestException(
                    "이 계정의 오늘 반영 한도(" + policy.maxDailyApplies() + "건)를 초과했습니다. 내일 다시 시도하세요.");
        }

        // 목표순위 추천은 추정치 자체가 목표라 변동폭 제한을 적용하지 않는다.
        long targetBid = recommendation.getStrategy() == BidStrategy.TARGET_RANK
                ? bound(recommendation.getRecommendedBid())
                : clamp(recommendation.getRecommendedBid(), recommendation.getCurrentBid());
        if (targetBid == recommendation.getCurrentBid()) {
            recommendation.markFailed(actorId, "가드레일 적용 후 변경할 금액이 없습니다.");
            return BidApplyResult.of(recommendation);
        }

        if (policy.dryRun()) {
            recommendation.markApplied(targetBid, actorId,
                    "DRY-RUN: 실제 반영 없이 기록만 남겼습니다. ads.bid.dry-run=false 로 두면 실제 반영됩니다.");
            log.warn("DRY-RUN 입찰가 승인: {} {}원 -> {}원", recommendation.getKeyword(),
                    recommendation.getCurrentBid(), targetBid);
            return BidApplyResult.of(recommendation);
        }

        AdKeyword keyword = keywordRepository
                .findByAdAccountIdAndNccKeywordId(account.getId(), recommendation.getNccKeywordId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "키워드를 찾을 수 없습니다: " + recommendation.getNccKeywordId()));

        try {
            SearchAdClient client = accountService.client(account);
            long applied = client.updateKeywordBid(
                    keyword.getNccKeywordId(), keyword.getNccAdgroupId(), keyword.getKeyword(), targetBid);
            keyword.update(keyword.getKeyword(), keyword.getStatus(), applied, false);
            recommendation.markApplied(applied, actorId, "네이버 반영 완료");
            log.info("입찰가 반영: 계정 {} {} {}원 -> {}원 (승인자 {})", account.getId(), keyword.getKeyword(),
                    recommendation.getCurrentBid(), applied, actorId);
        } catch (Exception e) {
            recommendation.markFailed(actorId, e.getMessage());
            log.error("입찰가 반영 실패: {}", recommendation.getKeyword(), e);
        }
        return BidApplyResult.of(recommendation);
    }

    @Transactional
    public BidApplyResult reject(Long id, Long actorId) {
        BidRecommendation recommendation = requireOwned(id);
        if (!recommendation.isPending()) {
            throw new BadRequestException("이미 처리된 추천입니다: " + recommendation.getStatus());
        }
        recommendation.markRejected(actorId);
        return BidApplyResult.of(recommendation);
    }

    /** 여러 건을 한 번에 승인한다. 개별 실패가 나머지를 막지 않는다. */
    @Transactional
    public List<BidApplyResult> approveAll(List<Long> ids, Long actorId) {
        List<BidApplyResult> results = new ArrayList<>();
        for (Long id : ids) {
            try {
                results.add(approve(id, actorId));
            } catch (Exception e) {
                results.add(BidApplyResult.failed(id, e.getMessage()));
            }
        }
        return results;
    }

    @Transactional(readOnly = true)
    public List<BidRecommendation> findPending(Long accountId, String campaignId) {
        AdAccount account = accountService.require(accountId);
        String campaign = accountService.normalizeCampaign(account, campaignId);
        return recommendationRepository.findByAdAccountIdAndStatusOrderByIdDesc(account.getId(), BidStatus.PENDING)
                .stream().filter(r -> matches(r, campaign)).toList();
    }

    @Transactional(readOnly = true)
    public List<BidRecommendation> findHistory(Long accountId, String campaignId) {
        AdAccount account = accountService.require(accountId);
        String campaign = accountService.normalizeCampaign(account, campaignId);
        return recommendationRepository.findByAdAccountIdAndStatusInOrderByIdDesc(account.getId(),
                        List.of(BidStatus.APPLIED, BidStatus.REJECTED, BidStatus.FAILED))
                .stream().filter(r -> matches(r, campaign)).toList();
    }

    // --- 범위와 소유 확인 ---

    /** 추천이 로그인한 사용자의 업체 계정에 속하는지 확인한다. 아니면 없는 것으로 취급한다. */
    private BidRecommendation requireOwned(Long id) {
        BidRecommendation recommendation = recommendationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("입찰가 추천을 찾을 수 없습니다: " + id));
        if (recommendation.getAdAccountId() == null) {
            throw new ResourceNotFoundException("입찰가 추천을 찾을 수 없습니다: " + id);
        }
        try {
            accountService.require(recommendation.getAdAccountId());
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("입찰가 추천을 찾을 수 없습니다: " + id);
        }
        return recommendation;
    }

    private void supersedePending(Long accountId, String campaign) {
        recommendationRepository.findByAdAccountIdAndStatusOrderByIdDesc(accountId, BidStatus.PENDING).stream()
                .filter(r -> matches(r, campaign))
                .forEach(BidRecommendation::markSuperseded);
    }

    private boolean matches(BidRecommendation recommendation, String campaign) {
        return campaign == null || campaign.equals(recommendation.getNccCampaignId());
    }

    private AdScope scope(Long accountId) {
        return AdScope.load(accountId, campaignRepository, adGroupRepository, keywordRepository);
    }

    // --- 추천 로직 ---

    private Optional<BidRecommendation> evaluate(Long accountId, String nccCampaignId, KeywordAggregate row,
            AdKeyword keyword, AdScope scope, Baseline baseline, LocalDate since, LocalDate until) {

        long impCnt = nz(row.impCnt());
        long clkCnt = nz(row.clkCnt());
        long salesAmt = nz(row.salesAmt());
        long ccnt = nz(row.ccnt());
        double avgRnk = weightedRank(row.rnkWeighted(), impCnt);
        double ctr = impCnt == 0 ? 0.0 : clkCnt * 100.0 / impCnt;

        if (impCnt < policy.minImpressions()) {
            return Optional.empty();
        }

        long currentBid = currentBid(keyword, scope);
        Decision decision = baseline.useCpa()
                ? decideByCpa(salesAmt, ccnt, avgRnk, baseline.targetCpa())
                : decideByCtr(ctr, salesAmt, avgRnk, baseline.avgCtr());

        if (decision == null) {
            return Optional.empty();
        }

        long proposed = Math.round(currentBid * (1 + decision.rate()));
        long finalBid = clamp(proposed, currentBid);
        if (finalBid == currentBid) {
            return Optional.empty();
        }

        String reason = decision.reason() + String.format(
                " (노출 %,d · 클릭 %,d · CTR %.2f%% · 광고비 %,d원 · 전환 %d · 평균순위 %.1f)",
                impCnt, clkCnt, ctr, salesAmt, ccnt, avgRnk);

        return Optional.of(new BidRecommendation(accountId, nccCampaignId,
                keyword.getNccKeywordId(), keyword.getKeyword(), currentBid, finalBid,
                reason, since, until, impCnt, clkCnt, salesAmt, ccnt, round2(ctr), round2(avgRnk),
                BidStrategy.PERFORMANCE));
    }

    /** 전환 데이터가 있을 때: 목표 CPA 대비 효율로 판단한다. */
    private Decision decideByCpa(long salesAmt, long ccnt, double avgRnk, long targetCpa) {
        if (ccnt == 0) {
            if (salesAmt >= targetCpa * 2) {
                return new Decision(-0.30, "전환 없이 목표 CPA의 2배 이상을 소진했습니다. 입찰가를 낮춰 손실을 줄이세요.");
            }
            if (salesAmt >= targetCpa) {
                return new Decision(-0.15, "전환 없이 목표 CPA 이상을 소진했습니다. 입찰가 하향을 권합니다.");
            }
            return null;
        }

        long cpa = salesAmt / ccnt;
        if (cpa <= targetCpa * 0.7) {
            if (avgRnk <= 2.0) {
                return null; // 이미 최상단이라 더 올릴 실익이 없다
            }
            return new Decision(0.20, String.format("CPA %,d원으로 목표(%,d원)보다 크게 효율적입니다. 노출을 늘리세요.", cpa, targetCpa));
        }
        if (cpa <= targetCpa) {
            if (avgRnk <= 2.0) {
                return null;
            }
            return new Decision(0.10, String.format("CPA %,d원으로 목표(%,d원) 이내입니다. 소폭 상향 여지가 있습니다.", cpa, targetCpa));
        }
        if (cpa > targetCpa * 1.5) {
            return new Decision(-0.20, String.format("CPA %,d원으로 목표(%,d원)의 1.5배를 넘습니다. 하향이 필요합니다.", cpa, targetCpa));
        }
        return new Decision(-0.10, String.format("CPA %,d원으로 목표(%,d원)를 초과합니다. 소폭 하향을 권합니다.", cpa, targetCpa));
    }

    /** 전환 데이터가 없을 때: 같은 캠페인의 평균 CTR 대비로 판단한다. */
    private Decision decideByCtr(double ctr, long salesAmt, double avgRnk, double avgCtr) {
        if (avgCtr <= 0) {
            return null;
        }
        if (ctr < avgCtr * 0.6 && salesAmt > 0) {
            return new Decision(-0.15, String.format(
                    "CTR %.2f%% 로 캠페인 평균(%.2f%%)의 60%% 미만입니다. 비용 대비 반응이 낮습니다.", ctr, avgCtr));
        }
        if (ctr > avgCtr * 1.4 && avgRnk > 3.0) {
            return new Decision(0.15, String.format(
                    "CTR %.2f%% 로 캠페인 평균(%.2f%%)을 크게 웃도는데 평균순위가 %.1f위입니다. 상향 시 클릭 증가를 기대할 수 있습니다.",
                    ctr, avgCtr, avgRnk));
        }
        return null;
    }

    /**
     * 한 캠페인의 평가 기준을 정한다. 목표 CPA 는 구체적인 설정부터 차례로 찾는다:
     * 캠페인 목표 -> 계정 기본 목표 -> 서버 설정(ADS_TARGET_CPA) -> 그 캠페인의 실제 평균 CPA.
     */
    private Baseline baseline(List<KeywordAggregate> aggregates, AdCampaign campaign, AdAccount account) {
        long totalImp = aggregates.stream().mapToLong(r -> nz(r.impCnt())).sum();
        long totalClk = aggregates.stream().mapToLong(r -> nz(r.clkCnt())).sum();
        long totalSales = aggregates.stream().mapToLong(r -> nz(r.salesAmt())).sum();
        long totalCcnt = aggregates.stream().mapToLong(r -> nz(r.ccnt())).sum();

        double avgCtr = totalImp == 0 ? 0.0 : totalClk * 100.0 / totalImp;
        long averageCpa = totalCcnt == 0 ? 0 : totalSales / totalCcnt;

        long targetCpa;
        String source;
        boolean explicit = true;
        if (campaign != null && positive(campaign.getTargetCpa())) {
            targetCpa = campaign.getTargetCpa();
            source = "캠페인 목표";
        } else if (positive(account.getTargetCpa())) {
            targetCpa = account.getTargetCpa();
            source = "계정 기본 목표";
        } else if (policy.targetCpa() > 0) {
            targetCpa = policy.targetCpa();
            source = "서버 설정";
        } else {
            targetCpa = averageCpa;
            source = "캠페인 평균 CPA";
            explicit = false;
        }

        // 목표를 직접 정했다면 전환이 아직 없어도 CPA 기준으로 본다. 전환 없이 돈만 쓰는 키워드를 잡아내기 위해서다.
        boolean useCpa = targetCpa > 0 && (totalCcnt > 0 || explicit);
        return new Baseline(targetCpa, source, avgCtr, useCpa);
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    /** 현재 입찰가. 키워드가 그룹 입찰가를 따르면 그룹 값을 쓴다. */
    private long currentBid(AdKeyword keyword, AdScope scope) {
        if (Boolean.TRUE.equals(keyword.getUseGroupBidAmt()) || keyword.getBidAmt() == null) {
            AdGroup group = scope.adgroupOf(keyword);
            return group != null && group.getBidAmt() != null ? group.getBidAmt() : policy.minBid();
        }
        return keyword.getBidAmt();
    }

    /** 입찰가 상·하한만 적용한다. 목표순위 추천에서 쓴다. */
    private long bound(long proposed) {
        return Math.min(Math.max(proposed, policy.minBid()), policy.maxBid());
    }

    /** 1회 변동폭과 상·하한을 모두 적용한다. 어떤 경로로도 이 한도를 넘길 수 없다. */
    private long clamp(long proposed, long currentBid) {
        long maxUp = Math.round(currentBid * (1 + policy.maxChangeRate()));
        long maxDown = Math.round(currentBid * (1 - policy.maxChangeRate()));
        long bounded = Math.min(Math.max(proposed, maxDown), maxUp);
        return bound(bounded);
    }

    /** 노출가중 평균순위. 노출이 없던 날의 0 이 섞이면 순위가 실제보다 좋게 보인다. */
    private double weightedRank(Double rnkWeighted, long impCnt) {
        if (rnkWeighted == null || impCnt == 0) {
            return 0.0;
        }
        return round2(rnkWeighted / impCnt);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private long nz(Long value) {
        return value != null ? value : 0L;
    }

    private record Decision(double rate, String reason) {}

    private record Baseline(long targetCpa, String targetSource, double avgCtr, boolean useCpa) {}
}
