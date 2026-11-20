# Cloudflare Tunnel 을 처음 한 번 설정한다. 도메인이 Cloudflare 에 있어야 한다.
#
#   powershell -ExecutionPolicy Bypass -File scripts\setup-tunnel.ps1 -Hostname marketing.onlycartalk.com
#
# 하는 일:
#   1) Cloudflare 로그인 (브라우저가 열리면 도메인을 골라 승인한다)
#   2) 터널 만들기
#   3) 그 주소를 터널로 보내는 DNS 레코드(CNAME) 한 줄 추가. 기존 레코드는 건드리지 않는다
param(
    [Parameter(Mandatory = $true)][string]$Hostname,
    [string]$TunnelName = "marketing"
)

$ErrorActionPreference = "Stop"
if (-not (Get-Command cloudflared -ErrorAction SilentlyContinue)) {
    throw "cloudflared 가 없습니다. 먼저 설치하세요:  winget install --id Cloudflare.cloudflared"
}

$cert = Join-Path $env:USERPROFILE ".cloudflared\cert.pem"
if (-not (Test-Path $cert)) {
    Write-Host "브라우저에서 Cloudflare 에 로그인하고 도메인을 선택해 승인하세요."
    cloudflared tunnel login
    if ($LASTEXITCODE -ne 0) { throw "로그인 실패" }
}

$exists = (cloudflared tunnel list --output json | ConvertFrom-Json) | Where-Object { $_.name -eq $TunnelName }
if (-not $exists) {
    cloudflared tunnel create $TunnelName
    if ($LASTEXITCODE -ne 0) { throw "터널 생성 실패" }
}

cloudflared tunnel route dns $TunnelName $Hostname
if ($LASTEXITCODE -ne 0) { throw "DNS 연결 실패. 같은 이름의 레코드가 이미 있으면 Cloudflare 대시보드에서 확인하세요." }

Write-Host "완료. 이제 scripts\run-public.ps1 을 실행하면 https://$Hostname 으로 접속됩니다." -ForegroundColor Green
