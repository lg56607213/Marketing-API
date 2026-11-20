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

## 여러 업체 · 여러 광고 계정

- **업체(Company)**: 회원가입(`POST /api/auth/register`, `companyName` 포함)을 하면 새 업체가 만들어지고
  가입자가 그 업체의 관리자(ADMIN)가 됩니다. 관리자는 `POST /api/admin/users` 로 자기 업체에 사용자를 추가합니다.
- **격리**: 브랜드·콘텐츠·광고 계정·성과·입찰 추천·리포트는 모두 업체 단위로 나뉩니다.
  다른 업체의 ID 를 직접 넣어 호출해도 404 로 응답합니다.
- **광고 계정(AdAccount)**: 한 업체가 네이버 검색광고 계정을 여러 개 등록할 수 있습니다.
  화면의 「광고 계정 관리」 또는 `POST /api/ads/accounts` 로 고객 ID · 액세스 라이선스 · 비밀키를 넣습니다.
  비밀키는 `APP_ENCRYPTION_KEY` 로 암호화해 저장하고 응답으로 내보내지 않습니다.
- **캠페인 필터**: 광고 API 는 `accountId`(필수)와 `campaignId`(선택)를 받습니다.
  사이트별로 캠페인을 나눠 두면 성과·유입 검색어·입찰 추천·리포트를 사이트별로 볼 수 있습니다.
- **캠페인별 목표 CPA**: `PUT /api/ads/campaigns/{nccCampaignId}/target-cpa?accountId=&targetCpa=`.
  입찰가 추천은 캠페인 목표 → 계정 기본 목표 → `ADS_TARGET_CPA` → 그 캠페인의 실제 평균 CPA 순으로 기준을 찾고,
  CTR 비교도 계정 전체가 아니라 같은 캠페인 평균과 합니다.
- **기존 데이터**: 업체/계정 구분이 없던 데이터는 첫 기동 때 "기본 업체"와 기본 광고 계정으로 옮겨집니다.
  `NAVER_SEARCHAD_PROVIDER=naver` 와 자격증명 환경변수가 있으면 그 값으로 기본 광고 계정을 만들고,
  없으면 예시 데이터 계정을 만듭니다. 이후에는 환경변수가 아니라 등록된 계정을 씁니다.

## 목표 상담 (처음 쓰는 분은 여기부터)

광고 계정을 등록하면 키를 확인하고 최근 30일 성과를 가져온 뒤 **목표 상담** 화면으로 넘어갑니다.
상담은 광고 계정(회사)마다 따로 하며, 업종을 미리 정해 두지 않고 대화로 사업 구조를 파악합니다.

1. 업종, 돈 버는 방식(판매·대여 / 중개 수수료 / 정기 결제), 상품별 계약 1건 매출과 남는 돈
2. 문의 → 계약 비율, 고객 1명당 계약 횟수, 한 달 목표(매출·이익·계약·문의), 이익 중 광고비 비율, 월 상한, 핵심 키워드
3. 서버가 계산: `문의 1건의 가치 = 이익 × 계약 횟수 × 계약 비율`, `목표 CPA = 가치 × 광고비 비율`, 필요한 문의 수
4. 네이버 키워드 수요·예상 성과로 운영안(목표 달성 / 예산 상한 / 시험 운영) 비교 → 고르면 운영 계획·자동 운영·입찰 추천의 목표가 됨
5. 운영 중 실제 계약 수를 알려 주면 계약 비율을 실측으로 보정하고 새 목표 CPA 를 제안

모르는 값은 보수적인 가정으로 채우고 "가정"이라고 표시합니다. AI(`AI_PROVIDER`)를 켜면 자유롭게 대화하고,
끄면 정해진 질문 순서로 진행합니다. 어느 쪽이든 숫자는 서버가 계산하며 AI 는 질문과 답 읽기만 합니다.

