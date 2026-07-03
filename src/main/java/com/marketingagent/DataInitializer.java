package com.marketingagent;

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

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByEmail(ADMIN_EMAIL)) {
            User admin = new User(ADMIN_EMAIL, passwordEncoder.encode(ADMIN_PASSWORD), UserRole.ADMIN);
            userRepository.save(admin);
            log.info("Default admin account created: {}", ADMIN_EMAIL);
        }
    }
}
