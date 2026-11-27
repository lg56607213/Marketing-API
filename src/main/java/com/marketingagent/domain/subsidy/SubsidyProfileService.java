package com.marketingagent.domain.subsidy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.common.security.CurrentUser;
import com.marketingagent.domain.subsidy.SubsidyDtos.MatchView;
import com.marketingagent.domain.subsidy.SubsidyDtos.ProfileRequest;
import com.marketingagent.domain.subsidy.SubsidyDtos.ProfileView;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인한 업체의 사업체와 추천 공고. 다른 업체의 사업체는 보이지 않는다. */
@Service
@RequiredArgsConstructor
public class SubsidyProfileService {

    private static final Set<String> TYPES = Set.of("법인", "개인", "예비창업자");

    private final SubsidyProfileRepository profileRepository;
    private final SubsidyMatchRepository matchRepository;
    private final SubsidyAnnouncementRepository announcementRepository;
    private final SubsidyService subsidyService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ProfileView> list() {
        return profileRepository.findByCompanyIdOrderByIdAsc(CurrentUser.companyId()).stream().map(this::view).toList();
    }

    @Transactional
    public ProfileView create(ProfileRequest req) {
        SubsidyProfile p = new SubsidyProfile(CurrentUser.companyId());
        apply(p, req);
        return view(profileRepository.save(p));
    }

    @Transactional
    public ProfileView update(Long id, ProfileRequest req) {
        SubsidyProfile p = require(id);
        apply(p, req);
        return view(p);
    }

    /** 사업체를 지우면 매칭 기록도 지운다(개인정보 최소 보관). */
    @Transactional
    public void delete(Long id) {
        SubsidyProfile p = require(id);
        matchRepository.deleteByProfile(p.getId());
        profileRepository.delete(p);
    }

    /** 사업체 정보를 바꾼 뒤 바로 다시 판정한다. 공고 수집·분석은 하지 않는다. */
    public int rematch(Long id) {
        return subsidyService.matchOne(require(id));
    }

    @Transactional(readOnly = true)
    public List<MatchView> matches(Long id) {
        SubsidyProfile p = require(id);
        List<SubsidyMatch> matches = matchRepository.findByProfileId(p.getId());
        Map<Long, SubsidyAnnouncement> ann = announcementRepository
                .findAllById(matches.stream().map(SubsidyMatch::getAnnouncementId).toList()).stream()
                .collect(Collectors.toMap(SubsidyAnnouncement::getId, Function.identity()));
        LocalDate today = LocalDate.now();
        List<MatchView> out = new ArrayList<>();
        for (SubsidyMatch m : matches) {
            SubsidyAnnouncement a = ann.get(m.getAnnouncementId());
            if (a == null || (a.getApplyEnd() != null && a.getApplyEnd().isBefore(today))) {
                continue;
            }
            Eligibility e = subsidyService.read(a.getEligibilityJson());
            out.add(new MatchView(m.getId(), a.getId(), a.getTitle(), a.getAgency(), a.getUrl(), a.getSource(),
                    a.getApplyStart(), a.getApplyEnd(),
                    a.getApplyEnd() == null ? null : ChronoUnit.DAYS.between(today, a.getApplyEnd()),
                    e != null ? e.supportType() : null, e != null ? e.amountText() : null,
                    a.getAiSummary() != null ? a.getAiSummary() : a.getSummary(), m.getScore(), m.getVerdict(),
                    lines(m.getReasons()), lines(m.getBlockers()), m.isAiJudged(), m.isStarred()));
        }
        out.sort(Comparator.comparingInt(MatchView::score).reversed());
        return out;
    }

    @Transactional
    public void star(Long matchId, boolean value) {
        SubsidyMatch m = matchRepository.findById(matchId).orElseThrow(() -> new BadRequestException("없는 추천입니다."));
        require(m.getProfileId());
        m.star(value);
    }

    private SubsidyProfile require(Long id) {
        return profileRepository.findByIdAndCompanyId(id, CurrentUser.companyId())
                .orElseThrow(() -> new BadRequestException("이 업체의 사업체가 아닙니다."));
    }

    private void apply(SubsidyProfile p, ProfileRequest r) {
        if (r.name() == null || r.name().isBlank()) {
            throw new BadRequestException("사업체 이름을 넣어 주세요.");
        }
        String type = r.entityType() == null ? "개인" : r.entityType().trim();
        if (!TYPES.contains(type)) {
            throw new BadRequestException("사업자 유형은 법인 · 개인 · 예비창업자 중에서 골라 주세요.");
        }
        String certs;
        try {
            certs = r.certifications() == null || r.certifications().isEmpty() ? null
                    : objectMapper.writeValueAsString(r.certifications());
        } catch (Exception e) {
            certs = null;
        }
        p.update(r.name().trim(), type, r.foundedDate(), blank(r.regionSido()), blank(r.regionSigungu()),
                blank(r.ksicCode()), blank(r.industryName()), r.revenueKrw(), r.employees(), certs, r.ceoBirthYear(),
                blank(r.ceoGender()), blank(r.keywords()), r.emailOptIn() == null || r.emailOptIn(),
                blank(r.recipients()));
    }

    private ProfileView view(SubsidyProfile p) {
        return new ProfileView(p.getId(), p.getName(), p.getEntityType(), p.getFoundedDate(), p.getRegionSido(),
                p.getRegionSigungu(), p.getKsicCode(), p.getIndustryName(), p.getRevenueKrw(), p.getEmployees(),
                SubsidyService.certs(p.getCertifications()), p.getCeoBirthYear(), p.getCeoGender(), p.getKeywords(),
                p.isEmailOptIn(), p.getRecipients());
    }

    private static List<String> lines(String s) {
        return s == null || s.isBlank() ? List.of() : List.of(s.split("\n"));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
