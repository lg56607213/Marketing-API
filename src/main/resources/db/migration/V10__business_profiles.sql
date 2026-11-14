CREATE TABLE IF NOT EXISTS business_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    homepage_url VARCHAR(300) NOT NULL,
    business_description TEXT NOT NULL,
    keywords VARCHAR(300) NOT NULL,
    monthly_budget BIGINT NOT NULL,
    target_conversions BIGINT NOT NULL,
    sale_value BIGINT NULL,
    site_monthly_visitors BIGINT NULL,
    site_monthly_conversions BIGINT NULL,
    proposal_json MEDIUMTEXT NULL,
    selected_option VARCHAR(20) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_business_profiles_owner UNIQUE (owner_id)
);
