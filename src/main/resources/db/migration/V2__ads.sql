CREATE TABLE IF NOT EXISTS ad_campaigns (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ncc_campaign_id VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    campaign_tp VARCHAR(50),
    status VARCHAR(50),
    daily_budget BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ad_groups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ncc_adgroup_id VARCHAR(100) NOT NULL UNIQUE,
    ncc_campaign_id VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50),
    bid_amt BIGINT,
    daily_budget BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ad_keywords (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ncc_keyword_id VARCHAR(100) NOT NULL UNIQUE,
    ncc_adgroup_id VARCHAR(100) NOT NULL,
    keyword VARCHAR(255) NOT NULL,
    status VARCHAR(50),
    bid_amt BIGINT,
    use_group_bid_amt BIT(1),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS keyword_stat_daily (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ncc_keyword_id VARCHAR(100) NOT NULL,
    stat_date DATE NOT NULL,
    imp_cnt BIGINT NOT NULL DEFAULT 0,
    clk_cnt BIGINT NOT NULL DEFAULT 0,
    sales_amt BIGINT NOT NULL DEFAULT 0,
    ctr DOUBLE NOT NULL DEFAULT 0,
    cpc DOUBLE NOT NULL DEFAULT 0,
    avg_rnk DOUBLE NOT NULL DEFAULT 0,
    ccnt BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_keyword_stat_daily UNIQUE (ncc_keyword_id, stat_date)
);

CREATE TABLE IF NOT EXISTS bid_recommendations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ncc_keyword_id VARCHAR(100) NOT NULL,
    keyword VARCHAR(255) NOT NULL,
    current_bid BIGINT NOT NULL,
    recommended_bid BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    analysis_since DATE NOT NULL,
    analysis_until DATE NOT NULL,
    imp_cnt BIGINT NOT NULL DEFAULT 0,
    clk_cnt BIGINT NOT NULL DEFAULT 0,
    sales_amt BIGINT NOT NULL DEFAULT 0,
    ccnt BIGINT NOT NULL DEFAULT 0,
    ctr DOUBLE NOT NULL DEFAULT 0,
    avg_rnk DOUBLE NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    applied_bid BIGINT,
    decided_at DATETIME,
    decided_by BIGINT,
    result_message VARCHAR(500),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);
