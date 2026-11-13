CREATE TABLE IF NOT EXISTS keyword_stat_hourly (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stat_date DATE NOT NULL,
    stat_hour INT NOT NULL,
    ncc_campaign_id VARCHAR(100) NOT NULL,
    ncc_adgroup_id VARCHAR(100) NOT NULL,
    ncc_keyword_id VARCHAR(100) NOT NULL,
    device VARCHAR(10) NOT NULL,
    imp_cnt BIGINT NOT NULL DEFAULT 0,
    clk_cnt BIGINT NOT NULL DEFAULT 0,
    sales_amt BIGINT NOT NULL DEFAULT 0,
    rnk_sum BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_keyword_stat_hourly UNIQUE (stat_date, stat_hour, ncc_adgroup_id, ncc_keyword_id, device)
);

CREATE INDEX ix_keyword_stat_hourly_keyword ON keyword_stat_hourly (ncc_keyword_id, stat_date);

CREATE TABLE IF NOT EXISTS keyword_conversion_hourly (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stat_date DATE NOT NULL,
    stat_hour INT NOT NULL,
    ncc_campaign_id VARCHAR(100) NOT NULL,
    ncc_adgroup_id VARCHAR(100) NOT NULL,
    ncc_keyword_id VARCHAR(100) NOT NULL,
    device VARCHAR(10) NOT NULL,
    conv_method INT NOT NULL,
    conv_type VARCHAR(50) NOT NULL,
    conv_cnt BIGINT NOT NULL DEFAULT 0,
    conv_amt BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_keyword_conversion_hourly
        UNIQUE (stat_date, stat_hour, ncc_adgroup_id, ncc_keyword_id, device, conv_method, conv_type)
);

CREATE INDEX ix_keyword_conversion_hourly_keyword ON keyword_conversion_hourly (ncc_keyword_id, stat_date);