- `GET /api/goals` · `POST /api/goals/start` · `POST /api/goals/messages`
- `PUT /api/goals/model` (직접 고치기) · `POST /api/goals/analyze` · `POST /api/goals/confirm`
- `POST /api/goals/checkins` (실제 결과) · `POST /api/goals/target/apply`

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

### 광고 계정
- GET /api/ads/accounts — 우리 업체의 광고 계정 목록
- POST /api/ads/accounts — 등록 (관리자). `provider: "stub"` 이면 예시 데이터 계정
- PUT /api/ads/accounts/{id} — 이름·기본 목표 CPA·자격증명 수정 (관리자)
- DELETE /api/ads/accounts/{id} — 계정과 쌓인 데이터 삭제 (관리자)

### 검색광고 성과
아래 광고 API 는 모두 `accountId` 가 필요하고, 조회·추천·리포트는 `campaignId` 로 범위를 좁힐 수 있습니다.

- GET /api/ads/health — 연동 상태 자가진단
- POST /api/ads/sync?days=30 — 캠페인·키워드·성과 적재
- GET /api/ads/summary — 기간 요약 (노출·클릭·CTR·CPC·CVR·CPA)
- GET /api/ads/keywords — 키워드별 성과
- GET /api/ads/campaigns

### 시간대별 성과 · 전환
네이버 대용량 보고서(AD_DETAIL, AD_CONVERSION_DETAIL)를 내려받아 적재한다.
- GET /api/ads/diagnostics — 실계정 연결 점검 (자격증명 → 계정 구조 → 성과 → 리포트 3종 형식). 광고 설정은 바꾸지 않음
- POST /api/ads/stat-reports/sync?days=7 — 시간대별 성과·전환 적재 (최대 31일)
- GET /api/ads/stat-reports/hourly?device=M — 하루 중 시간대별 성과 (전환·ROAS 포함)
- GET /api/ads/stat-reports/conversions — 키워드별 전환수·전환매출액·CPA·ROAS

`NAVER_SYNC_ENABLED=true` 이면 일 배치가 계정 구조·일자별 성과에 이어 시간대별 성과·전환·검색어 리포트까지
최근 `NAVER_REPORT_SYNC_DAYS`(기본 7)일치를 매일 다시 받는다. 간접전환이 며칠 뒤에 잡히기 때문이다.

### 업체 등록 · 예산 3안 제안
가입한 회원이 업체명·홈페이지·사업 내용·핵심 키워드·월 광고 가능 예산·월 목표 건수를 적으면,
홈페이지를 확인하고 예산 세 가지 안의 기대 효과를 비교해 돌려준다. 광고 계정은 바꾸지 않는다.
- GET /api/onboarding — 내 업체 정보와 마지막 제안
- POST /api/onboarding — 업체 정보 등록 후 제안 받기
- POST /api/onboarding/select?option=INCREASE|REQUESTED|DECREASE — 고른 안을 현재 운영 계획으로 저장

세 안은 같은 키워드 조사에 예산만 달리 적용한다: 증액(요청의 1.5배) / 요청대로 / 감액(요청의 0.6배).
안마다 예상 지출·클릭·전환(범위 포함)·전환 1건당 비용·추가 전환 1건의 값·매출·광고수익률을 내고,
목표를 채우는 안 중 가장 싼 것을 추천한다. 어느 안도 목표에 닿지 않으면 그렇게 알린다.

홈페이지 확인은 첫 화면을 한 번 열어 네이버 전환 추적·구글 애널리틱스·문의 양식이 보이는지만 본다.
내부망 주소는 열지 않는다(http/https 기본 포트, 공인 주소만).

키워드 검색량·입찰가 추정 같은 조사용 조회는 회원이 아직 네이버 계정을 연결하지 않았어도 되도록,
회원 계정이 없으면 기본 관리자 계정에 연결된 네이버 키로 대신 조회한다. 그 계정의 광고 데이터는 회원에게 보이지 않는다.
실계정 모드에서는 관리자 계정에 네이버 키가 연결돼 있어야 새 회원이 제안을 받을 수 있다.

