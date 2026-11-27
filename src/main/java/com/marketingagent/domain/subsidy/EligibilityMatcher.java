package com.marketingagent.domain.subsidy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 사업체 × 공고 규칙 판정. AI 를 부르지 않는다.
 *
 * <ul>
 *   <li>하드 탈락(no): 접수 마감 지남 · 지역 불일치(전국 제외) · 사업자 유형 · 업력 범위 · 제외업종 · 매출·인원 초과 ·
 *       필수인증 미보유 · 대표 나이 초과</li>
 *   <li>점수: 기본 50, 대상업종·관심 키워드 일치 +20, 우대조건 충족마다 +5</li>
 *   <li>공고 조건은 있는데 사업체 정보가 비어 판단 못 한 항목(unknowns)이 2개 이상이거나 점수가 낮으면 maybe</li>
 * </ul>
 * 결과마다 reasons(왜 맞는지)와 blockers(걸리는 조건)를 남긴다.
 */
public final class EligibilityMatcher {

    static final int BASE = 50;
    static final int INDUSTRY_BONUS = 20;
    static final int PREFERRED_BONUS = 5;
    static final int FIT_SCORE = 60;

    /** 시도 이름 표준화. 공고는 "서울특별시", "서울", "서울시" 등 다양하게 쓴다. */
    private static final Map<String, String> SIDO = new LinkedHashMap<>();

    static {
        String[][] pairs = {
            {"서울", "서울"}, {"부산", "부산"}, {"대구", "대구"}, {"인천", "인천"}, {"광주", "광주"}, {"대전", "대전"},
            {"울산", "울산"}, {"세종", "세종"}, {"경기", "경기"}, {"강원", "강원"}, {"충북", "충북"}, {"충청북", "충북"},
            {"충남", "충남"}, {"충청남", "충남"}, {"전북", "전북"}, {"전라북", "전북"}, {"전북특별자치", "전북"},
            {"전남", "전남"}, {"전라남", "전남"}, {"경북", "경북"}, {"경상북", "경북"}, {"경남", "경남"},
            {"경상남", "경남"}, {"제주", "제주"},
        };
        for (String[] p : pairs) {
            SIDO.put(p[0], p[1]);
        }
    }

    /** 금융·보험업(KSIC 64~66). 정책자금 대부분에서 융자 제외. */
    private static final Set<String> FINANCE_KSIC = Set.of("64", "65", "66");

    private EligibilityMatcher() {}

    /** 판정에 쓰는 사업체 정보. 모르는 값은 null. */
    public record ProfileFacts(String entityType, LocalDate foundedDate, String regionSido, String ksicCode,
                               String industryName, Long revenueKrw, Integer employees, List<String> certifications,
                               Integer ceoBirthYear, String ceoGender, String keywords) {}

    public record Result(int score, String verdict, List<String> reasons, List<String> blockers, int unknowns) {}

