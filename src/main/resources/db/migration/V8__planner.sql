CREATE TABLE IF NOT EXISTS marketing_plans (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    goal_type VARCHAR(20) NOT NULL,
    monthly_budget BIGINT NOT NULL,
    target_conversions BIGINT NOT NULL,
    target_cpa BIGINT NOT NULL,
    proposal_json TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX ix_marketing_plans_owner ON marketing_plans (owner_id, id);

CREATE TABLE IF NOT EXISTS briefings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    period VARCHAR(10) NOT NULL,
    since_date DATE NOT NULL,
    until_date DATE NOT NULL,
    verdict VARCHAR(20) NOT NULL,
    headline VARCHAR(300) NOT NULL,
    content_json TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_briefings UNIQUE (owner_id, period, until_date)
);
