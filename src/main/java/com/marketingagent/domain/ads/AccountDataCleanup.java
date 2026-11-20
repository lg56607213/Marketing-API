package com.marketingagent.domain.ads;

import com.marketingagent.domain.ads.account.AdAccountDeleted;
import com.marketingagent.domain.ads.autopilot.AutopilotRunRepository;
import com.marketingagent.domain.ads.autopilot.AutopilotSettingsRepository;
import com.marketingagent.domain.ads.planner.BriefingRepository;
import com.marketingagent.domain.ads.planner.MarketingPlanRepository;
import com.marketingagent.domain.goal.GoalCheckinRepository;
import com.marketingagent.domain.goal.GoalMessageRepository;
import com.marketingagent.domain.goal.GoalSessionRepository;
import com.marketingagent.domain.onboarding.BusinessProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 계정이 지워질 때 시간대별 실적·계획·브리핑·자동 운영·업체 정보·목표 상담을 함께 지운다. */
@Component
@RequiredArgsConstructor
class AccountDataCleanup {

    private final KeywordStatHourlyRepository hourlyRepository;
    private final KeywordConversionHourlyRepository conversionRepository;
    private final MarketingPlanRepository planRepository;
    private final BriefingRepository briefingRepository;
    private final AutopilotSettingsRepository autopilotSettingsRepository;
    private final AutopilotRunRepository autopilotRunRepository;
    private final BusinessProfileRepository profileRepository;
    private final GoalSessionRepository goalSessionRepository;
    private final GoalMessageRepository goalMessageRepository;
    private final GoalCheckinRepository goalCheckinRepository;

    @EventListener
    void on(AdAccountDeleted event) {
        Long id = event.accountId();
        hourlyRepository.deleteAllByAccount(id);
        conversionRepository.deleteAllByAccount(id);
        planRepository.deleteByAdAccountId(id);
        briefingRepository.deleteByAdAccountId(id);
        autopilotSettingsRepository.deleteByAdAccountId(id);
        autopilotRunRepository.deleteByAdAccountId(id);
        profileRepository.deleteByAdAccountId(id);
        goalMessageRepository.deleteByAdAccountId(id);
        goalSessionRepository.deleteByAdAccountId(id);
        goalCheckinRepository.deleteByAdAccountId(id);
    }
}
