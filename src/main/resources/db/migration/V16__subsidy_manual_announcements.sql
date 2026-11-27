-- 회원이 직접 등록하는 지원사업(수집 대상이 아닌 공고, 신보·기보 상품 등).
-- company_id 가 NULL 이면 모두에게 보이는 공고(수집 공고·관리자 공용 등록), 값이 있으면 그 업체만 본다.
ALTER TABLE subsidy_announcements ADD COLUMN company_id BIGINT NULL;
ALTER TABLE subsidy_announcements ADD COLUMN created_by BIGINT NULL;
CREATE INDEX ix_subsidy_announcements_company ON subsidy_announcements (company_id);
