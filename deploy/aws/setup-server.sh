#!/usr/bin/env bash
# EC2(Amazon Linux 2023) 를 처음 한 번 준비한다. deploy.ps1 -SetupServer 가 sudo 로 실행한다.
# 다시 실행해도 안전하다(이미 된 것은 건너뛴다).
set -euo pipefail

echo "[1/5] 시간대와 패키지"
timedatectl set-timezone Asia/Seoul
dnf -y -q install java-21-amazon-corretto-headless mariadb105 tar

echo "[2/5] 앱 계정과 폴더"
id marketing >/dev/null 2>&1 || useradd --system --home-dir /opt/marketing --shell /sbin/nologin marketing
mkdir -p /opt/marketing/app /opt/marketing/web /opt/marketing/logs
chown -R marketing:marketing /opt/marketing

echo "[3/5] 스왑 2GB (메모리가 잠깐 모자라도 서버가 죽지 않게)"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap defaults 0 0' >> /etc/fstab
fi

echo "[4/5] cloudflared (Cloudflare 터널로만 바깥과 연결한다. 서버에 공개 포트를 열지 않는다)"
if ! command -v cloudflared >/dev/null 2>&1; then
  case "$(uname -m)" in
    aarch64) pkg=cloudflared-linux-aarch64.rpm ;;
    *) pkg=cloudflared-linux-x86_64.rpm ;;
  esac
  curl -fsSL -o /tmp/cloudflared.rpm "https://github.com/cloudflare/cloudflared/releases/latest/download/${pkg}"
  dnf -y -q install /tmp/cloudflared.rpm
fi

echo "[5/5] 서비스 등록"
install -m 644 /tmp/marketing.service /etc/systemd/system/marketing.service
systemctl daemon-reload
systemctl enable marketing >/dev/null

# 로그가 디스크를 채우지 않게 일주일치만 남긴다.
cat > /etc/logrotate.d/marketing <<'ROTATE'
/opt/marketing/logs/app.log {
  daily
  rotate 7
  compress
  missingok
  copytruncate
}
ROTATE

java -version 2>&1 | head -1
echo "서버 준비 완료"
