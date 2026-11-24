# 이 PC 에서 빌드해 AWS EC2 로 올리고 다시 시작한다.
#
#   처음 한 번:  powershell -ExecutionPolicy Bypass -File deploy\aws\deploy.ps1 -ServerHost <EC2 공인 IP> -Key <pem 경로> -SetupServer
#   그다음부터:  powershell -ExecutionPolicy Bypass -File deploy\aws\deploy.ps1 -ServerHost <EC2 공인 IP> -Key <pem 경로>
#
# 비밀값은 deploy\aws\server.env 에 둔다. 처음 실행하면 비밀값을 만들어 이 파일을 생성하고,
# RDS 주소·비밀번호 같은 빈칸을 채우라고 알려 준 뒤 멈춘다. 이 파일은 저장소에 올라가지 않는다(*.env).
param(
    [Parameter(Mandatory = $true)][string]$ServerHost,
    [Parameter(Mandatory = $true)][string]$Key,
    [string]$User = "ec2-user",
    [switch]$SetupServer,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
$root = Split-Path -Parent (Split-Path -Parent $here)
Set-Location $root
$envFile = Join-Path $here "server.env"

function New-Secret([int]$bytes) {
    $buffer = New-Object byte[] $bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($buffer)
    $rng.Dispose()
    return ([Convert]::ToBase64String($buffer) -replace '[+/=]', '')
}

function Read-EnvValue([string]$file, [string]$name) {
    if (-not (Test-Path $file)) { return $null }
    foreach ($line in (Get-Content $file -Encoding UTF8)) {
        if ($line.StartsWith("$name=")) { return $line.Substring($name.Length + 1) }
    }
    return $null
}

# 1. 운영 설정 파일
if (-not (Test-Path $envFile)) {
    $admin = Read-EnvValue (Join-Path $root "public.env") "ADMIN_PASSWORD"
    if (-not $admin) { $admin = New-Secret 18 }
    $lines = @(
        "# AWS 운영 설정. 남에게 주지 말 것. 저장소에 올라가지 않는다(*.env).",
        "# JWT_SECRET 을 바꾸면 모두 다시 로그인해야 하고, APP_ENCRYPTION_KEY 를 바꾸면 등록한 광고 계정 비밀키를 다시 넣어야 한다.",
        "JWT_SECRET=$(New-Secret 48)",
        "APP_ENCRYPTION_KEY=$(New-Secret 48)",
        "# 관리자 로그인: 아이디 admin / 비밀번호는 아래 값",
        "ADMIN_PASSWORD=$admin",
        "APP_PUBLIC_MODE=true",
        "APP_SIGNUP_ENABLED=false",
        "H2_CONSOLE_ENABLED=false",
        "API_DOCS_ENABLED=false",
        "# 서버 안에서만 받는다. 바깥에서는 Cloudflare 터널로만 들어온다.",
        "SERVER_ADDRESS=127.0.0.1",
        "SERVER_PORT=8080",
        "APP_WEB_DIR=file:/opt/marketing/web/",
        "# RDS 를 만든 뒤 채운다: 엔드포인트와 마스터 비밀번호",
        "DB_URL=jdbc:mysql://<RDS-엔드포인트>:3306/marketing_agent?serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
        "DB_USERNAME=marketing",
        "DB_PASSWORD=<RDS-마스터-비밀번호>",
        "DB_DRIVER=com.mysql.cj.jdbc.Driver",
        "FLYWAY_ENABLED=true",
        "JPA_DDL_AUTO=validate",
        "NAVER_SEARCHAD_PROVIDER=naver",
        "NAVER_SYNC_ENABLED=true",
        "# true 면 입찰가·예산을 네이버에 반영하지 않고 기록만 남긴다. 충분히 지켜본 뒤 false 로",
        "ADS_BID_DRY_RUN=true",
        "# Claude 상담. 키가 없으면 AI_PROVIDER=stub 으로 두고 ANTHROPIC_API_KEY 줄을 지우면 정해진 질문으로 상담한다",
        "AI_PROVIDER=anthropic",
        "ANTHROPIC_API_KEY=<Anthropic-API-키>",
        "# Cloudflare Zero Trust > Networks > Tunnels 에서 만든 터널의 토큰",
        "CLOUDFLARE_TUNNEL_TOKEN=<터널-토큰>"
    )
    [System.IO.File]::WriteAllLines($envFile, $lines, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "deploy\aws\server.env 를 만들었습니다. <...> 로 된 빈칸을 채운 뒤 다시 실행하세요." -ForegroundColor Yellow
    exit 1
}
$unfilled = Get-Content $envFile -Encoding UTF8 | Where-Object { -not $_.StartsWith("#") -and $_ -match "<[^>]+>" }
if ($unfilled) {
    Write-Host "server.env 에 아직 채우지 않은 칸이 있습니다:" -ForegroundColor Yellow
    $unfilled | ForEach-Object { Write-Host ("  " + $_.Split("=")[0]) }
    exit 1
}

# 2. pem 키 권한(Windows OpenSSH 는 다른 사람이 읽을 수 있는 키를 거부한다)
$Key = (Resolve-Path $Key).Path
icacls $Key /inheritance:r /grant:r "$($env:USERNAME):(R)" | Out-Null
$sshOpts = @("-i", $Key, "-o", "StrictHostKeyChecking=accept-new", "-o", "ConnectTimeout=15")
$target = "$User@$ServerHost"

# 3. 빌드
if (-not $SkipBuild) {
    Write-Host "화면을 빌드합니다..."
    if (-not (Test-Path "client\node_modules")) { npm --prefix client ci; if ($LASTEXITCODE -ne 0) { throw "npm ci 실패" } }
    npm --prefix client run build
    if ($LASTEXITCODE -ne 0) { throw "화면 빌드 실패" }
    Write-Host "서버를 빌드합니다..."
    & .\gradlew.bat bootJar -x test --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw "서버 빌드 실패" }
}
$jar = Get-ChildItem "build\libs\*.jar" | Where-Object { $_.Name -notlike "*-plain.jar" } | Select-Object -First 1
if (-not $jar) { throw "build\libs 에 jar 가 없습니다." }

# 4. 올릴 파일 준비. 서버에서 쓸 파일은 줄바꿈을 LF 로 맞춘다.
$stage = Join-Path $env:TEMP "marketing-deploy"
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory $stage | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)
foreach ($name in @("setup-server.sh", "marketing.service")) {
    $text = [System.IO.File]::ReadAllText((Join-Path $here $name)) -replace "`r`n", "`n"
    [System.IO.File]::WriteAllText((Join-Path $stage $name), $text, $utf8)
}
$envText = [System.IO.File]::ReadAllText($envFile) -replace "`r`n", "`n"
[System.IO.File]::WriteAllText((Join-Path $stage "server.env"), $envText, $utf8)
tar.exe -czf (Join-Path $stage "web.tgz") -C "client\dist" .
if ($LASTEXITCODE -ne 0) { throw "화면 묶기 실패" }
Copy-Item $jar.FullName (Join-Path $stage "app.jar")

