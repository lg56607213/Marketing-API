package com.marketingagent.domain.goal;

import com.marketingagent.support.MySqlTestDatabases;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** GoalConversationTest 를 MySQL 에서 그대로 다시 돌린다: 목표 상담 전체 흐름과 업체 격리. Flyway 로 스키마를 만들고 Hibernate 는 validate 만 한다. */
@Tag("mysql")
class GoalConversationMySqlTest extends GoalConversationTest {

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        MySqlTestDatabases.register(registry, "goal_conversation");
    }
}
