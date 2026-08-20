-- MySQL 전환용 초기 설정.
-- 실행: mysql -u root -p < scripts/setup-mysql.sql
-- 비밀번호 'CHANGE_ME' 를 반드시 바꾼 뒤 실행하세요.

CREATE DATABASE IF NOT EXISTS marketing_agent
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'marketing'@'localhost' IDENTIFIED BY 'CHANGE_ME';
GRANT ALL PRIVILEGES ON marketing_agent.* TO 'marketing'@'localhost';
FLUSH PRIVILEGES;

SELECT '데이터베이스와 계정이 준비되었습니다.' AS result;
