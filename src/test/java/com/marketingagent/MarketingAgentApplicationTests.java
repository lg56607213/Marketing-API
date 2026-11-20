package com.marketingagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** 개발용 파일 DB(./data)를 건드리지 않도록 메모리 DB 로 띄운다. */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:contextloads;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class MarketingAgentApplicationTests {
    @Test
    void contextLoads() {
    }
}
