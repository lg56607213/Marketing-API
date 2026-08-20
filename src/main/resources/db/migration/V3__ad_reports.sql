CREATE TABLE IF NOT EXISTS ad_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    analysis_since DATE NOT NULL,
    analysis_until DATE NOT NULL,
    generated_by VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);
