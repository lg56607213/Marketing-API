package com.marketingagent.domain.ads.launch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.common.security.SystemTask;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.RunResult;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * 서버가 뜨면 ads-launch/*.json 계획을 한 번씩 적용한다. ADS_LAUNCH_ENABLED=true 일 때만 돈다.
 * 계획 적용은 여러 번 해도 같은 결과라서(이미 있는 그룹·소재·키워드는 건너뜀) 재시작마다 돌아도 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ads.launch.enabled", havingValue = "true")
public class AdLaunchRunner {

    private final AdLaunchService launchService;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        // 네이버 호출이 길어도 서버 기동·헬스체크를 막지 않게 따로 돈다
        Thread worker = new Thread(this::runAll, "ad-launch");
        worker.setDaemon(true);
        worker.start();
    }

    void runAll() {
        Resource[] plans;
        try {
            plans = new PathMatchingResourcePatternResolver().getResources("classpath*:ads-launch/*.json");
        } catch (Exception e) {
            log.error("새 광고 계획을 읽지 못했습니다", e);
            return;
        }
        for (Resource resource : plans) {
            try (InputStream in = resource.getInputStream()) {
                LaunchPlan plan = objectMapper.readValue(in, LaunchPlan.class);
                RunResult result = SystemTask.callFor(plan.accountId(), () -> launchService.launch(plan));
                log.info("새 광고 계획 {} 적용: {}", resource.getFilename(), result.summary());
            } catch (Exception e) {
                log.error("새 광고 계획 {} 적용 실패", resource.getFilename(), e);
            }
        }
    }
}
