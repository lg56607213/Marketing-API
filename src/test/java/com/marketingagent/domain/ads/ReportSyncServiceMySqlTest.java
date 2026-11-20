package com.marketingagent.domain.ads;

import com.marketingagent.support.MySqlTestDatabases;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** ReportSyncServiceTest 를 MySQL 에서 그대로 다시 돌린다: 시간대별 성과·전환 수집과 중복 방지. Flyway 로 스키마를 만들고 Hibernate 는 validate 만 한다. */
@Tag("mysql")
class ReportSyncServiceMySqlTest extends ReportSyncServiceTest {

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        MySqlTestDatabases.register(registry, "report_sync");
    }
}
