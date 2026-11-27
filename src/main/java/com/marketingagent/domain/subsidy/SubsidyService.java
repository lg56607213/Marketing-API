package com.marketingagent.domain.subsidy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.domain.ads.report.ReportMailer;
import com.marketingagent.domain.subsidy.SubsidyAnnouncement.Normalized;
import com.marketingagent.domain.subsidy.collect.AnnouncementCollector;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

/**
 * 정부지원사업 수집 → 분석 → 매칭 → 알림. 각 단계는 subsidy_job_runs 에 결과와 오류를 남긴다.
 * 정보 제공만 한다. 알선·대행·성공보수 기능은 두지 않는다.
 */
@Slf4j
@Service
public class SubsidyService {

    public static final String JOB_COLLECT = "collect";
    public static final String JOB_ANALYZE = "analyze";
    public static final String JOB_MATCH = "match";
    public static final String JOB_NOTIFY = "notify";
    static final int DEADLINE_ALERT_DAYS = 3;
    static final int NOTIFY_MIN_SCORE = 60;

    private final List<AnnouncementCollector> collectors;
    private final SubsidyAnnouncementRepository announcementRepository;
    private final SubsidyProfileRepository profileRepository;
    private final SubsidyMatchRepository matchRepository;
    private final SubsidyJobRunRepository jobRunRepository;
    private final SubsidyAi ai;
    private final SubsidyProperties properties;
    private final ReportMailer mailer;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    @Value("${app.public-url:https://marketagent.co.kr}")
    private String publicUrl;

    @Value("${app.encryption-key}")
    private String signingKey;