    public static Result judge(ProfileFacts p, Eligibility e, String title, String summary, LocalDate applyEnd,
            LocalDate today) {
        List<String> reasons = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        int unknowns = 0;
        boolean fail = false;

        if (applyEnd != null && applyEnd.isBefore(today)) {
            blockers.add("접수가 " + applyEnd + "에 마감됐습니다.");
            return new Result(0, SubsidyMatch.NO, reasons, blockers, 0);
        }
        if (e == null) {
            blockers.add("아직 자격요건을 분석하지 못했습니다. 공고 원문을 확인하세요.");
            return new Result(40, SubsidyMatch.MAYBE, reasons, blockers, 1);
        }

        // 지역
        List<String> regions = nonEmpty(e.regions());
        if (regions != null && regions.stream().noneMatch(r -> r.contains("전국"))) {
            String mine = sido(p.regionSido());
            if (mine == null) {
                unknowns++;
                blockers.add("지역 제한(" + String.join(", ", regions) + ")이 있는데 사업장 지역이 등록되지 않았습니다.");
            } else if (regions.stream().map(EligibilityMatcher::sido).noneMatch(mine::equals)) {
                fail = true;
                blockers.add("지역 제한: " + String.join(", ", regions) + " (사업장 " + p.regionSido() + ")");
            } else {
                reasons.add("지역 조건(" + String.join(", ", regions) + ")에 맞습니다.");
            }
        } else if (regions != null) {
            reasons.add("전국 대상입니다.");
        }

        // 사업자 유형
        boolean preliminary = "예비창업자".equals(p.entityType());
        List<String> types = nonEmpty(e.entityTypes());
        if (preliminary) {
            if (Boolean.TRUE.equals(e.preliminaryFounderOk()) || (types != null && types.contains("예비창업자"))) {
                reasons.add("예비창업자도 신청할 수 있습니다.");
            } else if (Boolean.FALSE.equals(e.preliminaryFounderOk()) || types != null) {
                fail = true;
                blockers.add("예비창업자는 대상이 아닙니다(사업자등록 필요).");
            } else {
                unknowns++;
            }
        } else if (types != null && p.entityType() != null) {
            if (types.contains(p.entityType())) {
                reasons.add(p.entityType() + " 사업자 대상입니다.");
            } else {
                fail = true;
                blockers.add("사업자 유형: " + String.join(", ", types) + "만 신청 가능 (내 유형 " + p.entityType() + ")");
            }
        }

        // 업력
        if (e.minBizAgeYears() != null || e.maxBizAgeYears() != null) {
            Double age = preliminary ? Double.valueOf(0) : bizAge(p.foundedDate(), today);
            if (age == null) {
                unknowns++;
                blockers.add("업력 조건(" + ageRange(e) + ")이 있는데 개업일이 등록되지 않았습니다.");
            } else if ((e.minBizAgeYears() != null && age < e.minBizAgeYears())
                    || (e.maxBizAgeYears() != null && age > e.maxBizAgeYears())) {
                fail = true;
                blockers.add(String.format(Locale.ROOT, "업력 조건 %s (내 업력 %.1f년)", ageRange(e), age));
            } else {
                reasons.add(String.format(Locale.ROOT, "업력 조건 %s 안입니다(%.1f년).", ageRange(e), age));
            }
        }

        // 제외 업종
        List<String> excluded = nonEmpty(e.excludedIndustries());
        if (excluded != null) {
            String hit = excludedHit(p, excluded);
            if (hit != null) {
                fail = true;
                blockers.add("제외 업종: " + hit);
            } else if (p.industryName() == null && p.ksicCode() == null) {
                unknowns++;
            }
        }

        // 매출 · 인원
        if (e.maxRevenueKrw() != null) {
            if (p.revenueKrw() == null) {
                unknowns++;
            } else if (p.revenueKrw() > e.maxRevenueKrw()) {
                fail = true;
                blockers.add(String.format("매출 %,d원 이하만 대상 (내 매출 %,d원)", e.maxRevenueKrw(), p.revenueKrw()));
            }
        }
        if (e.maxEmployees() != null) {
            if (p.employees() == null) {
                unknowns++;
            } else if (p.employees() > e.maxEmployees()) {
                fail = true;
                blockers.add(String.format("상시근로자 %d명 이하만 대상 (내 인원 %d명)", e.maxEmployees(), p.employees()));
            }
        }

        // 필수 인증
        List<String> required = nonEmpty(e.requiredCerts());
        if (required != null) {
            List<String> mine = p.certifications() == null ? List.of() : p.certifications();
            List<String> missing = required.stream().filter(r -> mine.stream().noneMatch(m -> m.contains(r) || r.contains(m))).toList();
            if (!missing.isEmpty()) {
                fail = true;
                blockers.add("필수 인증 없음: " + String.join(", ", missing));
            } else {
                reasons.add("필수 인증(" + String.join(", ", required) + ")을 갖고 있습니다.");
            }
        }

        // 대표 나이
        Integer ceoAge = p.ceoBirthYear() != null ? today.getYear() - p.ceoBirthYear() : null;
        if (e.ceoAgeMax() != null) {
            if (ceoAge == null) {
                unknowns++;
                blockers.add("대표자 나이 제한(만 " + e.ceoAgeMax() + "세 이하)이 있는데 대표 생년이 등록되지 않았습니다.");
            } else if (ceoAge > e.ceoAgeMax()) {
                fail = true;
                blockers.add("대표자 만 " + e.ceoAgeMax() + "세 이하만 대상 (대표 약 " + ceoAge + "세)");
            } else {
                reasons.add("대표자 나이 조건(만 " + e.ceoAgeMax() + "세 이하)에 맞습니다.");
            }
        }

        if (fail) {
            return new Result(0, SubsidyMatch.NO, reasons, blockers, unknowns);
        }

        // 점수
        int score = BASE;
        String industryHit = industryHit(p, nonEmpty(e.targetIndustries()), title, summary);
        if (industryHit != null) {
            score += INDUSTRY_BONUS;
            reasons.add(industryHit);
        }
        for (String pref : nonEmpty(e.preferred()) != null ? e.preferred() : List.<String>of()) {
            if (meetsPreferred(pref, p, ceoAge)) {
                score += PREFERRED_BONUS;
                reasons.add("우대 조건 충족: " + pref);
            }
        }
        if (applyEnd != null) {
            long days = ChronoUnit.DAYS.between(today, applyEnd);
            if (days <= 7) {
                reasons.add("마감 임박: D-" + days + " (" + applyEnd + ")");
            }
        }
        score = Math.min(100, score);
        String verdict = unknowns <= 1 && score >= FIT_SCORE ? SubsidyMatch.FIT : SubsidyMatch.MAYBE;
        return new Result(score, verdict, reasons, blockers, unknowns);
    }

