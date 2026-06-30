CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS brands (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    tone_and_manner TEXT,
    cta TEXT,
    forbidden_words TEXT,
    allowed_words TEXT,
    seo_rules TEXT,
    owner_id BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS templates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    brand_id BIGINT,
    content_type VARCHAR(50) NOT NULL,
    body TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_template_brand FOREIGN KEY (brand_id) REFERENCES brands (id)
);

CREATE TABLE IF NOT EXISTS prompt_templates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    brand_id BIGINT,
    content_type VARCHAR(50) NOT NULL,
    system_prompt TEXT NOT NULL,
    user_prompt_template TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_prompt_brand FOREIGN KEY (brand_id) REFERENCES brands (id)
);

CREATE TABLE IF NOT EXISTS contents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    brand_id BIGINT NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    title VARCHAR(255),
    topic VARCHAR(500),
    body TEXT NOT NULL,
    ai_model VARCHAR(100),
    author_id BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_content_brand FOREIGN KEY (brand_id) REFERENCES brands (id)
);

CREATE TABLE IF NOT EXISTS approvals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content_id BIGINT NOT NULL,
    actor_id BIGINT,
    action VARCHAR(20) NOT NULL,
    comment TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_approval_content FOREIGN KEY (content_id) REFERENCES contents (id)
);