### 시장 · 경쟁
네이버가 실제로 주는 수치만 쓴다. 고정 예시나 지어낸 숫자는 없다.
- GET /api/market/keywords?seeds= — 연관 키워드별 월 검색수(PC/모바일), 경쟁 정도, 1·3·5위 추정 입찰가, 기회 키워드. seeds 를 비우면 업체 정보의 키워드
- POST /api/market/competitors — 경쟁사 홈페이지(최대 3곳)를 내 홈페이지와 같은 기준으로 확인하고, 내 키워드의 자리 다툼 강도를 함께 돌려준다

경쟁사의 광고비·입찰가·키워드 목록은 네이버가 공개하지 않으므로 제공하지 않는다.
검색량의 시기별 변화와 뉴스는 네이버 개발자센터(데이터랩·뉴스 검색) 키가 있어야 해서 아직 없다.

콘텐츠 생성의 주제·키워드 추천도 이 API 로 내 업종의 실제 검색어를 보여 준다.
AI 를 연결하지 않으면() 글 대신 주제와 브랜드 이름만 넣은 틀을 "견본"이라고 표시해 돌려준다.

### 운영 계획 · 브리핑
직원 없이 광고를 운영하는 소기업을 위한 기능. 이 두 가지는 제안과 보고만 하고 광고 계정은 바꾸지 않는다.
- POST /api/ads/plans/propose — 월 목표 전환수 또는 월 예산을 주면 키워드·입찰가·예산·기대 성과(보수/기본/낙관)·운영 방법을 제안
- GET /api/ads/plans/current — 현재 계획
- POST /api/ads/briefings?period=DAILY|WEEKLY|MONTHLY — 일간·주간 브리핑, 월간 분석 보고서 생성 (같은 기간은 덮어씀)
- GET /api/ads/briefings — 최근 브리핑

계획의 숫자는 출처를 함께 낸다(`basis`: 실측 / 네이버 예측 / 가정).
- 클릭수·클릭 단가: 네이버 성과 예측(`/estimate/performance-bulk`)을 키워드마다 입찰가 4단계로 받아,
  추가 클릭이 가장 싼 단계부터 목표·예산에 닿을 때까지 고른다. 예측을 못 받으면 키워드도구 수치로 대체하고 그렇게 표시한다.
- 전환율: 내 광고의 최근 30일 실적 → 내 사이트 실측(`siteMonthlyVisitors`, `siteMonthlyConversions`) → 가정값 순.
  실측이면 90% 신뢰구간을 보수적/낙관적 시나리오로 쓴다.
- 수익성: `saleValue`(전환 1건 매출)와 `targetRoasPct`를 주면 수익이 남는 클릭 단가 한도를 계산해 그보다 비싼 클릭은 계획에 넣지 않는다.

브리핑은 지표 증감, 클릭률·전환율 평가, 이번 달 목표 대비 진행, 권장 조치와 함께
광고비 판단(더 써도 됨 / 유지 / 줄이기 / 판단 보류)을 근거 수치와 같이 낸다.
판단은 최근 14일을 기준으로 규칙으로 계산하며, AI 는 설정돼 있을 때 총평 한 문단만 덧붙인다.
브리핑에는 키워드별 광고비 대비 효율표(실제 클릭 단가, 쓸 수 있는 단가, 적정 입찰가)와 자동 운영이 그날 바꾼 내용이 함께 들어간다.
`NAVER_SYNC_ENABLED=true` 이면 일 배치가 수집 뒤에 일간 브리핑을, 월요일에는 주간 브리핑, 매월 1일에는 월간 보고서도 만든다.

