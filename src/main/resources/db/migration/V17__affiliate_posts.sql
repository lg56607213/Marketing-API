-- 브랜드커넥트(네이버 쇼핑 커넥트) 상품 블로그 글 초안.
-- 네이버 블로그 글쓰기 API 가 없어서 발행은 사용자가 복사·붙여넣기로 한다. 여기에는 초안만 둔다.
-- 사용자가 올린 사진은 서버에 저장하지 않는다(브라우저에만 있음). 설명(캡션)만 남긴다.
CREATE TABLE IF NOT EXISTS affiliate_posts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    created_by BIGINT NULL,
    product_name VARCHAR(200) NOT NULL,
    -- 브랜드커넥트에서 받은 판매 링크
    link_url VARCHAR(1000) NOT NULL,
    -- 네이버 쇼핑 검색으로 확인한 상품 정보(JSON). 못 찾으면 NULL
    product_json TEXT NULL,
    -- 사용자가 붙여 넣은 상품 설명·스펙
    product_notes TEXT NULL,
    -- 사용자가 직접 써 본 후기. 없으면 체험담을 쓰지 않는다
    review TEXT NULL,
    -- JSON 배열: 사용자가 올린 사진의 설명
    photo_captions TEXT NULL,
    -- 강조할 검색 키워드
    keyword VARCHAR(100) NULL,
    title VARCHAR(300) NULL,
    -- JSON: {title, tags[], sections[{heading, body, photo}]}
    post_json MEDIUMTEXT NULL,
    -- WRITING | DONE | FAILED
    status VARCHAR(20) NOT NULL,
    error VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_affiliate_posts_company ON affiliate_posts (company_id, id);
