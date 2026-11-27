package com.marketingagent.domain.subsidy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.common.security.CurrentUser;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 지원사업 직접 등록과 검색.
 * <ul>
 *   <li>직접 등록: 수집 대상이 아닌 사업(신보·기보 상품, 지자체 공고 등)을 회원이 넣는다. 그 업체에만 보이고,
 *       최고 관리자가 "공용"으로 넣으면 모든 회원에게 보인다. 넣는 즉시 자격요건을 분석하고 그 업체 사업체를 다시 판정한다.</li>
 *   <li>검색: 업무 말(예: 렌터카, 수출)로 공고 전체를 찾고, 사업자를 고르면 그 조건으로 판정 결과를 함께 붙인다(AI 호출 없음).</li>
 * </ul>
 */
@Slf4j
@Service
public class SubsidyCatalogService {

    public static final String MANUAL = "manual";
    private static final int SEARCH_LIMIT = 300;

    private final SubsidyAnnouncementRepository announcementRepository;
    private final SubsidyProfileRepository profileRepository;
    private final SubsidyMatchRepository matchRepository;
    private final SubsidyService subsidyService;
    private final SubsidyAi ai;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public SubsidyCatalogService(SubsidyAnnouncementRepository announcementRepository,
            SubsidyProfileRepository profileRepository, SubsidyMatchRepository matchRepository,
            SubsidyService subsidyService, SubsidyAi ai, ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.announcementRepository = announcementRepository;
        this.profileRepository = profileRepository;
        this.matchRepository = matchRepository;
        this.subsidyService = subsidyService;
        this.ai = ai;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * @param shared 최고 관리자만: 모든 회원에게 보이게 등록
     */
    public record ManualRequest(String title, String agency, String supportType, String amountText, String targetText,
                                String regionText, LocalDate applyStart, LocalDate applyEnd, String url, String summary,
                                Boolean shared) {}

    public record ManualView(Long id, String title, String agency, String supportType, String amountText,
                             String targetText, String regionText, LocalDate applyStart, LocalDate applyEnd,
                             String url, String summary, boolean shared, boolean analyzed) {}

    /** 검색 결과 한 줄. profileId 를 주면 verdict·score·reasons·blockers 가 채워진다. */
    public record SearchItem(Long announcementId, String title, String agency, String url, String source,
                             boolean manual, LocalDate applyStart, LocalDate applyEnd, Long daysLeft,
                             String supportType, String amountText, String summary, String targetText,
                             String regionText, int hits, Integer score, String verdict, List<String> reasons,
                             List<String> blockers) {}

    // --- 직접 등록 ---

    public List<ManualView> listManual() {
        Long companyId = CurrentUser.companyId();
        List<SubsidyAnnouncement> list = new ArrayList<>(announcementRepository.findBySourceAndCompanyIdOrderByIdDesc(MANUAL, companyId));
        list.addAll(announcementRepository.findSharedManual());
        return list.stream().map(this::manualView).toList();
    }

    public ManualView createManual(ManualRequest req) {
        validate(req);
        boolean shared = Boolean.TRUE.equals(req.shared()) && superAdmin();
        Long companyId = CurrentUser.companyId();
        SubsidyAnnouncement a = new SubsidyAnnouncement(MANUAL, UUID.randomUUID().toString());
        a.refresh(normalized(a.getSourceId(), req), SubsidyService.dedupeKey(req.title(), req.applyEnd()));
        a.ownedBy(shared ? null : companyId, CurrentUser.id());
        SubsidyAnnouncement saved = tx.execute(s -> announcementRepository.save(a));
        analyzeAndRematch(saved.getId(), req, companyId);
        return manualView(announcementRepository.findById(saved.getId()).orElseThrow());
    }

    public ManualView updateManual(Long id, ManualRequest req) {
        validate(req);
        SubsidyAnnouncement a = requireOwnManual(id);
        tx.executeWithoutResult(s -> {
            SubsidyAnnouncement x = announcementRepository.findById(a.getId()).orElseThrow();
            x.refresh(normalized(x.getSourceId(), req), SubsidyService.dedupeKey(req.title(), req.applyEnd()));
        });
        analyzeAndRematch(a.getId(), req, CurrentUser.companyId());
        return manualView(announcementRepository.findById(a.getId()).orElseThrow());
    }

    public void deleteManual(Long id) {
        SubsidyAnnouncement a = requireOwnManual(id);
        tx.executeWithoutResult(s -> {
            matchRepository.deleteByAnnouncement(a.getId());
            announcementRepository.deleteById(a.getId());
        });
    }

    /** 넣자마자 자격요건을 한 번 분석하고, 사용자가 적은 지원 유형·금액은 그대로 살린다. */
    private void analyzeAndRematch(Long id, ManualRequest req, Long companyId) {
        SubsidyAnnouncement a = announcementRepository.findById(id).orElseThrow();
        Eligibility e = ai.extract(a);
        Eligibility merged = new Eligibility(e.regions(), e.entityTypes(), e.minBizAgeYears(), e.maxBizAgeYears(),
                e.preliminaryFounderOk(), e.targetIndustries(), e.excludedIndustries(), e.maxRevenueKrw(),
                e.maxEmployees(), e.requiredCerts(), e.preferred(), e.ceoAgeMax(),
                blank(req.supportType()) != null ? req.supportType().trim() : e.supportType(),
                blank(req.amountText()) != null ? req.amountText().trim() : e.amountText(),
                e.summary() != null ? e.summary() : blank(req.summary()));
        String json;
        try {
            json = objectMapper.writeValueAsString(merged);
        } catch (Exception ex) {
            json = null;
        }
        String eligibility = json;
        tx.executeWithoutResult(s -> announcementRepository.findById(id).ifPresent(x -> x.analyzed(eligibility, merged.summary())));
        for (SubsidyProfile p : profileRepository.findByCompanyIdOrderByIdAsc(companyId)) {
            subsidyService.matchOne(p);
        }
    }

    private SubsidyAnnouncement requireOwnManual(Long id) {
        SubsidyAnnouncement a = announcementRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("없는 지원사업입니다."));
        boolean own = a.getCompanyId() != null && a.getCompanyId().equals(CurrentUser.companyId());
        boolean sharedByAdmin = a.getCompanyId() == null && superAdmin();
        if (!a.isManual() || !(own || sharedByAdmin)) {
            throw new BadRequestException("직접 등록한 지원사업만 고치거나 지울 수 있습니다.");
        }
        return a;
    }