Write-Host "서버로 올립니다: $target"
scp @sshOpts (Join-Path $stage "app.jar") (Join-Path $stage "web.tgz") (Join-Path $stage "server.env") `
    (Join-Path $stage "setup-server.sh") (Join-Path $stage "marketing.service") "${target}:/tmp/"
$uploadExit = $LASTEXITCODE
Remove-Item (Join-Path $stage "server.env") -Force
if ($uploadExit -ne 0) { throw "업로드 실패. IP·키 파일·보안그룹(SSH 22번, 내 IP 허용)을 확인하세요." }

# 5. 서버 준비(처음 한 번)와 배포
if ($SetupServer) {
    ssh @sshOpts $target "sudo bash /tmp/setup-server.sh"
    if ($LASTEXITCODE -ne 0) { throw "서버 준비 실패" }
}

$remote = @'
set -e
sudo install -o marketing -g marketing -m 600 /tmp/server.env /opt/marketing/app.env
rm -f /tmp/server.env
sudo install -o marketing -g marketing -m 644 /tmp/app.jar /opt/marketing/app/app.jar
sudo rm -rf /opt/marketing/web.new
sudo mkdir -p /opt/marketing/web.new
sudo tar -xzf /tmp/web.tgz -C /opt/marketing/web.new
sudo rm -rf /opt/marketing/web
sudo mv /opt/marketing/web.new /opt/marketing/web
sudo chown -R marketing:marketing /opt/marketing/web
TOKEN=$(sudo grep '^CLOUDFLARE_TUNNEL_TOKEN=' /opt/marketing/app.env | cut -d= -f2-)
if [ -n "$TOKEN" ] && ! systemctl is-enabled cloudflared >/dev/null 2>&1; then
  sudo cloudflared service install "$TOKEN"
fi
sudo systemctl restart marketing
code=000
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/ || true)
  [ "$code" = "200" ] && break
  sleep 3
done
echo "HEALTH=$code"
if [ "$code" != "200" ]; then sudo tail -n 60 /opt/marketing/logs/app.log; exit 1; fi
if systemctl is-active cloudflared >/dev/null 2>&1; then echo "TUNNEL=active"; else echo "TUNNEL=inactive"; fi
'@ -replace "`r`n", "`n"
ssh @sshOpts $target $remote
if ($LASTEXITCODE -ne 0) { throw "배포 후 서버가 정상 응답하지 않습니다. 위 로그를 확인하세요." }
Write-Host "배포 완료." -ForegroundColor Green
