package com.marketingagent.domain.onboarding;

import com.marketingagent.support.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.planner.PlanService;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanKeyword;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanProposal;
import com.marketingagent.domain.onboarding.OnboardingDtos.Alternative;
import com.marketingagent.domain.onboarding.OnboardingDtos.ProfileRequest;
import com.marketingagent.domain.onboarding.OnboardingDtos.Proposal;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Stub 데이터로 업체 등록과 예산 3안 제안을 확인한다. 홈페이지는 막힌 주소를 써서 테스트가 바깥 망에 의존하지 않게 한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:onboarding;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "naver.searchad.provider=stub",
        "ai.provider=stub"
})
class OnboardingTest {

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private OnboardingService onboardingService;
    @Autowired
    private PlanService planService;

    private ProfileRequest request(long budget, long target, Long saleValue) {
        return new ProfileRequest("테스트 화물", "http://127.0.0.1/", "중고 화물차와 탑차를 매매하고 리스합니다.",
                List.of("화물차", "탑차"), budget, target, saleValue, null, null);
    }

    @Test
    void proposesThreeBudgetAlternativesFromTheSameResearch() {
        accounts.runAs(8301L, acc -> {
            assertThat(onboardingService.current(acc)).isEmpty();

            Proposal proposal = onboardingService.submit(acc, request(300_000, 10, 200_000L));

            assertThat(proposal.profile().companyName()).isEqualTo("테스트 화물");
            assertThat(proposal.profile().homepageUrl()).isEqualTo("http://127.0.0.1/");
            // 내부 주소는 열어 보지 않고 그렇게 알린다.
            assertThat(proposal.siteCheck().reachable()).isFalse();
            assertThat(proposal.siteCheck().notes()).anySatisfy(n -> assertThat(n).contains("공개 홈페이지 주소만"));

            assertThat(proposal.alternatives()).extracting(Alternative::key)
                    .containsExactly("INCREASE", "REQUESTED", "DECREASE");
            Alternative up = proposal.alternatives().get(0);
            Alternative mid = proposal.alternatives().get(1);
            Alternative down = proposal.alternatives().get(2);

            assertThat(up.monthlyBudget()).isEqualTo(450_000);
            assertThat(mid.monthlyBudget()).isEqualTo(300_000);
            assertThat(down.monthlyBudget()).isEqualTo(180_000);

            // 어느 안도 예산을 넘겨 쓰지 않고, 돈을 더 쓸수록 전환은 늘되 1건당 비용은 비싸진다.
            assertThat(proposal.alternatives()).allSatisfy(a -> {
                assertThat(a.expectedCost()).isPositive().isLessThanOrEqualTo(a.monthlyBudget());
                assertThat(a.expectedConversions()).isPositive();
                assertThat(a.conversionsLow()).isLessThanOrEqualTo(a.expectedConversions());
                assertThat(a.conversionsHigh()).isGreaterThanOrEqualTo(a.expectedConversions());
                assertThat(a.effect()).isNotBlank();
                assertThat(a.revenue()).isEqualTo(a.expectedConversions() * 200_000L);
                long selectedCost = a.plan().keywords().stream().filter(PlanKeyword::selected)
                        .mapToLong(PlanKeyword::expectedCost).sum();
                assertThat(a.expectedCost()).isEqualTo(selectedCost);
            });
            assertThat(up.expectedConversions()).isGreaterThan(mid.expectedConversions());
            assertThat(mid.expectedConversions()).isGreaterThan(down.expectedConversions());
            assertThat(up.costPerConversion()).isGreaterThanOrEqualTo(mid.costPerConversion());
            assertThat(mid.costPerConversion()).isGreaterThanOrEqualTo(down.costPerConversion());
            // 추가 전환 1건의 값은 평균보다 비싸다. 싼 클릭부터 사기 때문이다.
            assertThat(up.extraCostPerExtraConversion()).isGreaterThan(mid.costPerConversion());
            assertThat(down.extraCostPerExtraConversion()).isNull();

            // 정확히 한 안을 권하고 이유를 적는다.
            assertThat(proposal.alternatives()).filteredOn(Alternative::recommended).hasSize(1);
            assertThat(proposal.recommendedKey()).isIn("INCREASE", "REQUESTED", "DECREASE");
            assertThat(proposal.recommendation()).isNotBlank();
            assertThat(proposal.selectedOption()).isNull();

            // 고르기 전에는 운영 계획이 생기지 않는다.
            assertThat(planService.current(acc)).isEmpty();
            Proposal selected = onboardingService.select(acc, "DECREASE");
            assertThat(selected.selectedOption()).isEqualTo("DECREASE");
            PlanProposal plan = planService.current(acc).orElseThrow();
            assertThat(plan.monthlyBudget()).isEqualTo(180_000);
            assertThat(plan.expectedConversions()).isEqualTo(down.expectedConversions());
            assertThat(onboardingService.current(acc).orElseThrow().selectedOption()).isEqualTo("DECREASE");

            assertThatThrownBy(() -> onboardingService.select(acc, "NOPE")).isInstanceOf(BadRequestException.class);
        });
        // 다른 회원에게는 보이지 않는다.
        accounts.runAs(8302L, acc -> {
            assertThat(onboardingService.current(acc)).isEmpty();
            assertThatThrownBy(() -> onboardingService.select(acc, "REQUESTED")).isInstanceOf(BadRequestException.class);
        });
    }

    @Test
    void recommendsTheCheapestAlternativeThatMeetsTheGoal() {
        // 목표가 아주 낮으면 감액안으로도 채운다.
        accounts.runAs(8303L, acc -> {
            Proposal easy = onboardingService.submit(acc, request(300_000, 1, null));
            assertThat(easy.recommendedKey()).isEqualTo("DECREASE");
            assertThat(easy.recommendation()).contains("감액안을 권합니다");
            assertThat(easy.alternatives().get(1).revenue()).isNull();

            // 다시 제출하면 정보와 제안이 바뀌고 선택은 초기화된다.
            onboardingService.select(acc, "DECREASE");
            Proposal again = onboardingService.submit(acc, request(300_000, 100_000, null));
            assertThat(again.selectedOption()).isNull();
            // 어느 안으로도 닿지 않는 목표면 요청대로 하되 목표를 다시 잡으라고 한다.
            assertThat(again.recommendedKey()).isEqualTo("REQUESTED");
            assertThat(again.recommendation()).contains("목표를 다시 잡기를 권합니다");
        });
    }

    @Test
    void neverOpensInternalAddresses() {
        for (String blocked : List.of("http://127.0.0.1/", "http://localhost:8090/", "https://192.168.0.1/",
                "http://10.0.0.5/", "http://169.254.169.254/latest/meta-data", "ftp://example.com/",
                "https://example.com:8443/", "http://[::1]/", "https://user@example.com/")) {
            assertThatThrownBy(() -> SiteInspector.guard(URI.create(blocked)))
                    .as(blocked)
                    .isInstanceOf(SiteInspector.BlockedAddressException.class);
        }
        assertThat(SiteInspector.normalize("www.example.com")).isEqualTo("https://www.example.com");
        assertThatThrownBy(() -> SiteInspector.normalize("not a url")).isInstanceOf(BadRequestException.class);
    }
}