    public SubsidyService(List<AnnouncementCollector> collectors, SubsidyAnnouncementRepository announcementRepository,
            SubsidyProfileRepository profileRepository, SubsidyMatchRepository matchRepository,
            SubsidyJobRunRepository jobRunRepository, SubsidyAi ai, SubsidyProperties properties, ReportMailer mailer,
            ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.collectors = collectors;
        this.announcementRepository = announcementRepository;
        this.profileRepository = profileRepository;
        this.matchRepository = matchRepository;
        this.jobRunRepository = jobRunRepository;
        this.ai = ai;
        this.properties = properties;
        this.mailer = mailer;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // --- 수집 ---

    public String collect() {
        return record(JOB_COLLECT, () -> {
            List<String> lines = new ArrayList<>();
            boolean failed = false;
            for (AnnouncementCollector c : collectors) {
                if (!c.configured()) {
                    lines.add(c.source() + ": 인증키가 없어 건너뜀");
                    continue;
                }
                try {
                    List<Normalized> rows = c.fetchOpen();
                    int[] counts = new int[2];
                    for (Normalized n : rows) {
                        tx.executeWithoutResult(s -> {
                            SubsidyAnnouncement a = announcementRepository.findBySourceAndSourceId(n.source(), n.sourceId())
                                    .orElseGet(() -> {
                                        counts[0]++;
                                        return new SubsidyAnnouncement(n.source(), n.sourceId());
                                    });
                            a.refresh(n, dedupeKey(n.title(), n.applyEnd()));
                            announcementRepository.save(a);
                            counts[1]++;
                        });
                    }
                    lines.add(String.format("%s: %d건 받음(새 공고 %d건)", c.source(), counts[1], counts[0]));
                } catch (RuntimeException e) {
                    failed = true;
                    log.error("지원사업 수집 실패: {}", c.source(), e);
                    lines.add(c.source() + ": 실패 " + e.getMessage());
                }
            }
            return new Outcome(failed ? "PARTIAL" : "OK", String.join("\n", lines));
        });
    }

    /** 같은 공고가 여러 소스에 올라올 때 묶는 키. 괄호·기호·공백을 빼고 제목 앞부분 + 마감일. */
    static String dedupeKey(String title, LocalDate applyEnd) {
        String t = title == null ? "" : title
                .replaceAll("\\[[^\\]]*]|\\([^)]*\\)|<[^>]*>|「|」|『|』", "")
                .replaceAll("[^0-9A-Za-z가-힣]", "")
                .toLowerCase(Locale.ROOT);
        return (t.length() > 80 ? t.substring(0, 80) : t) + "|" + (applyEnd == null ? "-" : applyEnd);
    }

    // --- 분석 (공고 1건당 1회) ---

    public String analyze() {
        return record(JOB_ANALYZE, () -> {
            int limit = properties.maxAnalyzePerDay() > 0 ? properties.maxAnalyzePerDay() : 200;
            List<SubsidyAnnouncement> todo = announcementRepository.findUnanalyzed(LocalDate.now(), PageRequest.of(0, limit));
            int done = 0;
            for (SubsidyAnnouncement a : todo) {
                Eligibility e = ai.extract(a);
                String json = write(e);
                Long id = a.getId();
                tx.executeWithoutResult(s -> announcementRepository.findById(id)
                        .ifPresent(x -> x.analyzed(json, e.summary())));
                done++;
            }
            long left = announcementRepository.countByAnalyzedAtIsNull();
            return new Outcome("OK", String.format("공고 %d건 분석(%s). 남은 미분석 %d건(하루 상한 %d건).",
                    done, ai.available() ? "AI" : "규칙", left, limit));
        });
    }

    // --- 매칭 ---

    public String matchAll() {
        return record(JOB_MATCH, () -> {
            List<SubsidyProfile> profiles = profileRepository.findAll();
            List<SubsidyAnnouncement> open = representatives(announcementRepository.findOpen(LocalDate.now()));
            int total = 0;
            for (SubsidyProfile p : profiles) {
                total += matchProfile(p, open);
            }
            return new Outcome("OK", String.format("사업체 %d곳 × 공고 %d건 판정, 적합·검토 %d건", profiles.size(),
                    open.size(), total));
        });
    }

    /** 사업체 하나를 지금 다시 판정한다(사업체 정보를 고친 직후 등). */
    public int matchOne(SubsidyProfile p) {
        return matchProfile(p, representatives(announcementRepository.findOpen(LocalDate.now())));
    }

    /** 같은 공고 묶음에서 하나만 남긴다. 분석된 것, 원문 정보가 많은 것을 먼저. */
    static List<SubsidyAnnouncement> representatives(List<SubsidyAnnouncement> all) {
        Map<String, SubsidyAnnouncement> byKey = new LinkedHashMap<>();
        for (SubsidyAnnouncement a : all) {
            byKey.merge(a.getDedupeKey(), a, (x, y) -> rank(y) > rank(x) ? y : x);
        }
        return new ArrayList<>(byKey.values());
    }

    private static int rank(SubsidyAnnouncement a) {
        return (a.getEligibilityJson() != null ? 2 : 0) + (a.getTargetText() != null ? 1 : 0);
    }

    private int matchProfile(SubsidyProfile p, List<SubsidyAnnouncement> open) {
        LocalDate today = LocalDate.now();
        EligibilityMatcher.ProfileFacts facts = facts(p);
        record Scored(SubsidyAnnouncement a, EligibilityMatcher.Result r) {}
        List<Scored> scored = new ArrayList<>();
        for (SubsidyAnnouncement a : open) {
            scored.add(new Scored(a, EligibilityMatcher.judge(facts, read(a.getEligibilityJson()), a.getTitle(),
                    a.getSummary(), a.getApplyEnd(), today)));
        }
        Map<Long, SubsidyMatch> existing = new HashMap<>();
        matchRepository.findByProfileId(p.getId()).forEach(m -> existing.put(m.getAnnouncementId(), m));

        // 애매한 건 중 점수 높은 것만, 아직 AI 로 안 본 것만 하루 상한까지 재판단
        int budget = properties.maxJudgePerProfile() > 0 ? properties.maxJudgePerProfile() : 5;
        List<Scored> toJudge = scored.stream()
                .filter(s -> SubsidyMatch.MAYBE.equals(s.r().verdict()))
                .filter(s -> existing.get(s.a().getId()) == null || !existing.get(s.a().getId()).isAiJudged())
                .sorted(Comparator.comparingInt((Scored s) -> s.r().score()).reversed())
                .limit(budget).toList();
        String profileText = describe(p, facts);

        int kept = 0;
        for (Scored s : scored) {
            EligibilityMatcher.Result r = s.r();
            String verdict = r.verdict();
            int score = r.score();
            List<String> reasons = new ArrayList<>(r.reasons());
            List<String> blockers = new ArrayList<>(r.blockers());
            boolean aiJudged = false;
            SubsidyMatch before = existing.get(s.a().getId());
            if (toJudge.contains(s)) {
                SubsidyAi.Judgment j = ai.judge(profileText, s.a(), s.a().getEligibilityJson(), r);
                if (j != null) {
                    verdict = j.verdict();
                    score = j.score();
                    reasons.addAll(j.reasons());
                    blockers.addAll(j.blockers());
                    aiJudged = true;
                }
            } else if (before != null && before.isAiJudged() && SubsidyMatch.MAYBE.equals(r.verdict())) {
                // 이미 AI 로 본 건은 그 판정을 유지(매일 다시 부르지 않는다)
                continue;
            }
            if (SubsidyMatch.NO.equals(verdict) && before == null && r.blockers().stream().anyMatch(b -> b.startsWith("접수가"))) {
                continue; // 마감 지난 공고는 새로 만들지 않는다
            }
            if (!SubsidyMatch.NO.equals(verdict)) {
                kept++;
            }
            String v = verdict;
            int sc = score;
            boolean judged = aiJudged;
            tx.executeWithoutResult(st -> {
                SubsidyMatch m = matchRepository.findByProfileIdAndAnnouncementId(p.getId(), s.a().getId())
                        .orElseGet(() -> new SubsidyMatch(p.getId(), s.a().getId()));
                m.judged(sc, v, String.join("\n", reasons), String.join("\n", blockers), judged || m.isAiJudged());
                matchRepository.save(m);
            });
        }
        return kept;
    }

    static EligibilityMatcher.ProfileFacts facts(SubsidyProfile p) {
        return new EligibilityMatcher.ProfileFacts(p.getEntityType(), p.getFoundedDate(), p.getRegionSido(),
                p.getKsicCode(), p.getIndustryName(), p.getRevenueKrw(), p.getEmployees(), certs(p.getCertifications()),
                p.getCeoBirthYear(), p.getCeoGender(), p.getKeywords());
    }

    static List<String> certs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return new ObjectMapper().readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of(json.split("\\s*,\\s*"));
        }
    }

    private static String describe(SubsidyProfile p, EligibilityMatcher.ProfileFacts f) {
        Double age = EligibilityMatcher.bizAge(f.foundedDate(), LocalDate.now());
        return String.format("유형 %s / 지역 %s %s / 업종 %s(%s) / 업력 %s / 매출 %s / 인원 %s / 인증 %s / 대표 %s년생 %s / 관심 %s",
                f.entityType(), nz(p.getRegionSido()), nz(p.getRegionSigungu()), nz(f.industryName()), nz(f.ksicCode()),
                age == null ? "모름" : String.format(Locale.ROOT, "%.1f년", age),
                f.revenueKrw() == null ? "모름" : String.format("%,d원", f.revenueKrw()),
                f.employees() == null ? "모름" : f.employees() + "명", f.certifications(),
                f.ceoBirthYear() == null ? "모름" : f.ceoBirthYear(), nz(f.ceoGender()), nz(f.keywords()));
    }

    // --- 알림 ---

    public String sendNotifications() {
        return record(JOB_NOTIFY, () -> {
            if (!mailer.configured()) {
                return new Outcome("SKIPPED", "메일 발송 설정(MAIL_HOST 등)이 없어 보내지 않았습니다.");
            }
            int sent = 0;
            List<String> lines = new ArrayList<>();
            for (SubsidyProfile p : profileRepository.findAll()) {
                List<String> to = recipients(p);
                if (!p.isEmailOptIn() || to.isEmpty()) {
                    continue;
                }
                try {
                    if (notifyProfile(p, to)) {
                        sent++;
                    }
                } catch (RuntimeException e) {
                    log.error("지원사업 알림 실패: 사업체 {}", p.getId(), e);
                    lines.add("사업체 " + p.getId() + " 실패: " + e.getMessage());
                }
            }
            lines.add(0, "메일 " + sent + "통 발송");
            return new Outcome(lines.size() > 1 ? "PARTIAL" : "OK", String.join("\n", lines));
        });
    }

    private boolean notifyProfile(SubsidyProfile p, List<String> to) {
        LocalDate today = LocalDate.now();
        List<SubsidyMatch> fresh = new ArrayList<>();
        List<SubsidyMatch> closing = new ArrayList<>();
        Map<Long, SubsidyAnnouncement> ann = new HashMap<>();
        for (SubsidyMatch m : matchRepository.findByProfileId(p.getId())) {
            if (SubsidyMatch.NO.equals(m.getVerdict())) {
                continue;
            }
            SubsidyAnnouncement a = announcementRepository.findById(m.getAnnouncementId()).orElse(null);
            if (a == null || (a.getApplyEnd() != null && a.getApplyEnd().isBefore(today))) {
                continue;
            }
            ann.put(a.getId(), a);
            boolean worth = SubsidyMatch.FIT.equals(m.getVerdict()) || m.getScore() >= NOTIFY_MIN_SCORE || m.isStarred();
            if (m.getNotifiedAt() == null && worth) {
                fresh.add(m);
            } else if (m.getDeadlineNotifiedAt() == null && a.getApplyEnd() != null && worth
                    && ChronoUnit.DAYS.between(today, a.getApplyEnd()) <= DEADLINE_ALERT_DAYS) {
                closing.add(m);
            }
        }
        if (fresh.isEmpty() && closing.isEmpty()) {
            return false;
        }
        fresh.sort(Comparator.comparingInt(SubsidyMatch::getScore).reversed());
        String html = mailHtml(p, fresh, closing, ann);
        mailer.send(to, String.format("[지원사업] %s: 새로 맞는 공고 %d건 · 마감 임박 %d건", p.getName(), fresh.size(),
                closing.size()), html);
        tx.executeWithoutResult(s -> {
            fresh.forEach(m -> matchRepository.findById(m.getId()).ifPresent(SubsidyMatch::notified));
            closing.forEach(m -> matchRepository.findById(m.getId()).ifPresent(SubsidyMatch::deadlineNotified));
        });
        return true;
    }

    String mailHtml(SubsidyProfile p, List<SubsidyMatch> fresh, List<SubsidyMatch> closing,
            Map<Long, SubsidyAnnouncement> ann) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:sans-serif;max-width:680px;color:#111\">");
        sb.append("<h2 style=\"font-size:18px\">").append(h(p.getName())).append(" 맞춤 지원사업</h2>");
        if (!closing.isEmpty()) {
            sb.append("<h3 style=\"font-size:15px;color:#b91c1c\">마감 임박</h3>");
            closing.forEach(m -> sb.append(item(m, ann.get(m.getAnnouncementId()))));
        }
        if (!fresh.isEmpty()) {
            sb.append("<h3 style=\"font-size:15px\">새로 맞는 공고</h3>");
            fresh.stream().limit(20).forEach(m -> sb.append(item(m, ann.get(m.getAnnouncementId()))));
            if (fresh.size() > 20) {
                sb.append("<p>외 ").append(fresh.size() - 20).append("건은 화면에서 확인하세요.</p>");
            }
        }
        sb.append("<p><a href=\"").append(h(publicUrl)).append("/subsidy\">전체 보기</a></p>");
        sb.append("<hr><p style=\"font-size:12px;color:#666\">공고 원문 확인 필수, 매칭 결과는 참고용입니다. ")
                .append("이 메일은 정보 제공용이며 자금 알선·대행을 하지 않습니다.<br>")
                .append("<a href=\"").append(h(unsubscribeUrl(p.getId()))).append("\">이 사업체의 알림 메일 받지 않기</a></p>");
        sb.append("</div>");
        return sb.toString();
    }

    private String item(SubsidyMatch m, SubsidyAnnouncement a) {
        if (a == null) {
            return "";
        }
        Eligibility e = read(a.getEligibilityJson());
        String dday = a.getApplyEnd() == null ? "마감일 확인 필요"
                : "마감 " + a.getApplyEnd() + " (D-" + ChronoUnit.DAYS.between(LocalDate.now(), a.getApplyEnd()) + ")";
        String reason = m.getReasons() == null ? "" : String.join(" · ", m.getReasons().split("\n")).replaceAll("(.{0,160}).*", "$1");
        return "<div style=\"border:1px solid #e5e7eb;border-radius:8px;padding:10px 12px;margin:8px 0\">"
                + "<b><a href=\"" + h(nz(a.getUrl())) + "\">" + h(a.getTitle()) + "</a></b><br>"
                + "<span style=\"font-size:13px;color:#555\">" + h(nz(a.getAgency())) + " · " + dday
                + (e != null && e.supportType() != null ? " · " + h(e.supportType()) : "")
                + (e != null && e.amountText() != null ? " · " + h(e.amountText()) : "") + " · 적합도 " + m.getScore()
                + "</span><br><span style=\"font-size:13px\">" + h(nz(a.getAiSummary())) + "</span>"
                + (reason.isEmpty() ? "" : "<br><span style=\"font-size:12px;color:#047857\">" + h(reason) + "</span>")
                + "</div>";
    }

    String unsubscribeUrl(Long profileId) {
        return publicUrl + "/api/public/subsidy/unsubscribe?p=" + profileId + "&t=" + sign(profileId);
    }

    String sign(Long profileId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(("subsidy-unsubscribe:" + profileId).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 메일의 수신거부 링크. 로그인 없이 서명만 확인한다. */
    public boolean unsubscribe(Long profileId, String token) {
        if (profileId == null || token == null || !java.security.MessageDigest.isEqual(
                sign(profileId).getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        tx.executeWithoutResult(s -> profileRepository.findById(profileId).ifPresent(SubsidyProfile::unsubscribe));
        return true;
    }

    static List<String> recipients(SubsidyProfile p) {
        if (p.getRecipients() == null) {
            return List.of();
        }
        return List.of(p.getRecipients().split("[,;\\s]+")).stream().filter(s -> s.contains("@")).toList();
    }

    // --- 보조 ---

    record Outcome(String status, String detail) {}

    private String record(String job, java.util.function.Supplier<Outcome> work) {
        SubsidyJobRun run = tx.execute(s -> jobRunRepository.save(new SubsidyJobRun(job)));
        Outcome out;
        try {
            out = work.get();
        } catch (RuntimeException e) {
            log.error("지원사업 작업 실패: {}", job, e);
            out = new Outcome("FAILED", e.getMessage());
        }
        Outcome result = out;
        tx.executeWithoutResult(s -> jobRunRepository.findById(run.getId()).ifPresent(r -> r.finish(result.status(), result.detail())));
        log.info("지원사업 {}: {} {}", job, result.status(), result.detail());
        return result.status() + ": " + result.detail();
    }

    Eligibility read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Eligibility.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String write(Eligibility e) {
        try {
            return objectMapper.writeValueAsString(e);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String h(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s);
    }

    private static String nz(Object s) {
        return s == null ? "" : s.toString();
    }
}
