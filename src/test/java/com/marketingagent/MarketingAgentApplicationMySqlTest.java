package com.marketingagent;

import com.marketingagent.support.MySqlTestDatabases;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** MarketingAgentApplicationTests 를 MySQL 에서 그대로 다시 돌린다: 애플리케이션 기동과 엔티티-스키마 검증. Flyway 로 스키마를 만들고 Hibernate 는 validate 만 한다. */
@Tag("mysql")
class MarketingAgentApplicationMySqlTest extends MarketingAgentApplicationTests {

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        MySqlTestDatabases.register(registry, "app_context");
    }
}
