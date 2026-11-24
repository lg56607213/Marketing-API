# AWS 배포 (동시접속 소수 기준)

## 구성

```
사용자 ──HTTPS──> Cloudflare (marketing.onlycartalk.com)
                     │  터널(서버가 바깥으로 연결. 서버에 공개 포트 없음)
                     ▼
              EC2 t4g.small (서울)  ── Spring Boot + 화면(정적 파일)
                     │  같은 VPC 안에서만
                     ▼
              RDS MySQL 8.0 db.t4g.micro  ── 자동 백업 7일
```

| 항목 | 사양 | 월 비용(대략, 서울) |
|---|---|---|
| EC2 | t4g.small, Amazon Linux 2023 (ARM), gp3 20GB | 약 1.7만 원 |
| RDS | MySQL 8.0, db.t4g.micro, gp3 20GB, 단일 AZ | 약 2만 원 |
| 합계 | | 약 3.5~4만 원 (+ Claude API 사용량) |

## 1. AWS 계정 (처음 한 번, 사장님이 직접)

1. 계정을 만들고 **루트 계정에 MFA**(휴대폰 인증 앱)를 켭니다.
2. 오른쪽 위 리전을 **아시아 태평양(서울) ap-northeast-2** 로 바꿉니다.
3. **결제 > Budgets** 에서 월 50달러 예산 알림을 만듭니다. 예상치 못한 요금을 바로 알 수 있습니다.

## 2. EC2 만들기

**EC2 > 인스턴스 시작**

| 항목 | 값 |
|---|---|
| 이름 | `marketing-api` |
| AMI | Amazon Linux 2023 (64비트 **Arm**) |
| 인스턴스 유형 | `t4g.small` |
| 키 페어 | 새로 만들기 → 이름 `marketing-key`, 유형 RSA, 형식 `.pem` → **다운로드한 파일을 잘 보관** |
| 네트워크 | 보안 그룹 생성: **SSH(22) 소스 = 내 IP** 만. HTTP/HTTPS 는 열지 않음 |
| 스토리지 | 20 GiB, gp3 |

만든 뒤 **탄력적 IP** 를 하나 할당해 이 인스턴스에 연결합니다(재시작해도 IP 가 바뀌지 않게).

## 3. RDS MySQL 만들기

**RDS > 데이터베이스 생성 > 표준 생성**

| 항목 | 값 |
|---|---|
| 엔진 | MySQL, 버전 **8.0.x** (테스트한 버전) |
| 템플릿 | 프리 티어(가능하면) 또는 개발/테스트 |
| DB 인스턴스 식별자 | `marketing-db` |
| 마스터 사용자 이름 | `marketing` |
| 마스터 암호 | 직접 입력(16자 이상). **server.env 에만 적고 다른 곳에 남기지 않기** |
| 인스턴스 클래스 | `db.t4g.micro` |
| 스토리지 | gp3 20 GiB, 자동 조정 끔 |
| 연결 | **EC2 컴퓨팅 리소스에 연결 → `marketing-api` 선택** (보안 그룹이 자동으로 EC2 만 허용) |
| 퍼블릭 액세스 | **아니요** |
| 추가 구성 > 초기 데이터베이스 이름 | `marketing_agent` |
| 백업 | 자동 백업 7일 |

만든 뒤 **엔드포인트**(예: `marketing-db.xxxx.ap-northeast-2.rds.amazonaws.com`)를 복사합니다.

## 4. Cloudflare 터널 토큰

**Cloudflare 대시보드 > Zero Trust > Networks > Tunnels > Create a tunnel**

1. 유형 Cloudflared, 이름 `marketing-aws`.
2. 설치 화면의 명령어에 나오는 **토큰**(긴 문자열)을 복사합니다. 설치 명령은 실행하지 않아도 됩니다. 배포 스크립트가 서버에서 실행합니다.
3. Public Hostname: `marketing.onlycartalk.com` → Service `HTTP` / `127.0.0.1:8080`.
   기존 PC 터널이 같은 주소를 쓰고 있으면, AWS 가 정상 동작하는 것을 확인한 뒤 기존 터널의 호스트 이름을 지웁니다.

## 5. 배포 (이 PC 에서)

```powershell
# 1) 처음 실행하면 deploy\aws\server.env 가 생기고 멈춘다
powershell -ExecutionPolicy Bypass -File deploy\aws\deploy.ps1 -ServerHost <탄력적IP> -Key C:\경로\marketing-key.pem -SetupServer

# 2) server.env 의 <...> 칸을 채운다: RDS 엔드포인트, 마스터 암호, Anthropic API 키, 터널 토큰

# 3) 다시 실행: 서버 준비 → 빌드 → 업로드 → 시작 → 상태 확인
powershell -ExecutionPolicy Bypass -File deploy\aws\deploy.ps1 -ServerHost <탄력적IP> -Key C:\경로\marketing-key.pem -SetupServer
```

이후 코드를 바꿨을 때는 `-SetupServer` 없이 실행하면 됩니다.
`HEALTH=200`, `TUNNEL=active` 가 나오면 https://marketing.onlycartalk.com 으로 접속됩니다.

## 운영 메모

- 로그: `ssh -i marketing-key.pem ec2-user@<IP> "sudo tail -f /opt/marketing/logs/app.log"`
- 재시작: `sudo systemctl restart marketing`
- 새 DB 는 비어 있습니다. 관리자(`admin` / server.env 의 `ADMIN_PASSWORD`)로 로그인해 업체·광고 계정을 다시 등록합니다.
  기존 PC(H2) 데이터를 옮겨야 하면 따로 요청하세요.
- `ADS_BID_DRY_RUN=true` 인 동안은 입찰가·예산을 네이버에 반영하지 않습니다. 충분히 지켜본 뒤 `false` 로 바꾸고 다시 배포합니다.
- DB 스키마는 Flyway 로 관리하고(`FLYWAY_ENABLED=true`), 엔티티와 어긋나면 기동 단계에서 바로 실패합니다(`JPA_DDL_AUTO=validate`).
