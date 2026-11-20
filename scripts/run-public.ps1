# 이 PC 에서 마케팅 API 를 외부 공개용으로 띄우고 Cloudflare Tunnel 로 연결한다.
# PC 를 끄거나 이 창을 닫으면 주소(marketing.onlycartalk.com)도 함께 닫힌다.
#
#   powershell -ExecutionPolicy Bypass -File scripts\run-public.ps1            # 실행
#   powershell -ExecutionPolicy Bypass -File scripts\run-public.ps1 -Rebuild   # 코드를 바꾼 뒤 다시 빌드해서 실행
#   powershell -ExecutionPolicy Bypass -File scripts\run-public.ps1 -NoTunnel  # 터널 없이 이 PC 에서만 확인
param(
    [switch]$Rebuild,
    [switch]$NoTunnel,
    [string]$TunnelName = "marketing",
    [int]$Port = 8090
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$envFile = Join-Path $root "public.env"

function New-Secret([int]$bytes) {
    $buffer = New-Object byte[] $bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($buffer)
    $rng.Dispose()
    return ([Convert]::ToBase64String($buffer) -replace '[+/=]', '')
}

# 1. 비밀값 파일. 처음 한 번만 만든다. 저장소에는 올라가지 않는다(.gitignore 의 *.env).
if (-not (Test-Path $envFile)) {
    $lines = @(
        "# 외부 공개용 설정. 이 파일은 남에게 주지 말 것.",
        "# JWT_SECRET 을 바꾸면 모두 다시 로그인해야 하고,",
        "# APP_ENCRYPTION_KEY 를 바꾸면 등록된 광고 계정의 비밀키를 다시 넣어야 한다.",
        "JWT_SECRET=$(New-Secret 48)",
        "APP_ENCRYPTION_KEY=$(New-Secret 48)",
        "# 관리자 로그인: 아이디 admin / 비밀번호는 아래 값",
        "ADMIN_PASSWORD=$(New-Secret 18)",
        "APP_PUBLIC_MODE=true",
        "H2_CONSOLE_ENABLED=false",
        "API_DOCS_ENABLED=false",
        "# true 로 바꾸면 주소를 아는 누구나 가입할 수 있다",
        "APP_SIGNUP_ENABLED=false",
        "NAVER_SEARCHAD_PROVIDER=naver",
        "NAVER_SYNC_ENABLED=true",
        "# true 면 입찰가·예산을 네이버에 반영하지 않고 기록만 남긴다. 충분히 지켜본 뒤 false 로",
        "ADS_BID_DRY_RUN=true",
        "DB_URL=jdbc:h2:file:./data-public/marketing_agent;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"
    )
    [System.IO.File]::WriteAllLines($envFile, $lines, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "public.env 를 새로 만들었습니다. 관리자 비밀번호는 이 파일의 ADMIN_PASSWORD 입니다." -ForegroundColor Yellow
}

Get-Content $envFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#")) {
        $index = $line.IndexOf("=")
        if ($index -gt 0) {
            [Environment]::SetEnvironmentVariable($line.Substring(0, $index), $line.Substring($index + 1), "Process")
        }
    }
}
# 이 PC 안에서만 받는다. 바깥에서는 Cloudflare Tunnel 을 거쳐야만 들어올 수 있다.
$env:SERVER_PORT = "$Port"
$env:SERVER_ADDRESS = "127.0.0.1"

# 2. 화면과 서버 빌드
if ($Rebuild -or -not (Test-Path "client\dist\index.html")) {
    Write-Host "화면을 빌드합니다..."
    if (-not (Test-Path "client\node_modules")) { npm --prefix client install; if ($LASTEXITCODE -ne 0) { throw "npm install 실패" } }
    npm --prefix client run build
    if ($LASTEXITCODE -ne 0) { throw "화면 빌드 실패" }
}
$jar = Get-ChildItem "build\libs\*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike "*-plain.jar" } | Select-Object -First 1
if ($Rebuild -or -not $jar) {
    Write-Host "서버를 빌드합니다..."
    & .\gradlew.bat bootJar -x test --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw "서버 빌드 실패" }
    $jar = Get-ChildItem "build\libs\*.jar" | Where-Object { $_.Name -notlike "*-plain.jar" } | Select-Object -First 1
}

# 3. 터널
$tunnel = $null
if (-not $NoTunnel) {
    $cloudflared = Get-Command cloudflared -ErrorAction SilentlyContinue
    if (-not $cloudflared) {
        Write-Host "cloudflared 가 설치돼 있지 않아 터널 없이 띄웁니다. scripts\setup-tunnel.ps1 을 먼저 실행하세요." -ForegroundColor Yellow
    } else {
        $tunnel = Start-Process -FilePath $cloudflared.Source -PassThru -WindowStyle Minimized `
            -ArgumentList @("tunnel", "run", "--url", "http://127.0.0.1:$Port", $TunnelName)
        Write-Host "터널 '$TunnelName' 을 시작했습니다."
    }
}

# 4. 서버. 이 창을 닫거나 Ctrl+C 를 누르면 서버와 터널이 함께 내려간다.
try {
    Write-Host "서버를 시작합니다: http://127.0.0.1:$Port"
    & java -jar $jar.FullName
} finally {
    if ($tunnel -and -not $tunnel.HasExited) { Stop-Process -Id $tunnel.Id -Force }
}
