package com.marketingagent.domain.ads.autopilot;

import com.marketingagent.support.MySqlTestDatabases;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** AutopilotTest 를 MySQL 에서 그대로 다시 돌린다: 자동 운영 한도와 반영. Flyway 로 스키마를 만들고 Hibernate 는 validate 만 한다. */
@Tag("mysql")
class AutopilotMySqlTest extends AutopilotTest {

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        MySqlTestDatabases.register(registry, "autopilot");
    }
}
