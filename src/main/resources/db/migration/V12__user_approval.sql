-- 가입 승인과 최고 관리자.
-- status: PENDING(승인 대기) / ACTIVE(이용 중) / REJECTED(거절·이용 중지). 기존 계정은 모두 이용 중으로 둔다.
-- super_admin: 모든 업체의 가입 신청을 승인할 수 있는 운영자. 기본 관리자 계정에 애플리케이션이 지정한다.
ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
-- 엔티티의 boolean 과 맞추려고 BIT(1) 로 둔다(MySQL 의 BOOLEAN 은 TINYINT(1) 이라 스키마 검사에서 어긋난다).
ALTER TABLE users ADD COLUMN super_admin BIT(1) NOT NULL DEFAULT 0;

CREATE INDEX idx_users_status ON users (status);
