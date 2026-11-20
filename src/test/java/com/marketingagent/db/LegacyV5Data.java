package com.marketingagent.db;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V5 시점(업체·광고 계정 구분이 없던 때)의 예시 데이터. 사용자·브랜드·콘텐츠·승인과 광고 데이터 한 벌.
 * 값은 모두 가짜다.
 */
final class LegacyV5Data {

    static final String LEGACY_EMAIL = "legacy-user@example.test";
    static final String LEGACY_BRAND = "예전 브랜드";

    private LegacyV5Data() {
    }

    static void insert(JdbcTemplate jdbc) {
        String now = "'2025-01-15 10:00:00'";
        jdbc.update("insert into users (id, email, password, role, created_at, updated_at) values "
                + "(100, '" + LEGACY_EMAIL + "', '{noop}not-a-real-password', 'USER', " + now + ", " + now + ")");
        jdbc.update("insert into brands (id, name, description, owner_id, created_at, updated_at) values "
                + "(200, '" + LEGACY_BRAND + "', '업체 구분 전 브랜드', 100, " + now + ", " + now + ")");
        jdbc.update("insert into templates (id, name, brand_id, content_type, body, created_at, updated_at) values "
                + "(210, '예전 템플릿', 200, 'BLOG', '본문 {{topic}}', " + now + ", " + now + ")");
        jdbc.update("insert into prompt_templates (id, name, brand_id, content_type, system_prompt, user_prompt_template, "
                + "created_at, updated_at) values (220, '예전 프롬프트', 200, 'BLOG', '시스템', '사용자 {{topic}}', " + now + ", " + now + ")");
        jdbc.update("insert into contents (id, brand_id, content_type, status, title, body, author_id, created_at, updated_at) "
                + "values (300, 200, 'BLOG', 'DRAFT', '예전 글', '예전 본문 😀 이모지 포함', 100, " + now + ", " + now + ")");
        jdbc.update("insert into approvals (id, content_id, actor_id, action, comment, created_at, updated_at) values "
                + "(310, 300, 100, 'APPROVE', '좋아요', " + now + ", " + now + ")");

        jdbc.update("insert into ad_campaigns (id, ncc_campaign_id, name, campaign_tp, status, daily_budget, created_at, updated_at) "
                + "values (400, 'cmp-legacy-1', '예전 캠페인', 'WEB_SITE', 'ELIGIBLE', 30000, " + now + ", " + now + ")");
        jdbc.update("insert into ad_groups (id, ncc_adgroup_id, ncc_campaign_id, name, status, bid_amt, created_at, updated_at) "
                + "values (410, 'grp-legacy-1', 'cmp-legacy-1', '예전 그룹', 'ELIGIBLE', 500, " + now + ", " + now + ")");
        jdbc.update("insert into ad_keywords (id, ncc_keyword_id, ncc_adgroup_id, keyword, status, bid_amt, use_group_bid_amt, "
                + "created_at, updated_at) values (420, 'kwd-legacy-1', 'grp-legacy-1', '예전 키워드', 'ELIGIBLE', 700, b'0', "
                + now + ", " + now + ")");
        jdbc.update("insert into keyword_stat_daily (ncc_keyword_id, stat_date, imp_cnt, clk_cnt, sales_amt, ctr, cpc, avg_rnk, ccnt, "
                + "created_at, updated_at) values ('kwd-legacy-1', '2025-01-13', 1000, 20, 14000, 2.0, 700, 3.2, 1, " + now + ", " + now + "), "
                + "('kwd-legacy-1', '2025-01-14', 900, 18, 12600, 2.0, 700, 3.4, 0, " + now + ", " + now + ")");
        jdbc.update("insert into bid_recommendations (id, ncc_keyword_id, keyword, current_bid, recommended_bid, reason, "
                + "analysis_since, analysis_until, status, created_at, updated_at) values (430, 'kwd-legacy-1', '예전 키워드', "
                + "700, 650, '예전 추천', '2025-01-01', '2025-01-14', 'PENDING', " + now + ", " + now + ")");
        jdbc.update("insert into ad_reports (id, title, body, analysis_since, analysis_until, generated_by, created_at, updated_at) "
                + "values (440, '예전 보고서', '본문', '2025-01-01', '2025-01-14', 'stub', " + now + ", " + now + ")");
        jdbc.update("insert into search_query_daily (stat_date, ncc_campaign_id, ncc_adgroup_id, search_query, device, imp_cnt, "
                + "clk_cnt, sales_amt, created_at, updated_at) values ('2025-01-14', 'cmp-legacy-1', 'grp-legacy-1', '예전 검색어', "
                + "'PC', 50, 3, 2100, " + now + ", " + now + ")");
    }
}
