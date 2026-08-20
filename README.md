# AI Marketing Agent

Java 21, Spring Boot 3, JPA 기반의 AI 마케팅 콘텐츠 생성 + 네이버 검색광고 성과 분석 API입니다.

## 핵심 구조

### 콘텐츠 생성
- Brand: 브랜드별 톤앤매너, CTA, 금지/허용 표현, SEO 규칙 관리
- Prompt: 브랜드와 콘텐츠 유형별 프롬프트 DB 관리
- Template: 콘텐츠 유형별 템플릿 DB 관리
- Content: 생성 글 Draft 저장, 승인 후 게시 가능
- Approval: 승인/게시 이력 저장
- AI Provider: OpenAI와 Stub Provider 교체 가능

### 검색광고 분석
- AdCampaign / AdGroup / AdKeyword: 네이버 검색광고 계정 구조 미러링
- KeywordStatDaily: 키워드 단위 일자별 성과 (노출·클릭·광고비·전환·평균순위)
- BidRecommendation: 입찰가 조정 추천과 승인 이력
- AdReport: 기간별 성과 분석 리포트
- SearchAdClient: 네이버 실제 API와 Stub 교체 가능

## 주요 API

### 콘텐츠
- POST /api/content/generate
- POST /api/content/rewrite
- POST /api/content/approve
- POST /api/content/publish
- GET /api/content
- GET /api/brand
- GET /api/template
- GET /api/prompt

### 검색광고 성과
- GET /api/ads/health — 연동 상태 자가진단
- POST /api/ads/sync?days=30 — 캠페인·키워드·성과 적재
- GET /api/ads/summary — 기간 요약 (노출·클릭·CTR·CPC·CVR·CPA)
- GET /api/ads/keywords — 키워드별 성과
- GET /api/ads/campaigns

### 입찰가 조정
- POST /api/ads/bids/recommend — 조정안 생성 (네이버에 쓰지 않음)
- GET /api/ads/bids — 승인 대기 목록
- GET /api/ads/bids/history — 처리 이력
- POST /api/ads/bids/{id}/approve — 승인 및 실제 반영
- POST /api/ads/bids/{id}/reject
- POST /api/ads/bids/approve — 일괄 승인

### 분석 리포트
- POST /api/ads/reports?days=7
- GET /api/ads/reports
- GET /api/ads/reports/{id}

## 실행

로컬 기본값은 H2 **파일** DB와 Stub Provider입니다. 별도 설정 없이 바로 실행되며,
서버를 재시작해도 데이터가 유지됩니다. DB 파일은 `data/marketing_agent.mv.db` 에 생성됩니다.

```bash
./gradlew bootRun
```

프론트엔드는 별도 터미널에서 실행합니다.

```bash
cd client
npm install
npm run dev
```

- 화면: http://localhost:3000 (기본 계정 `admin@marketing.local` / `admin1234`)
- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 콘솔: http://localhost:8080/h2-console

환경변수는 `.env.example` 을 참고하세요.

## 네이버 검색광고 실계정 연결

### 1. API 자격증명 발급

