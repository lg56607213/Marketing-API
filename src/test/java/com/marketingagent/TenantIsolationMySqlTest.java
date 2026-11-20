package com.marketingagent;

import com.marketingagent.support.MySqlTestDatabases;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** TenantIsolationTests 를 MySQL 에서 그대로 다시 돌린다: 업체·광고 계정 격리. Flyway 로 스키마를 만들고 Hibernate 는 validate 만 한다. */
@Tag("mysql")
class TenantIsolationMySqlTest extends TenantIsolationTests {

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        MySqlTestDatabases.register(registry, "tenant_isolation");
    }
}
