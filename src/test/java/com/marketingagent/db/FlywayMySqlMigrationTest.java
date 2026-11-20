package com.marketingagent.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketingagent.support.MySqlTestDatabases;
import java.util.Arrays;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 실제 MySQL 에서 Flyway 마이그레이션이 처음부터 끝까지, 그리고 V5 에서 최신까지 도는지 확인한다.
 * 실행 결과(버전·설명·상태·소요 시간)는 표준 출력에 남겨 보고에 쓴다.
 */
@Tag("mysql")
class FlywayMySqlMigrationTest {

    @Test
    void emptyDatabaseMigratesFromV1ToLatest() {
        String url = MySqlTestDatabases.newDatabase("flyway_empty");
        Flyway flyway = flyway(url, null);

        MigrateResult result = flyway.migrate();
        print("빈 DB → 최신", flyway);

        assertThat(result.success).isTrue();
        List<MigrationInfo> applied = Arrays.asList(flyway.info().applied());
        assertThat(applied).isNotEmpty().allMatch(m -> m.getState() == MigrationState.SUCCESS);
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(applied.get(0).getVersion().getVersion()).isEqualTo("1");
        assertThat(versionOf(flyway.info().current())).isEqualTo(latestVersion(flyway));
    }

    /** V5 까지만 올린 DB 에 업체 구분 이전 형태의 데이터를 넣고, 나머지를 적용해도 데이터가 남고 새 제약이 맞는지 본다. */
    @Test
    void upgradeFromV5KeepsLegacyRowsAndSwitchesUniqueKeysToPerAccount() {
        String url = MySqlTestDatabases.newDatabase("flyway_upgrade");
        Flyway toV5 = flyway(url, "5");
        assertThat(toV5.migrate().success).isTrue();
        assertThat(versionOf(toV5.info().current())).isEqualTo("5");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource(url));
        LegacyV5Data.insert(jdbc);

        Flyway rest = flyway(url, null);
        MigrateResult result = rest.migrate();
        print("V5 → 최신", rest);
        assertThat(result.success).isTrue();
        assertThat(result.migrations).extracting(m -> m.version).first().isEqualTo("6");
        assertThat(versionOf(rest.info().current())).isEqualTo(latestVersion(rest));

        // 예전 행은 그대로 있고, 새로 생긴 소속 컬럼은 비어 있다(애플리케이션이 첫 기동 때 채운다).
        assertThat(jdbc.queryForObject("select count(*) from users", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from users where company_id is null", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ad_campaigns where ad_account_id is null", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from keyword_stat_daily", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select strategy from bid_recommendations", String.class)).isEqualTo("PERFORMANCE");

        // ncc ID 유일 제약이 계정 단위로 바뀌었다: 다른 계정이면 같은 캠페인 ID 를 넣을 수 있고, 같은 계정이면 막힌다.
        jdbc.update("update ad_campaigns set ad_account_id = 1");
        jdbc.update("insert into ad_campaigns (ncc_campaign_id, name, ad_account_id, created_at, updated_at) "
                + "values ('cmp-legacy-1', '다른 계정 캠페인', 2, now(), now())");
        assertThatThrownBy(() -> jdbc.update(
                "insert into ad_campaigns (ncc_campaign_id, name, ad_account_id, created_at, updated_at) "
                        + "values ('cmp-legacy-1', '같은 계정 중복', 1, now(), now())"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    static Flyway flyway(String url, String target) {
        var config = Flyway.configure()
                .dataSource(url, MySqlTestDatabases.username(), MySqlTestDatabases.password())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    static DriverManagerDataSource dataSource(String url) {
        DriverManagerDataSource ds = new DriverManagerDataSource(url, MySqlTestDatabases.username(), MySqlTestDatabases.password());
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        return ds;
    }

    private static String latestVersion(Flyway flyway) {
        MigrationInfo[] all = flyway.info().all();
        return all[all.length - 1].getVersion().getVersion();
    }

    private static String versionOf(MigrationInfo info) {
        return info == null ? null : info.getVersion().getVersion();
    }

    static void print(String title, Flyway flyway) {
        StringBuilder out = new StringBuilder("\n[MYSQL-MIGRATION] ").append(title)
                .append(" (image=").append(System.getProperty("mysql.image", "mysql:8.0.46")).append(")\n");
        for (MigrationInfo m : flyway.info().all()) {
            out.append(String.format("[MYSQL-MIGRATION]   V%-3s %-32s %-8s %5d ms%n", m.getVersion(), m.getDescription(),
                    m.getState(), m.getExecutionTime() == null ? 0 : m.getExecutionTime()));
        }
        System.out.print(out);
    }
}
