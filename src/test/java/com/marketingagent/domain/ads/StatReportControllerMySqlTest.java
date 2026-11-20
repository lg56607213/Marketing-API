package com.marketingagent.domain.ads;

import com.marketingagent.support.MySqlTestDatabases;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** StatReportControllerTest 를 MySQL 에서 그대로 다시 돌린다: 성과 리포트 API. Flyway 로 스키마를 만들고 Hibernate 는 validate 만 한다. */
@Tag("mysql")
class StatReportControllerMySqlTest extends StatReportControllerTest {

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        MySqlTestDatabases.register(registry, "stat_report");
    }
}