### 자동 운영 · 효율 계산
회원이 직접 켜야만 동작한다(기본 꺼짐). 켜 두면 일 배치가 수집 뒤에 광고 계정을 한도 안에서 바꾸고 기록을 남긴다.
- GET /api/ads/autopilot — 설정과 지금 적용되는 목표·한도
- PUT /api/ads/autopilot — 켜기/끄기, 목표 전환당 비용·목표 광고수익률·월 광고비 한도, 맡길 범위
- POST /api/ads/autopilot/run?preview=true — 무엇을 할지 미리보기(아무것도 바꾸지 않음). `preview=false` 는 켜져 있을 때만 실행
- GET /api/ads/autopilot/runs — 실행 기록(무엇을 왜 바꿨는지)
- GET /api/ads/efficiency — 키워드별 효율과 적정 입찰가 계산 결과

입찰가 계산식:

```
클릭당 쓸 수 있는 돈 = 키워드 전환율 × 전환 1건에 쓸 수 있는 돈
전환 1건에 쓸 수 있는 돈 = 목표 전환당 비용, 또는 (전환 1건 매출 ÷ 목표 광고수익률)
적정 입찰가 = 현재 입찰가 × (클릭당 쓸 수 있는 돈 ÷ 실제 클릭 단가)
```

키워드 전환율은 클릭이 적을 때 우연에 흔들리므로 계정 평균을 가상 클릭 50회만큼 섞는다. 최근 28일 실적을 쓴다.

자동 운영이 하는 일과 한도:

| 항목 | 조건 | 한도 |
|---|---|---|
| 입찰가 | 키워드 클릭 20회 이상, 계정 전환 1건 이상 | 1회 ±20%, 70~100,000원, 하루 50건, 같은 키워드는 7일에 한 번 |
| 키워드 중지 | 클릭 30회 이상인데 전환 0건이고, 전환 1건에 쓸 수 있는 돈의 3배 이상 지출 | 한 번에 5개 |
| 일예산 인하 | 남은 월 한도 ÷ 남은 날수보다 일예산이 클 때 | 캠페인당 최저 1,000원 |
| 일예산 인상 | 최근 14일 전환당 비용이 목표의 80% 이하이고 일예산의 85% 이상을 쓸 때 | +20%, 월 한도 이내, 7일에 한 번 |

- 계정 클릭이 100회 미만이거나 전환이 잡히지 않으면 입찰가와 키워드는 건드리지 않는다.
- 월 한도를 다 써도 캠페인을 끄지는 않는다(일예산을 최저로 낮추고 기록에 경고를 남긴다).
- 키워드 추가, 제외 키워드, 광고 문구는 자동으로 바꾸지 않는다.
- `ADS_BID_DRY_RUN=true` 이면 자동 운영도 네이버에 쓰지 않고 기록만 남긴다. 실계정에서는 dry-run 으로 며칠 지켜본 뒤 끄기를 권한다.

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

| 값 | 요청 헤더 |
|---|---|
| 고객 ID | `X-Customer` |
| 액세스 라이선스 | `X-API-KEY` |
| 비밀키 | 서명 생성에만 사용 |

요청마다 `{timestamp}.{method}.{path}` 를 비밀키로 HMAC-SHA256 서명해
Base64 인코딩한 값을 `X-Signature` 헤더에 넣습니다. 서명 원문의 path 에는 쿼리스트링을 포함하지 않습니다.

### 2. 연결 (광고 계정별, 첫 실행은 dry-run 권장)

네이버 계정은 **업체 안에서 광고 계정마다** 등록합니다. 한 업체가 여러 회사의 광고 계정을 함께 운영할 수 있고,
계정마다 업종·상품·목표가 따로 저장됩니다.

```bash
APP_ENCRYPTION_KEY=길고_고유한_값 ADS_BID_DRY_RUN=true ./gradlew bootRun
```

