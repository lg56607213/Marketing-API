package com.marketingagent.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketingagent.support.MySqlTestDatabases;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 엔티티가 기대하는 스키마(Hibernate 가 직접 만든 DB)와 Flyway 가 만든 DB 를 컬럼 단위로 한 번에 비교한다.
 * Hibernate validate 는 첫 불일치에서 멈추므로, 남은 차이를 모두 보려고 둔다.
 */
@Tag("mysql")
@SpringBootTest(properties = {
        "naver.searchad.provider=stub",
        "ai.provider=stub"
})
class SchemaDriftMySqlTest {

    private static String flywayDb;

    @DynamicPropertySource
    static void databases(DynamicPropertyRegistry registry) {
        String hibernateUrl = MySqlTestDatabases.newDatabase("schema_hibernate");
        registry.add("spring.datasource.url", () -> hibernateUrl);
        registry.add("spring.datasource.username", MySqlTestDatabases::username);
        registry.add("spring.datasource.password", MySqlTestDatabases::password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");

        flywayDb = "schema_flyway";
        FlywayMySqlMigrationTest.flyway(MySqlTestDatabases.newDatabase(flywayDb), null).migrate();
    }

    @org.springframework.beans.factory.annotation.Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywaySchemaMatchesWhatEntitiesExpect() {
        Map<String, Map<String, Object>> expected = columns("schema_hibernate");
        Map<String, Map<String, Object>> actual = columns(flywayDb);

        List<String> missing = new ArrayList<>();
        List<String> typeMismatch = new ArrayList<>();
        List<String> nullability = new ArrayList<>();
        expected.forEach((key, e) -> {
            Map<String, Object> a = actual.get(key);
            if (a == null) {
                missing.add(key + " (" + e.get("column_type") + ")");
                return;
            }
            // Hibernate 는 MySQL 에서 문자열 enum 을 ENUM 으로 만들지만 validate 는 VARCHAR 도 받는다.
            // 마이그레이션은 enum 값을 늘려도 스키마를 안 바꿔도 되게 VARCHAR 로 둔다(H2EnumColumnPatch 참고). 차이로 보지 않는다.
            boolean enumAsVarchar = "enum".equals(e.get("data_type")) && "varchar".equals(a.get("data_type"));
            if (!enumAsVarchar && !e.get("data_type").equals(a.get("data_type"))) {
                typeMismatch.add(key + ": 엔티티=" + e.get("column_type") + " / Flyway=" + a.get("column_type"));
            }
            if (!e.get("is_nullable").equals(a.get("is_nullable"))) {
                nullability.add(key + ": 엔티티 nullable=" + e.get("is_nullable") + " / Flyway nullable=" + a.get("is_nullable"));
            }
        });

        StringBuilder out = new StringBuilder("\n[SCHEMA-DRIFT] image=" + System.getProperty("mysql.image", "mysql:8.0.46") + "\n");
        out.append("[SCHEMA-DRIFT] 컬럼 없음 ").append(missing.size()).append("건\n");
        missing.forEach(m -> out.append("[SCHEMA-DRIFT]   MISSING ").append(m).append('\n'));
        out.append("[SCHEMA-DRIFT] 타입 다름 ").append(typeMismatch.size()).append("건\n");
        typeMismatch.forEach(m -> out.append("[SCHEMA-DRIFT]   TYPE ").append(m).append('\n'));
        out.append("[SCHEMA-DRIFT] NULL 허용 다름(참고) ").append(nullability.size()).append("건\n");
        nullability.forEach(m -> out.append("[SCHEMA-DRIFT]   NULL ").append(m).append('\n'));
        System.out.print(out);

        assertThat(missing).as("Flyway 스키마에 없는 엔티티 컬럼").isEmpty();
        assertThat(typeMismatch).as("엔티티와 Flyway 의 컬럼 타입 차이").isEmpty();
    }

    private Map<String, Map<String, Object>> columns(String schema) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        jdbc.queryForList("select table_name, column_name, data_type, column_type, is_nullable "
                        + "from information_schema.columns where table_schema = ? order by table_name, ordinal_position", schema)
                .forEach(row -> {
                    Map<String, Object> lower = new LinkedHashMap<>();
                    row.forEach((k, v) -> lower.put(k.toLowerCase(), v == null ? null : v.toString().toLowerCase()));
                    result.put(lower.get("table_name") + "." + lower.get("column_name"), lower);
                });
        return result;
    }
}
