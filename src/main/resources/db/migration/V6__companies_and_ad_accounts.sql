-- 여러 업체가 함께 쓰도록 업체(companies)와 광고 계정(ad_accounts)을 도입한다.
-- 기존 행의 company_id / ad_account_id 는 비워 두고, 애플리케이션이 첫 기동 때
-- 기본 업체와 기본 광고 계정으로 옮긴다 (DataInitializer).

CREATE TABLE IF NOT EXISTS companies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

ALTER TABLE users ADD COLUMN company_id BIGINT NULL;
ALTER TABLE brands ADD COLUMN company_id BIGINT NULL;
CREATE INDEX idx_users_company ON users (company_id);
CREATE INDEX idx_brands_company ON brands (company_id);

CREATE TABLE IF NOT EXISTS ad_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    customer_id VARCHAR(50),
    api_key VARCHAR(200),
    secret_key_enc VARCHAR(500),
    target_cpa BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ad_accounts_company_name UNIQUE (company_id, name)
);

-- 캠페인: 계정 구분 + 캠페인별 목표 CPA. ncc ID 는 이제 계정 안에서만 유일하다.
ALTER TABLE ad_campaigns
    ADD COLUMN ad_account_id BIGINT NULL,
    ADD COLUMN target_cpa BIGINT NULL;
ALTER TABLE ad_campaigns DROP INDEX ncc_campaign_id;
ALTER TABLE ad_campaigns
    ADD CONSTRAINT uk_ad_campaigns_account UNIQUE (ad_account_id, ncc_campaign_id);

ALTER TABLE ad_groups ADD COLUMN ad_account_id BIGINT NULL;
ALTER TABLE ad_groups DROP INDEX ncc_adgroup_id;
ALTER TABLE ad_groups
    ADD CONSTRAINT uk_ad_groups_account UNIQUE (ad_account_id, ncc_adgroup_id);
CREATE INDEX idx_ad_groups_campaign ON ad_groups (ad_account_id, ncc_campaign_id);

ALTER TABLE ad_keywords ADD COLUMN ad_account_id BIGINT NULL;
ALTER TABLE ad_keywords DROP INDEX ncc_keyword_id;
ALTER TABLE ad_keywords
    ADD CONSTRAINT uk_ad_keywords_account UNIQUE (ad_account_id, ncc_keyword_id);

ALTER TABLE keyword_stat_daily ADD COLUMN ad_account_id BIGINT NULL;
ALTER TABLE keyword_stat_daily DROP INDEX uk_keyword_stat_daily;
ALTER TABLE keyword_stat_daily
    ADD CONSTRAINT uk_keyword_stat_daily_account UNIQUE (ad_account_id, ncc_keyword_id, stat_date);
CREATE INDEX idx_keyword_stat_daily_date ON keyword_stat_daily (ad_account_id, stat_date);

ALTER TABLE search_query_daily ADD COLUMN ad_account_id BIGINT NULL;
ALTER TABLE search_query_daily DROP INDEX uk_search_query_daily;
ALTER TABLE search_query_daily
    ADD CONSTRAINT uk_search_query_daily_account
        UNIQUE (ad_account_id, stat_date, ncc_adgroup_id, search_query, device);

ALTER TABLE bid_recommendations
    ADD COLUMN ad_account_id BIGINT NULL,
    ADD COLUMN ncc_campaign_id VARCHAR(100) NULL;
CREATE INDEX idx_bid_recommendations_account ON bid_recommendations (ad_account_id, status);

ALTER TABLE ad_reports
    ADD COLUMN ad_account_id BIGINT NULL,
    ADD COLUMN ncc_campaign_id VARCHAR(100) NULL;
CREATE INDEX idx_ad_reports_account ON ad_reports (ad_account_id);