1. 네이버 검색광고 관리자(https://manage.searchad.naver.com) 접속
2. **도구 → API 사용 관리** 이동
3. 액세스 라이선스와 비밀키를 발급받고, 화면 상단의 고객 ID를 확인

발급되는 값은 3개입니다.

| 값 | 환경변수 | 요청 헤더 |
|---|---|---|
| 고객 ID | `NAVER_CUSTOMER_ID` | `X-Customer` |
| 액세스 라이선스 | `NAVER_API_KEY` | `X-API-KEY` |
| 비밀키 | `NAVER_SECRET_KEY` | 서명 생성에만 사용 |

요청마다 `{timestamp}.{method}.{path}` 를 비밀키로 HMAC-SHA256 서명해
Base64 인코딩한 값을 `X-Signature` 헤더에 넣습니다. 서명 원문의 path 에는 쿼리스트링을 포함하지 않습니다.

### 2. 연결 (첫 실행은 dry-run 권장)

```bash
NAVER_SEARCHAD_PROVIDER=naver \
NAVER_CUSTOMER_ID=고객ID \
NAVER_API_KEY=액세스라이선스 \
NAVER_SECRET_KEY=비밀키 \
ADS_BID_DRY_RUN=true \
./gradlew bootRun
```

`ADS_BID_DRY_RUN=true` 이면 입찰가를 승인해도 네이버에 반영하지 않고 기록만 남깁니다.
추천 품질을 확인한 뒤 `false` 로 바꾸세요.

### 3. 연결 확인

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/ads/health
```

`reachable: true` 와 캠페인 개수가 나오면 정상입니다. 실패하면 메시지에 원인이 표시됩니다.

### 4. 첫 동기화

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/ads/sync?days=30"
```

> **주의:** `/stats` 응답의 일자 필드명이 계정/응답 종류에 따라 다르게 내려오는 사례가 보고되어 있습니다.
> 알려진 후보(`statDt`, `dateTime`, `statDate`, `date`, `day`)를 순서대로 확인하고,
> 못 찾으면 원본 JSON을 WARN 로그로 남기도록 되어 있습니다. 첫 동기화 후 로그를 확인하세요.

전환 기반 입찰가 추천이 의미를 가지려면 사이트에 **네이버 전환추적 스크립트**가 설치되어 있어야 합니다.
전환 데이터가 없으면 CPA 대신 계정 평균 CTR 기준으로 추천이 생성됩니다.

## 입찰가 조정 정책

자동 조정은 하지 않습니다. 추천 생성과 실제 반영은 완전히 분리되어 있으며,
반영은 사용자가 승인한 건에 대해서만 일어납니다.

### 판단 기준

목표 CPA(`ADS_TARGET_CPA`, 0이면 계정 평균 CPA)를 기준으로 합니다.

| 상황 | 조정 |
|---|---|
| CPA ≤ 목표의 70% | +20% |
| CPA ≤ 목표 | +10% |
| CPA > 목표 | -10% |
| CPA > 목표의 1.5배 | -20% |
| 전환 0, 목표 CPA의 2배 이상 소진 | -30% |

평균순위가 2위 이내면 상향하지 않습니다. 이미 최상단이라 클릭당 비용만 오르기 때문입니다.

### 가드레일

승인해도 아래 한도는 넘길 수 없습니다.

- 1회 변동폭 ±20% (`ADS_BID_MAX_CHANGE_RATE`)
- 입찰가 하한 70원 / 상한 100,000원 (`ADS_BID_MIN` / `ADS_BID_MAX`)
- 하루 반영 최대 50건 (`ADS_BID_MAX_DAILY_APPLIES`)
- 노출 100회 미만 키워드는 추천에서 제외 (`ADS_BID_MIN_IMPRESSIONS`)
- 재분석 시 기존 대기 건은 SUPERSEDED 처리되어 오래된 근거로 승인되지 않음

## MySQL 전환

기본값은 H2 파일 모드입니다. MySQL로 옮기려면 다음 순서로 진행합니다.

### 1. 데이터베이스와 전용 계정 생성

`scripts/setup-mysql.sql` 의 `CHANGE_ME` 를 원하는 비밀번호로 바꾼 뒤 실행합니다.

```bash
mysql -u root -p < scripts/setup-mysql.sql
```

애플리케이션 전용 계정(`marketing`)에는 `marketing_agent` 스키마 권한만 부여됩니다.
같은 서버에 다른 데이터베이스가 있어도 접근할 수 없습니다.

### 2. 자격증명 파일 작성

비밀번호를 명령줄에 노출하지 않도록 `.env.local` 에 담아 둡니다.
이 파일은 `.gitignore` 에 포함되어 커밋되지 않습니다.

```bash
cat > .env.local <<'ENV'
export DB_URL="jdbc:mysql://localhost:3306/marketing_agent?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
export DB_USERNAME=marketing
export DB_PASSWORD='설정한비밀번호'
export DB_DRIVER=com.mysql.cj.jdbc.Driver
export FLYWAY_ENABLED=true
export JPA_DDL_AUTO=validate
ENV
```

### 3. 실행

```bash
source .env.local && ./gradlew bootRun
```

첫 실행 시 Flyway가 `V1__init.sql`(콘텐츠) → `V2__ads.sql`(검색광고) →
`V3__ad_reports.sql`(리포트) 순으로 적용합니다.
`JPA_DDL_AUTO=validate` 이므로 엔티티와 스키마가 어긋나면 기동 단계에서 즉시 실패합니다.

### 주의

H2에 쌓인 기존 데이터는 자동으로 넘어가지 않습니다. 옮겨야 할 데이터가 있다면
H2 콘솔에서 `SCRIPT TO 'dump.sql'` 로 내보낸 뒤 수동으로 반영하세요.
실계정 데이터를 쌓기 전에 전환하는 것이 순서상 유리합니다.

## 테스트

```bash
./gradlew test        # 백엔드
cd client && npm run build   # 프론트 타입체크 + 빌드
```
