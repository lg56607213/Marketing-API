package com.marketingagent.domain.ads.planner;

import com.marketingagent.support.MySqlTestDatabases;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** PlannerTest 를 MySQL 에서 그대로 다시 돌린다: 운영 계획·브리핑. Flyway 로 스키마를 만들고 Hibernate 는 validate 만 한다. */
@Tag("mysql")
class PlannerMySqlTest extends PlannerTest {

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        MySqlTestDatabases.register(registry, "planner");
    }
}
