CREATE TABLE IF NOT EXISTS search_query_daily (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stat_date DATE NOT NULL,
    ncc_campaign_id VARCHAR(100) NOT NULL,
    ncc_adgroup_id VARCHAR(100) NOT NULL,
    search_query VARCHAR(255) NOT NULL,
    device VARCHAR(10) NOT NULL,
    imp_cnt BIGINT NOT NULL DEFAULT 0,
    clk_cnt BIGINT NOT NULL DEFAULT 0,
    sales_amt BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_search_query_daily UNIQUE (stat_date, ncc_adgroup_id, search_query, device)
);
