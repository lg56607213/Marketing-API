-- 회원별 네이버 검색광고 계정 연결과 광고 데이터 분리.
-- 기존 데이터는 계정이 하나뿐이던 시절의 것이므로 가장 먼저 만들어진 관리자에게 넘긴다.

CREATE TABLE IF NOT EXISTS naver_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    customer_id VARCHAR(50) NOT NULL,
    api_key_enc VARCHAR(1000) NOT NULL,
    secret_key_enc VARCHAR(1000) NOT NULL,
    api_key_tail VARCHAR(10) NOT NULL,
    connected_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_naver_accounts_user UNIQUE (user_id)
);

ALTER TABLE ad_campaigns ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ad_groups ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ad_keywords ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE keyword_stat_daily ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE bid_recommendations ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ad_reports ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE search_query_daily ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE keyword_stat_hourly ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE keyword_conversion_hourly ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 0;

SET @first_admin = COALESCE((SELECT MIN(id) FROM users WHERE role = 'ADMIN'), 0);

UPDATE ad_campaigns SET owner_id = @first_admin;
UPDATE ad_groups SET owner_id = @first_admin;
UPDATE ad_keywords SET owner_id = @first_admin;
UPDATE keyword_stat_daily SET owner_id = @first_admin;
UPDATE bid_recommendations SET owner_id = @first_admin;
UPDATE ad_reports SET owner_id = @first_admin;
UPDATE search_query_daily SET owner_id = @first_admin;
UPDATE keyword_stat_hourly SET owner_id = @first_admin;
UPDATE keyword_conversion_hourly SET owner_id = @first_admin;

-- 유일 조건에 소유자를 넣는다. 서로 다른 회원이 같은 네이버 ID 를 가질 수 있다.
ALTER TABLE ad_campaigns DROP INDEX ncc_campaign_id;
ALTER TABLE ad_campaigns ADD CONSTRAINT uk_ad_campaigns_owner UNIQUE (owner_id, ncc_campaign_id);

ALTER TABLE ad_groups DROP INDEX ncc_adgroup_id;
ALTER TABLE ad_groups ADD CONSTRAINT uk_ad_groups_owner UNIQUE (owner_id, ncc_adgroup_id);

ALTER TABLE ad_keywords DROP INDEX ncc_keyword_id;
ALTER TABLE ad_keywords ADD CONSTRAINT uk_ad_keywords_owner UNIQUE (owner_id, ncc_keyword_id);

ALTER TABLE keyword_stat_daily DROP INDEX uk_keyword_stat_daily;
ALTER TABLE keyword_stat_daily
    ADD CONSTRAINT uk_keyword_stat_daily UNIQUE (owner_id, ncc_keyword_id, stat_date);

ALTER TABLE search_query_daily DROP INDEX uk_search_query_daily;
ALTER TABLE search_query_daily
    ADD CONSTRAINT uk_search_query_daily UNIQUE (owner_id, stat_date, ncc_adgroup_id, search_query, device);

ALTER TABLE keyword_stat_hourly DROP INDEX uk_keyword_stat_hourly;
ALTER TABLE keyword_stat_hourly
    ADD CONSTRAINT uk_keyword_stat_hourly
        UNIQUE (owner_id, stat_date, stat_hour, ncc_adgroup_id, ncc_keyword_id, device);

ALTER TABLE keyword_conversion_hourly DROP INDEX uk_keyword_conversion_hourly;
ALTER TABLE keyword_conversion_hourly
    ADD CONSTRAINT uk_keyword_conversion_hourly
        UNIQUE (owner_id, stat_date, stat_hour, ncc_adgroup_id, ncc_keyword_id, device, conv_method, conv_type);
