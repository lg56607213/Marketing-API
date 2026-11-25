-- 완전 일임 운영: 자동 운영이 맡을 수 있는 일을 늘리고, 매일 보고 메일 설정을 둔다.
-- 새 항목은 기본으로 켜 둔다. 자동 운영 자체(enabled)를 켠 계정만 움직이고, 서버가 dry-run 이면 기록만 남는다.
ALTER TABLE autopilot_settings ADD COLUMN add_keywords BIT NOT NULL DEFAULT 1;
ALTER TABLE autopilot_settings ADD COLUMN add_negatives BIT NOT NULL DEFAULT 1;
ALTER TABLE autopilot_settings ADD COLUMN cleanup_keywords BIT NOT NULL DEFAULT 1;
ALTER TABLE autopilot_settings ADD COLUMN realtime_bids BIT NOT NULL DEFAULT 1;
-- 실시간 입찰이 노리는 평균 노출 순위
ALTER TABLE autopilot_settings ADD COLUMN target_rank INT NOT NULL DEFAULT 3;
-- 클릭 1회에 낼 수 있는 최대 금액. 0 이면 목표 상담의 손익분기로 자동 계산한다
ALTER TABLE autopilot_settings ADD COLUMN max_cpc BIGINT NOT NULL DEFAULT 0;
-- 이 날짜까지는 판단만 하고 네이버에는 반영하지 않는다(지켜보기 기간)
ALTER TABLE autopilot_settings ADD COLUMN observe_until DATE NULL;

-- 매일 아침 보고 메일. 받는 사람은 업체가 화면에서 정한다.
CREATE TABLE IF NOT EXISTS report_settings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ad_account_id BIGINT NOT NULL,
    enabled BIT NOT NULL DEFAULT 1,
    recipients VARCHAR(1000) NOT NULL,
    last_sent_on DATE NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_report_settings_account UNIQUE (ad_account_id)
);
