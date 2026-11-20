package com.marketingagent.domain.onboarding;

import com.marketingagent.support.MySqlTestDatabases;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** OnboardingTest 를 MySQL 에서 그대로 다시 돌린다: 업체 등록·예산 제안. Flyway 로 스키마를 만들고 Hibernate 는 validate 만 한다. */
@Tag("mysql")
class OnboardingMySqlTest extends OnboardingTest {

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        MySqlTestDatabases.register(registry, "onboarding");
    }
}