로그인한 뒤 **광고 계정 관리**에서 위 3개 값을 넣어 계정을 추가합니다.
저장하기 전에 그 키로 네이버에 실제로 한 번 조회해 보고, 조회가 되지 않으면 저장하지 않습니다.
비밀키는 DB 에 암호화해 두고 응답으로 내보내지 않습니다. `APP_ENCRYPTION_KEY` 를 바꾸면 등록된 비밀키를 다시 넣어야 합니다.

- 캠페인·키워드·성과·검색어·입찰 추천·계획·브리핑·자동 운영은 모두 광고 계정 단위로 나뉩니다.
  다른 업체의 계정 ID 를 넣어도 조회되지 않습니다.
- 광고 API 는 모두 `accountId` 를 받습니다. 화면에서는 상단 계정 선택 막대의 값이 자동으로 붙습니다.
- 일 배치는 모든 업체의 실계정을 차례로 돌고, 한 계정이 실패해도 나머지는 계속합니다.
- 예전 방식(`NAVER_CUSTOMER_ID` / `NAVER_API_KEY` / `NAVER_SECRET_KEY` 환경변수)의 값이 남아 있으면
  첫 실행 때 기본 업체의 광고 계정으로 한 번 옮겨집니다.

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

추천 생성과 실제 반영은 분리되어 있으며, 반영은 사용자가 승인한 건이나
회원이 켜 둔 자동 운영이 같은 가드레일 안에서 승인한 건에 대해서만 일어납니다.

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

## 내 PC 에서 도메인으로 공개하기 (Cloudflare Tunnel)

도메인이 Cloudflare 에 있으면, 서버를 따로 빌리지 않고 이 PC 를 켜 둔 동안만 `https://marketing.내도메인` 으로 열 수 있습니다.
PC 를 끄거나 실행 창을 닫으면 그 주소만 닫히고, 같은 도메인의 다른 사이트에는 영향이 없습니다.

```powershell
winget install --id Cloudflare.cloudflared
powershell -ExecutionPolicy Bypass -File scripts\setup-tunnel.ps1 -Hostname marketing.내도메인
powershell -ExecutionPolicy Bypass -File scripts\run-public.ps1
```

- `setup-tunnel.ps1` 은 처음 한 번만 실행합니다. 브라우저에서 Cloudflare 로그인을 승인하면 터널과 DNS 레코드 한 줄이 만들어집니다.
- `run-public.ps1` 은 화면을 빌드해 서버와 같은 포트(8090)로 내보내고 터널을 연결합니다. 코드를 바꾼 뒤에는 `-Rebuild` 를 붙입니다.
- 처음 실행할 때 `public.env` 가 만들어집니다. 무작위 비밀값과 관리자 비밀번호(아이디 `admin`)가 들어 있고 저장소에는 올라가지 않습니다.
- 공개 모드에서는 H2 콘솔과 Swagger 가 꺼지고, 기본 비밀값이 남아 있으면 서버가 뜨지 않으며, 서버는 이 PC 안(127.0.0.1)에서만 받습니다.
- 회원가입은 기본으로 막혀 있습니다. 다른 사람을 받으려면 `public.env` 의 `APP_SIGNUP_ENABLED` 를 `true` 로 바꿉니다.
- 데이터는 `data-public/` 에 따로 쌓입니다. 일 배치(오전 5시 30분)는 그 시각에 PC 가 켜져 있어야 돕니다.

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
`V3__ad_reports.sql`(리포트) → `V4`(입찰 전략) → `V5`(유입 검색어) →
`V6__companies_and_ad_accounts.sql`(업체·광고 계정) → `V7`(시간대별 성과·전환) → `V8`(운영 계획·브리핑) →
`V9`(자동 운영) → `V10`(업체 정보) 순으로 적용합니다.
V6 이후는 H2 에서만 검증했고 실제 MySQL 에는 아직 적용해 보지 않았으니, 운영 DB 에 올리기 전에 사본으로 먼저 돌려 보세요.
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
