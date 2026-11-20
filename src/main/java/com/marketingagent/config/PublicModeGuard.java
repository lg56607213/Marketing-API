package com.marketingagent.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 외부 공개용(app.public-mode=true)으로 띄울 때 위험한 기본값이 남아 있으면 서버를 띄우지 않는다.
 * 저장소에 적혀 있는 기본 비밀값은 누구나 아는 값이라, 그대로 공개하면 로그인 토큰을 위조하거나
 * 회원이 맡긴 네이버 키를 풀 수 있다.
 */
@Slf4j
@Component
public class PublicModeGuard {

    private static final String DEFAULT_JWT_SECRET = "ai-marketing-agent-secret-key-must-be-at-least-32-characters-long";

    @Value("${app.public-mode:false}")
    private boolean publicMode;
    @Value("${jwt.secret}")
    private String jwtSecret;
    @Value("${app.encryption-key}")
    private String encryptionKey;
    @Value("${app.admin-password:}")
    private String adminPassword;
    @Value("${spring.h2.console.enabled:false}")
    private boolean h2Console;
    /** 운영자가 위험을 알고 짧은 관리자 비밀번호를 쓰기로 했을 때만 true. */
    @Value("${app.allow-weak-admin-password:false}")
    private boolean allowWeakAdminPassword;

    @PostConstruct
    void check() {
        if (!publicMode) {
            return;
        }
        List<String> problems = new ArrayList<>();
        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            problems.add("JWT_SECRET 이 기본값입니다");
        }
        // 암호화 키를 따로 주지 않으면 JWT 비밀값을 쓴다. 그러면 JWT 비밀값을 바꾸는 순간 저장된 광고 키를 못 읽게 된다.
        if (encryptionKey == null || encryptionKey.equals(jwtSecret)) {
            problems.add("APP_ENCRYPTION_KEY 가 따로 지정되지 않았습니다");
        }
        boolean weak = adminPassword == null || adminPassword.length() < 12;
        if (adminPassword == null || adminPassword.isBlank()) {
            problems.add("ADMIN_PASSWORD 가 없습니다");
        } else if (weak && !allowWeakAdminPassword) {
            problems.add("ADMIN_PASSWORD 가 12자 미만입니다 (그래도 쓰려면 APP_ALLOW_WEAK_ADMIN_PASSWORD=true)");
        } else if (weak) {
            log.warn("관리자 비밀번호가 12자 미만인 채로 외부에 공개돼 있습니다. 쉽게 추측될 수 있으니 ADS_BID_DRY_RUN 을 끄지 마세요.");
        }
        if (h2Console) {
            problems.add("H2_CONSOLE_ENABLED 가 true 입니다");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("외부 공개 모드로 띄울 수 없습니다: " + String.join(", ", problems));
        }
    }
}
