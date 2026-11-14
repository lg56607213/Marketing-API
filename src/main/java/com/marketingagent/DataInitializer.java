package com.marketingagent;

import com.marketingagent.domain.ads.account.NaverAccountService;
import com.marketingagent.domain.user.User;
import com.marketingagent.domain.user.UserRepository;
import com.marketingagent.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final String ADMIN_EMAIL = "admin@marketing.local";
    private static final String ADMIN_PASSWORD = "admin1234";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NaverAccountService accountService;

    @org.springframework.beans.factory.annotation.Value("${app.admin-password:}")
    private String configuredAdminPassword;

    @Override
    public void run(ApplicationArguments args) {
        boolean configured = configuredAdminPassword != null && !configuredAdminPassword.isBlank();
        if (!userRepository.existsByEmail(ADMIN_EMAIL)) {
            User admin = new User(ADMIN_EMAIL,
                    passwordEncoder.encode(configured ? configuredAdminPassword : ADMIN_PASSWORD), UserRole.ADMIN);
            userRepository.save(admin);
            log.info("Default admin account created: {}", ADMIN_EMAIL);
        } else if (configured) {
            // 널리 알려진 기본 비밀번호로 만들어진 관리자 계정을 설정한 비밀번호로 맞춘다.
            userRepository.findByEmail(ADMIN_EMAIL).ifPresent(admin -> {
                if (!passwordEncoder.matches(configuredAdminPassword, admin.getPassword())) {
                    admin.changePassword(passwordEncoder.encode(configuredAdminPassword));
                    userRepository.save(admin);
                    log.info("관리자 계정의 비밀번호를 설정값으로 바꿨습니다.");
                }
            });
        }
        // 예전에는 네이버 키를 환경변수로 하나만 받았다. 그 키가 남아 있으면 관리자 계정의 연결로 옮겨 둔다.
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(admin -> {
            if (accountService.adoptLegacyCredentials(admin.getId())) {
                log.info("환경변수의 네이버 자격증명을 {} 계정의 연결로 옮겼습니다.", ADMIN_EMAIL);
            }
        });
    }
}
