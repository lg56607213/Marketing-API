package com.marketingagent;

import com.marketingagent.common.crypto.SecretCipher;
import com.marketingagent.domain.ads.AdCampaignRepository;
import com.marketingagent.domain.ads.AdGroupRepository;
import com.marketingagent.domain.ads.AdKeywordRepository;
import com.marketingagent.domain.ads.AdReportRepository;
import com.marketingagent.domain.ads.BidRecommendationRepository;
import com.marketingagent.domain.ads.KeywordStatDailyRepository;
import com.marketingagent.domain.ads.SearchQueryDailyRepository;
import com.marketingagent.domain.ads.account.AdAccount;
import com.marketingagent.domain.ads.account.AdAccountRepository;
import com.marketingagent.domain.brand.BrandRepository;
import com.marketingagent.domain.company.Company;
import com.marketingagent.domain.company.CompanyRepository;
import com.marketingagent.domain.user.User;
import com.marketingagent.domain.user.UserRepository;
import com.marketingagent.domain.user.UserRole;
import com.marketingagent.integration.naver.NaverSearchAdProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첫 실행 준비와, 업체 구분이 없던 시절 데이터의 이관.
 *
 * <p>업체/광고 계정 개념이 생기기 전에 쌓인 사용자·브랜드·광고 데이터는 소속이 비어 있다.
 * 이들을 기본 업체와 기본 광고 계정으로 옮겨 기존 사용자가 그대로 이어 쓸 수 있게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final String ADMIN_EMAIL = "admin@marketing.local";
    private static final String ADMIN_PASSWORD = "admin1234";
    private static final String DEFAULT_COMPANY_NAME = "기본 업체";

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final BrandRepository brandRepository;
    private final AdAccountRepository accountRepository;
    private final AdCampaignRepository campaignRepository;
    private final AdGroupRepository adGroupRepository;
    private final AdKeywordRepository keywordRepository;
    private final KeywordStatDailyRepository statRepository;
    private final SearchQueryDailyRepository searchQueryRepository;
    private final BidRecommendationRepository recommendationRepository;
    private final AdReportRepository reportRepository;
    private final NaverSearchAdProperties naverProperties;
    private final SecretCipher secretCipher;
    private final PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Value("${app.admin-password:}")
    private String configuredAdminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long companyId = ensureDefaultCompany();
        applyConfiguredAdminPassword();
        adoptLegacyOwners(companyId);
        ensureDefaultAdAccount(companyId);
    }

    /** 기본 관리자와 그 업체를 만든다. 이미 있으면 그 업체 ID 를 돌려준다. */
    private Long ensureDefaultCompany() {
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElse(null);
        if (admin != null && admin.getCompanyId() != null) {
            return admin.getCompanyId();
        }

        Company company = companyRepository.save(new Company(DEFAULT_COMPANY_NAME));
        if (admin == null) {
            String password = adminPasswordConfigured() ? configuredAdminPassword : ADMIN_PASSWORD;
            userRepository.save(new User(ADMIN_EMAIL, passwordEncoder.encode(password),
                    UserRole.ADMIN, company.getId()));
            log.info("Default admin account created: {}", ADMIN_EMAIL);
        } else {
            admin.assignCompany(company.getId());
        }
        return company.getId();
    }

    /** 널리 알려진 기본 비밀번호로 만들어진 관리자 계정을 설정한 비밀번호로 맞춘다. */
    private void applyConfiguredAdminPassword() {
        if (!adminPasswordConfigured()) {
            return;
        }
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(admin -> {
            if (!passwordEncoder.matches(configuredAdminPassword, admin.getPassword())) {
                admin.changePassword(passwordEncoder.encode(configuredAdminPassword));
                log.info("관리자 계정의 비밀번호를 설정값으로 바꿨습니다.");
            }
        });
    }

    private boolean adminPasswordConfigured() {
        return configuredAdminPassword != null && !configuredAdminPassword.isBlank();
    }

    private void adoptLegacyOwners(Long companyId) {
        var users = userRepository.findByCompanyIdIsNull();
        users.forEach(user -> user.assignCompany(companyId));
        var brands = brandRepository.findByCompanyIdIsNull();
        brands.forEach(brand -> brand.assignCompany(companyId));
        if (!users.isEmpty() || !brands.isEmpty()) {
            log.info("업체가 없던 사용자 {}명, 브랜드 {}개를 기본 업체로 옮겼습니다.", users.size(), brands.size());
        }
    }

    /**
     * 기본 업체에 광고 계정이 하나도 없으면 만든다.
     * 예전처럼 환경변수로 네이버 자격증명을 준 경우에는 그 값으로 실계정을, 아니면 예시 데이터 계정을 만든다.
     */
    private void ensureDefaultAdAccount(Long companyId) {
        if (!accountRepository.findByCompanyIdOrderByIdAsc(companyId).isEmpty()) {
            return;
        }

        boolean fromEnv = "naver".equalsIgnoreCase(naverProperties.provider())
                && notBlank(naverProperties.customerId())
                && notBlank(naverProperties.apiKey())
                && notBlank(naverProperties.secretKey());

        AdAccount account = accountRepository.save(fromEnv
                ? new AdAccount(companyId, "기본 광고 계정", AdAccount.PROVIDER_NAVER,
                        naverProperties.customerId(), naverProperties.apiKey(),
                        secretCipher.encrypt(naverProperties.secretKey()), null)
                : new AdAccount(companyId, "예시 광고 계정", AdAccount.PROVIDER_STUB, null, null, null, null));
        log.info("기본 광고 계정 생성: {} ({})", account.getName(), account.getProvider());

        Long accountId = account.getId();
        int adopted = campaignRepository.adoptOrphans(accountId)
                + adGroupRepository.adoptOrphans(accountId)
                + keywordRepository.adoptOrphans(accountId)
                + statRepository.adoptOrphans(accountId)
                + searchQueryRepository.adoptOrphans(accountId)
                + recommendationRepository.adoptOrphans(accountId)
                + reportRepository.adoptOrphans(accountId);
        if (adopted > 0) {
            log.info("계정 구분이 없던 광고 데이터 {}행을 '{}' 로 옮겼습니다.", adopted, account.getName());
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