    private static void validate(ManualRequest r) {
        if (r.title() == null || r.title().isBlank()) {
            throw new BadRequestException("사업 이름을 넣어 주세요.");
        }
        if (r.applyStart() != null && r.applyEnd() != null && r.applyEnd().isBefore(r.applyStart())) {
            throw new BadRequestException("마감일이 시작일보다 빠릅니다.");
        }
    }

    private static SubsidyAnnouncement.Normalized normalized(String sourceId, ManualRequest r) {
        String summary = blank(r.summary());
        String extra = String.join(" / ", java.util.stream.Stream.of(
                blank(r.supportType()) != null ? "지원 유형: " + r.supportType().trim() : null,
                blank(r.amountText()) != null ? "지원 규모: " + r.amountText().trim() : null)
                .filter(java.util.Objects::nonNull).toList());
        String body = summary == null ? (extra.isEmpty() ? null : extra) : extra.isEmpty() ? summary : summary + " / " + extra;
        return new SubsidyAnnouncement.Normalized(MANUAL, sourceId, r.title().trim(), blank(r.agency()), body,
                blank(r.targetText()), blank(r.regionText()), r.applyStart(), r.applyEnd(), blank(r.url()), null);
    }

    private ManualView manualView(SubsidyAnnouncement a) {
        Eligibility e = subsidyService.read(a.getEligibilityJson());
        return new ManualView(a.getId(), a.getTitle(), a.getAgency(), e != null ? e.supportType() : null,
                e != null ? e.amountText() : null, a.getTargetText(), a.getRegionText(), a.getApplyStart(),
                a.getApplyEnd(), a.getUrl(), a.getSummary(), a.getCompanyId() == null, a.getAnalyzedAt() != null);
    }

