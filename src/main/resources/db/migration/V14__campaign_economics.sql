-- 사이트(캠페인)마다 상품과 남는 돈이 달라 자동 운영 기준을 캠페인별로 둔다.
-- 비어 있으면 지금처럼 계정 기본값(목표 상담·자동 운영 설정)을 따른다.
-- 전환 1건으로 남는 돈. 이보다 비싸게 전환을 사면 손해
ALTER TABLE ad_campaigns ADD COLUMN break_even_cpa BIGINT NULL;
-- 클릭 1회 최대 금액. 있으면 손익분기 계산보다 먼저 쓴다
ALTER TABLE ad_campaigns ADD COLUMN max_cpc BIGINT NULL;
-- 키워드 판단에 쓰는 이 캠페인의 상품·고객 설명
ALTER TABLE ad_campaigns ADD COLUMN business_note VARCHAR(500) NULL;