    static String sido(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        String r = region.replace(" ", "");
        for (Map.Entry<String, String> e : SIDO.entrySet()) {
            if (r.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return r;
    }

    static Double bizAge(LocalDate founded, LocalDate today) {
        return founded == null ? null : ChronoUnit.DAYS.between(founded, today) / 365.25;
    }

    private static String ageRange(Eligibility e) {
        if (e.minBizAgeYears() != null && e.maxBizAgeYears() != null) {
            return fmt(e.minBizAgeYears()) + "~" + fmt(e.maxBizAgeYears()) + "년";
        }
        return e.maxBizAgeYears() != null ? fmt(e.maxBizAgeYears()) + "년 이내" : fmt(e.minBizAgeYears()) + "년 이상";
    }

    private static String fmt(Double v) {
        return v == Math.floor(v) ? String.valueOf(v.intValue()) : String.valueOf(v);
    }

    private static String excludedHit(ProfileFacts p, List<String> excluded) {
        String industry = (p.industryName() == null ? "" : p.industryName()).replace(" ", "");
        boolean financeKsic = p.ksicCode() != null && p.ksicCode().length() >= 2
                && FINANCE_KSIC.contains(p.ksicCode().substring(0, 2));
        for (String x : excluded) {
            String word = x.replace(" ", "");
            if (!word.isEmpty() && industry.contains(word)) {
                return x;
            }
            if (financeKsic && (word.contains("금융") || word.contains("보험") || word.contains("대부"))) {
                return x + " (업종코드 " + p.ksicCode() + ")";
            }
        }
        return null;
    }

    private static String industryHit(ProfileFacts p, List<String> targets, String title, String summary) {
        String mine = ((p.industryName() == null ? "" : p.industryName()) + " "
                + (p.keywords() == null ? "" : p.keywords())).replace(" ", "");
        if (targets != null) {
            for (String t : targets) {
                String w = t.replace(" ", "");
                if (!w.isEmpty() && mine.contains(w)) {
                    return "대상 업종(" + t + ")에 해당합니다.";
                }
            }
        }
        // 관심 키워드가 공고 제목·요약에 나오면 관련 있는 공고로 본다
        String text = ((title == null ? "" : title) + " " + (summary == null ? "" : summary)).replace(" ", "");
        if (p.keywords() != null) {
            for (String k : p.keywords().split("[,\\s]+")) {
                if (k.length() >= 2 && text.contains(k)) {
                    return "관심 키워드 '" + k + "'가 공고에 나옵니다.";
                }
            }
        }
        return null;
    }

    private static boolean meetsPreferred(String pref, ProfileFacts p, Integer ceoAge) {
        List<String> certs = p.certifications() == null ? List.of() : p.certifications();
        if (pref.contains("청년")) {
            return ceoAge != null && ceoAge <= 39;
        }
        if (pref.contains("여성")) {
            return "여".equals(p.ceoGender()) || "여성".equals(p.ceoGender()) || certs.stream().anyMatch(c -> c.contains("여성"));
        }
        return certs.stream().anyMatch(c -> pref.contains(c) || c.contains(pref));
    }

    private static List<String> nonEmpty(List<String> list) {
        return list == null || list.isEmpty() ? null : list;
    }
}
