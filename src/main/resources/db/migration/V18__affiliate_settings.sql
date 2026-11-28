-- 업체별 네이버 개발자센터 검색 API 키(브랜드커넥트 글의 상품 정보 확인용).
-- Client Secret 은 SecretCipher(AES-256-GCM)로 암호화해 둔다. 화면에는 다시 보여 주지 않는다.
CREATE TABLE IF NOT EXISTS affiliate_settings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    naver_client_id VARCHAR(100) NOT NULL,
    naver_client_secret_enc VARCHAR(500) NOT NULL,
    verified_at DATETIME NULL,
    updated_by BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_affiliate_settings_company UNIQUE (company_id)
);
