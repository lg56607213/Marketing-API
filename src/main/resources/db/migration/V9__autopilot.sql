CREATE TABLE IF NOT EXISTS autopilot_settings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    enabled BIT NOT NULL DEFAULT 0,
    target_cpa BIGINT NOT NULL DEFAULT 0,
    target_roas_pct DOUBLE NOT NULL DEFAULT 0,
    monthly_budget_cap BIGINT NOT NULL DEFAULT 0,
    manage_bids BIT NOT NULL DEFAULT 1,
    manage_budget BIT NOT NULL DEFAULT 1,
    pause_wasteful BIT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_autopilot_settings_owner UNIQUE (owner_id)
);

CREATE TABLE IF NOT EXISTS autopilot_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    run_date DATE NOT NULL,
    preview BIT NOT NULL,
    dry_run BIT NOT NULL,
    summary VARCHAR(500) NOT NULL,
    content_json TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX ix_autopilot_runs_owner ON autopilot_runs (owner_id, id);
