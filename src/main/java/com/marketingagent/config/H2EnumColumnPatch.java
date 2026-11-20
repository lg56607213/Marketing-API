package com.marketingagent.config;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 로컬 H2 파일 DB 전용 보정.
 *
 * <p>Hibernate 는 H2 에서 enum 필드를 값 목록이 박힌 ENUM 컬럼으로 만든다. 그래서 enum 에 값을 추가하면
 * 예전에 만들어진 DB 는 새 값을 저장하지 못한다(ddl-auto=update 는 컬럼 타입을 고치지 않는다).
 * 해당 컬럼을 VARCHAR 로 바꿔 둔다. MySQL 은 마이그레이션에서 처음부터 VARCHAR 라 대상이 아니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class H2EnumColumnPatch implements ApplicationRunner {

    private static final List<String[]> COLUMNS = List.of(
            new String[] {"bid_recommendations", "strategy"},
            new String[] {"bid_recommendations", "status"});

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            if (!"H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                return;
            }
        } catch (Exception e) {
            log.warn("DB 종류를 확인하지 못해 enum 컬럼 보정을 건너뜁니다: {}", e.getMessage());
            return;
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String[] column : COLUMNS) {
            try {
                String type = jdbc.queryForObject(
                        "select data_type from information_schema.columns "
                                + "where lower(table_name) = ? and lower(column_name) = ?",
                        String.class, column[0], column[1]);
                if ("ENUM".equalsIgnoreCase(type)) {
                    jdbc.execute("alter table " + column[0] + " alter column " + column[1]
                            + " set data type varchar(20)");
                    log.info("H2 enum 컬럼을 VARCHAR 로 바꿨습니다: {}.{}", column[0], column[1]);
                }
            } catch (Exception e) {
                log.warn("H2 enum 컬럼 보정 실패 {}.{}: {}", column[0], column[1], e.getMessage());
            }
        }
    }
}
