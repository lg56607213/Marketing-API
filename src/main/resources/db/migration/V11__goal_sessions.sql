-- 목표 상담: 대화로 파악한 사업 구조, 계산·검토 결과, 사용자가 알려 준 실제 계약 결과. 모두 광고 계정 단위.
CREATE TABLE IF NOT EXISTS goal_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ad_account_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    model_json TEXT NOT NULL,
    analysis_json MEDIUMTEXT NULL,
    confirmed_option VARCHAR(20) NULL,
    confirmed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX ix_goal_sessions_account ON goal_sessions (ad_account_id, id);

CREATE TABLE IF NOT EXISTS goal_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ad_account_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX ix_goal_messages_session ON goal_messages (session_id, id);

CREATE TABLE IF NOT EXISTS goal_checkins (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ad_account_id BIGINT NOT NULL,
    since_date DATE NOT NULL,
    until_date DATE NOT NULL,
    leads BIGINT NOT NULL,
    leads_source VARCHAR(10) NOT NULL,
    deals BIGINT NOT NULL,
    revenue BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX ix_goal_checkins_account ON goal_checkins (ad_account_id, id);
