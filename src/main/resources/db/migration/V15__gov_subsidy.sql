-- 정부지원사업 자동 매칭: 사업체 · 공고 · 매칭 결과 · 작업 기록

-- 지원사업을 찾을 사업체. 한 업체(회원사)가 법인·개인 사업자를 여러 개 등록할 수 있다.
CREATE TABLE IF NOT EXISTS subsidy_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    -- 법인 | 개인 | 예비창업자
    entity_type VARCHAR(20) NOT NULL,
    founded_date DATE NULL,
    region_sido VARCHAR(20) NULL,
    region_sigungu VARCHAR(30) NULL,
    ksic_code VARCHAR(10) NULL,
    industry_name VARCHAR(100) NULL,
    revenue_krw BIGINT NULL,
    employees INT NULL,
    -- JSON 배열: 벤처 · 이노비즈 · 메인비즈 · 여성기업 · 장애인기업 · 사회적기업 등
    certifications VARCHAR(500) NULL,
    ceo_birth_year INT NULL,
    ceo_gender VARCHAR(10) NULL,
    -- 관심 분야·사업 설명. 매칭 키워드로 쓴다
    keywords VARCHAR(1000) NULL,
    email_opt_in BIT NOT NULL DEFAULT 1,
    -- 쉼표로 구분한 받는 사람. 비면 알림을 보내지 않는다
    recipients VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_subsidy_profiles_company ON subsidy_profiles (company_id);

-- 수집한 공고. 공고 1건당 AI 분석 1회(eligibility).
CREATE TABLE IF NOT EXISTS subsidy_announcements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(20) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    agency VARCHAR(200) NULL,
    summary TEXT NULL,
    target_text TEXT NULL,
    region_text VARCHAR(300) NULL,
    apply_start DATE NULL,
    apply_end DATE NULL,
    url VARCHAR(1000) NULL,
    raw_json MEDIUMTEXT NULL,
    -- 여러 소스에 같은 공고가 올라올 때 하나로 묶는 키(제목·기관·마감일 정규화)
    dedupe_key VARCHAR(200) NOT NULL,
    eligibility_json TEXT NULL,
    ai_summary TEXT NULL,
    analyzed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_subsidy_announcements_source UNIQUE (source, source_id)
);
CREATE INDEX ix_subsidy_announcements_dedupe ON subsidy_announcements (dedupe_key);
CREATE INDEX ix_subsidy_announcements_end ON subsidy_announcements (apply_end);

-- 사업체 × 공고 판정 결과
CREATE TABLE IF NOT EXISTS subsidy_matches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    announcement_id BIGINT NOT NULL,
    score INT NOT NULL,
    -- fit | maybe | no
    verdict VARCHAR(10) NOT NULL,
    reasons TEXT NULL,
    blockers TEXT NULL,
    ai_judged BIT NOT NULL DEFAULT 0,
    starred BIT NOT NULL DEFAULT 0,
    notified_at DATETIME NULL,
    deadline_notified_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_subsidy_matches UNIQUE (profile_id, announcement_id)
);

-- 수집 · 분석 · 매칭 · 발송 이력과 오류
CREATE TABLE IF NOT EXISTS subsidy_job_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job VARCHAR(30) NOT NULL,
    started_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    status VARCHAR(20) NOT NULL,
    detail TEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);
