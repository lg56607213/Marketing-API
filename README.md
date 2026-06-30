# AI Marketing Agent

Java 21, Spring Boot 3, JPA 기반의 AI 마케팅 콘텐츠 생성 API 초기 구현입니다.

## 핵심 구조

- Brand: 브랜드별 톤앤매너, CTA, 금지/허용 표현, SEO 규칙 관리
- Prompt: 브랜드와 콘텐츠 유형별 프롬프트 DB 관리
- Template: 콘텐츠 유형별 템플릿 DB 관리
- Content: 생성 글 Draft 저장, 승인 후 게시 가능
- Approval: 승인/게시 이력 저장
- AI Provider: OpenAI와 Stub Provider 교체 가능

## 주요 API

- POST /api/content/generate
- POST /api/content/rewrite
- POST /api/content/approve
- POST /api/content/publish
- GET /api/content
- GET /api/brand
- GET /api/template
- GET /api/prompt

## 실행

로컬 기본값은 H2 메모리 DB와 Stub AI Provider입니다.

```bash
./gradlew bootRun
```

OpenAI를 사용할 때는 환경변수를 지정합니다.

```bash
AI_PROVIDER=openai OPENAI_API_KEY=your_key ./gradlew bootRun
```

MySQL 사용 예시입니다.

```bash
DB_URL=jdbc:mysql://localhost:3306/marketing_agent DB_USERNAME=root DB_PASSWORD=password DB_DRIVER=com.mysql.cj.jdbc.Driver JPA_DDL_AUTO=update ./gradlew bootRun
```

Swagger UI: http://localhost:8080/swagger-ui.html
