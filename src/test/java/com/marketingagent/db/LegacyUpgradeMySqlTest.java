package com.marketingagent.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketingagent.DataInitializer;
import com.marketingagent.support.MySqlTestDatabases;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * V5 시점 데이터가 있는 MySQL 에서 애플리케이션을 띄운다. 기동하면서 Spring 의 Flyway 가 V6~최신을 적용하고,
 * Hibernate validate 를 통과한 뒤 DataInitializer 가 소속 없는 데이터를 기본 업체·기본 광고 계정으로 옮겨야 한다.
 */
@Tag("mysql")
@SpringBootTest(properties = {
        "naver.searchad.provider=stub",
        "ai.provider=stub"
})
class LegacyUpgradeMySqlTest {

    @DynamicPropertySource
    static void legacyDatabase(DynamicPropertyRegistry registry) {
        String url = MySqlTestDatabases.newDatabase("legacy_upgrade");
        FlywayMySqlMigrationTest.flyway(url, "5").migrate();
        LegacyV5Data.insert(new JdbcTemplate(FlywayMySqlMigrationTest.dataSource(url)));
        MySqlTestDatabases.registerUrl(registry, url);
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataInitializer dataInitializer;

    @Test
    void legacyRowsAreAdoptedByDefaultCompanyAndAccount() {
        Long companyId = jdbc.queryForObject(
                "select company_id from users where email = 'admin@marketing.local'", Long.class);
        Long accountId = jdbc.queryForObject(
                "select id from ad_accounts where company_id = ? order by id limit 1", Long.class, companyId);
        assertThat(companyId).isNotNull();
        assertThat(accountId).isNotNull();

        assertThat(jdbc.queryForObject("select company_id from users where email = ?", Long.class, LegacyV5Data.LEGACY_EMAIL))
                .isEqualTo(companyId);
        assertThat(jdbc.queryForObject("select company_id from brands where id = 200", Long.class)).isEqualTo(companyId);

        for (String table : new String[] {"ad_campaigns", "ad_groups", "ad_keywords", "keyword_stat_daily",
                "search_query_daily", "bid_recommendations", "ad_reports"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where ad_account_id is null", Long.class))
                    .as(table + " 에 소속 없는 행").isZero();
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where ad_account_id = ?", Long.class, accountId))
                    .as(table + " 이 기본 계정으로 옮겨졌는지").isPositive();
        }

        // 콘텐츠·승인과 4바이트 문자(이모지)가 그대로 남는다.
        assertThat(jdbc.queryForObject("select body from contents where id = 300", String.class)).contains("😀");
        assertThat(jdbc.queryForObject("select count(*) from approvals where content_id = 300", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from keyword_stat_daily", Long.class)).isEqualTo(2);
    }

    /** 재기동해도 업체·계정이 또 생기거나 데이터가 다시 옮겨지지 않는다. */
    @Test
    void restartingDoesNotDuplicateCompanyOrAccount() {
        long companies = jdbc.queryForObject("select count(*) from companies", Long.class);
        long accounts = jdbc.queryForObject("select count(*) from ad_accounts", Long.class);

        dataInitializer.run(new DefaultApplicationArguments());

        assertThat(jdbc.queryForObject("select count(*) from companies", Long.class)).isEqualTo(companies);
        assertThat(jdbc.queryForObject("select count(*) from ad_accounts", Long.class)).isEqualTo(accounts);
    }
}
