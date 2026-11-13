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

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByEmail(ADMIN_EMAIL)) {
            User admin = new User(ADMIN_EMAIL, passwordEncoder.encode(ADMIN_PASSWORD), UserRole.ADMIN);
            userRepository.save(admin);
            log.info("Default admin account created: {}", ADMIN_EMAIL);
        }
        // 예전에는 네이버 키를 환경변수로 하나만 받았다. 그 키가 남아 있으면 관리자 계정의 연결로 옮겨 둔다.
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(admin -> {
            if (accountService.adoptLegacyCredentials(admin.getId())) {
                log.info("환경변수의 네이버 자격증명을 {} 계정의 연결로 옮겼습니다.", ADMIN_EMAIL);
            }
        });
    }
}
