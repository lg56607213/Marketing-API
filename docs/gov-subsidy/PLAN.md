# 정부지원사업 자동 매칭 에이전트 — 실행 계획 (marketagent 모듈)

## 목표
회원이 사업체(법인·개인·예비창업자)를 등록하면 매일 아침 정부지원사업·정책자금 공고를 모아
사업체에 맞는 것을 골라 요약·추천한다. 정보 제공 서비스로만 운영한다(알선·대행·성공보수 기능 없음).

## 왜 marketagent 안에 만드나
- 회원·업체·권한, 매일 배치(@Scheduled), 메일 발송, 비밀값 암호화, AI 호출(Claude), 배포(EC2·Cloudflare Tunnel)가 이미 있다.
- 따로 만들면 이것들을 다시 만들어야 하고, 나중에 로그인을 합칠 때 회원 DB 이관이 필요하다.
- 외부 홍보용으로 별도 도메인이 필요하면, 같은 서버의 공개 화면(/subsidy)을 다른 도메인으로 연결하면 된다.

## 데이터 소스 (공식 API만, 화면 크롤링 없음)
| 소스 | 방식 | 키 |
|---|---|---|
| 기업마당 (중기부, 중진공·소진공·신보·기보 공고 대부분 포함) | `GET https://www.bizinfo.go.kr/uss/rss/bizinfoApi.do` (crtfcKey, dataType=json, pageIndex, pageUnit) | `BIZINFO_KEY` |
| K-Startup (창업진흥원, 공공데이터포털 15125364) | `apis.data.go.kr/B552735/...` — 활용신청 후 operation명 확인 | `DATA_GO_KR_KEY` |
| 판판대로 (공공데이터포털 15163956) | 활용신청 후 명세 확인, 3번째 수집기 | `DATA_GO_KR_KEY` |
| 신보·기보 상시 보증상품 | 시드 데이터 1회 등록 (특례·협약보증은 기업마당 공고로) | - |

응답 필드명은 키 발급 후 실제 1회 호출로 확인해 `src/test/resources/subsidy/fixtures/` 에 저장하고,
그 샘플로 normalize·매칭 테스트를 고정한다. 키가 없으면 수집기는 건너뛰고 기록만 남긴다.

## DB (Flyway V15)
- `subsidy_profiles`: 사업체 1건. company_id(소유 업체), name, entity_type(법인/개인/예비창업자), founded_date,
  region_sido, region_sigungu, ksic_code, industry_name, revenue_krw, employees, certifications(JSON),
  ceo_birth_year, ceo_gender, keywords, email_opt_in, recipients
- `subsidy_announcements`: source, source_id, title, agency, summary, target_text, region_text, apply_start,
  apply_end, url, raw(JSON), dedupe_key, eligibility(JSON), ai_summary, analyzed_at — UNIQUE(source, source_id)
- `subsidy_matches`: profile_id, announcement_id, score, verdict(fit/maybe/no), reasons, blockers, ai_judged,
  starred, notified_at — UNIQUE(profile_id, announcement_id)
- `subsidy_job_runs`: job, started_at, finished_at, status, detail

## 처리 흐름 (Asia/Seoul)
1. **08:30 수집**: 소스별로 접수중 공고만 받아 upsert. 같은 공고가 두 소스에 있으면 dedupe_key(제목·기관·마감일 정규화)로 묶어 한 번만 추천.
2. **08:30 분석**: 아직 분석 안 한 공고만, 공고 1건당 Claude 1회(`claude-haiku-5-5`)로 eligibility JSON 추출.
   하루 분석 상한 `SUBSIDY_MAX_ANALYZE_PER_DAY`(기본 200). 모르는 값은 null(추측 금지).
3. **09:00 매칭**: 규칙으로 먼저 판정 → 애매한(maybe) 상위 건만 Claude(`claude-sonnet-5-5`) 재판단,
   사업체당 하루 상한 `SUBSIDY_MAX_JUDGE_PER_PROFILE`(기본 5).
4. **09:00 알림**: 신규 적합 공고 + 마감 7일 이내를 메일로. 수신거부 링크 포함. 마감 3일 전 1회 재알림.

## 매칭 규칙
- 하드 탈락(no): 지역 불일치(전국 제외) · 업력 범위 밖 · 사업자 유형 불일치 · 제외업종 · 매출·인원 초과 ·
  필수인증 미보유 · 대표 나이 제한 초과 · 접수 마감 지남
- 점수: 기본 50, 대상업종·관심 키워드 일치 +20, 우대조건 충족마다 +5, 마감 7일 이내 표시
- null 이 많으면 maybe. 결과마다 reasons(왜 맞는지)·blockers(걸리는 조건)를 남긴다
- no 도 숨기지 않고 "조건 불일치"로 접어 보여 준다(AI 추출 오류로 맞는 공고가 사라지지 않게)
- 대부업·금융업은 중진공·소진공 정책자금 대부분에서 융자 제외 업종

## 화면 (marketagent)
- 사업체 등록·수정(여러 개), 추천 공고(점수순)·마감임박·조건 불일치 탭, 공고 요약·원문 링크·관심표시
- 관리자: 수집·분석·매칭 수동 실행, 작업 기록
- 하단 고지: "공고 원문 확인 필수, 매칭 결과는 참고용"

## 개인정보
- 대표 생년·성별 등은 매칭에만 쓰고 업체 소유자만 조회. 프로필 삭제 시 매칭 기록도 삭제.
- 외부 공개 전: 개인정보 처리방침·이용약관·수집 동의, 비밀번호 재설정, 회원 탈퇴 필요(2단계).

## 단계
1. (키 없이) DB·사업체 등록·매칭 규칙·테스트·수집기 골격·분석기·배치·화면·메일 섹션
2. (키 발급 후) 실제 응답 샘플 저장 → normalize 확정 → 수집 연결
3. 카카오 알림톡, 신보·기보 상품 시드, 외부 공개용 도메인·약관

## 사용자 준비물
- 기업마당 API 인증키, 공공데이터포털 K-Startup·판판대로 활용신청 → 서버 `app.env` 에 직접 입력
- (이미 있음) ANTHROPIC_API_KEY, 메일 발송 설정