    // --- 검색 ---

    /**
     * @param query     업무·사업 말(공백·쉼표로 여러 개). 비우면 전체
     * @param profileId 고르면 그 사업자 조건으로 판정을 붙이고 적합도 순으로 정렬
     */
    public List<SearchItem> search(String query, Long profileId, boolean includeClosed, boolean hideNo) {
        Long companyId = CurrentUser.companyId();
        SubsidyProfile profile = profileId == null ? null : profileRepository.findByIdAndCompanyId(profileId, companyId)
                .orElseThrow(() -> new BadRequestException("이 업체의 사업자가 아닙니다."));
        List<String> words = words(query);
        if (profile == null && words.isEmpty()) {
            throw new BadRequestException("검색할 업무 말을 넣거나 사업자를 골라 주세요.");
        }
        LocalDate today = LocalDate.now();
        EligibilityMatcher.ProfileFacts facts = profile == null ? null : SubsidyService.facts(profile);

        List<SearchItem> out = new ArrayList<>();
        for (SubsidyAnnouncement a : SubsidyService.representatives(announcementRepository.findVisible(companyId, today, includeClosed))) {
            int hits = words.isEmpty() ? 0 : hits(a, words);
            if (!words.isEmpty() && hits == 0) {
                continue;
            }
            Eligibility e = subsidyService.read(a.getEligibilityJson());
            EligibilityMatcher.Result r = facts == null ? null
                    : EligibilityMatcher.judge(facts, e, a.getTitle(), a.getSummary(), a.getApplyEnd(), today);
            if (r != null && hideNo && SubsidyMatch.NO.equals(r.verdict())) {
                continue;
            }
            out.add(new SearchItem(a.getId(), a.getTitle(), a.getAgency(), a.getUrl(), a.getSource(), a.isManual(),
                    a.getApplyStart(), a.getApplyEnd(),
                    a.getApplyEnd() == null ? null : ChronoUnit.DAYS.between(today, a.getApplyEnd()),
                    e != null ? e.supportType() : null, e != null ? e.amountText() : null,
                    a.getAiSummary() != null ? a.getAiSummary() : a.getSummary(), a.getTargetText(), a.getRegionText(),
                    hits, r != null ? r.score() : null, r != null ? r.verdict() : null,
                    r != null ? r.reasons() : List.of(), r != null ? r.blockers() : List.of()));
        }
        Comparator<SearchItem> byScore = Comparator.comparing(i -> i.score() == null ? -1 : i.score());
        Comparator<SearchItem> order = words.isEmpty() ? byScore.reversed()
                : Comparator.comparingInt(SearchItem::hits).reversed().thenComparing(byScore.reversed());
        out.sort(order.thenComparing(i -> i.applyEnd() == null ? LocalDate.MAX : i.applyEnd()));
        return out.size() > SEARCH_LIMIT ? out.subList(0, SEARCH_LIMIT) : out;
    }

    /** 제목에 나오면 3점, 지원대상·요약·기관·지역에 나오면 1점. 말마다 더한다. */
    static int hits(SubsidyAnnouncement a, List<String> words) {
        String title = squash(a.getTitle());
        String rest = squash(String.join(" ", nz(a.getTargetText()), nz(a.getSummary()), nz(a.getAiSummary()),
                nz(a.getAgency()), nz(a.getRegionText())));
        int score = 0;
        for (String w : words) {
            if (title.contains(w)) {
                score += 3;
            } else if (rest.contains(w)) {
                score += 1;
            }
        }
        return score;
    }

    static List<String> words(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String w : query.split("[,\\s]+")) {
            String s = squash(w);
            if (!s.isEmpty() && !out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    private static String squash(String s) {
        return s == null ? "" : s.replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private static boolean superAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(g -> "ROLE_SUPER_ADMIN".equals(g.getAuthority()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
