package com.marketingagent.domain.ads.account;

import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.common.security.CredentialCipher;
import com.marketingagent.common.security.OwnerContext;
import com.marketingagent.domain.ads.account.NaverAccountDtos.AccountStatus;
import com.marketingagent.domain.ads.account.NaverAccountDtos.ConnectRequest;
import com.marketingagent.integration.naver.NaverCredentials;
import com.marketingagent.integration.naver.NaverCredentialsProvider;
import com.marketingagent.integration.naver.NaverSearchAdProperties;
import com.marketingagent.integration.naver.SearchAdClient;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원별 네이버 검색광고 계정 연결을 관리하고, API 호출에 쓸 자격증명을 내준다.
 */
@Slf4j
@Service
public class NaverAccountService implements NaverCredentialsProvider {

    private final NaverAccountRepository repository;
    private final CredentialCipher cipher;
    private final NaverSearchAdProperties properties;
    private final SearchAdClient searchAdClient;

    static final String PLATFORM_ADMIN_EMAIL = "admin@marketing.local";

    private final com.marketingagent.domain.user.UserRepository userRepository;

    /** 클라이언트가 이 서비스에서 자격증명을 받아 가므로 순환 참조를 피하려고 늦게 주입한다. */
    public NaverAccountService(NaverAccountRepository repository, CredentialCipher cipher,
            NaverSearchAdProperties properties, @Lazy SearchAdClient searchAdClient,
            com.marketingagent.domain.user.UserRepository userRepository) {
        this.userRepository = userRepository;
        this.repository = repository;
        this.cipher = cipher;
        this.properties = properties;
        this.searchAdClient = searchAdClient;
    }

    @Override
    @Transactional(readOnly = true)
    public NaverCredentials current() {
        Long ownerId = OwnerContext.currentOwnerId();
        if (ownerId == null) {
            throw new BadRequestException("로그인한 회원을 확인할 수 없습니다.");
        }
        NaverAccount account = repository.findByUserId(ownerId)
                .orElseThrow(() -> new BadRequestException(
                        "네이버 검색광고 계정이 연결되지 않았습니다. 설정 화면에서 먼저 연결하세요."));
        return new NaverCredentials(account.getCustomerId(),
                cipher.decrypt(account.getApiKeyEnc()), cipher.decrypt(account.getSecretKeyEnc()));
    }

    /** 회원 자신의 계정이 없으면 운영자(기본 관리자) 계정으로 조사용 조회를 한다. */
    @Override
    @Transactional(readOnly = true)
    public NaverCredentials research() {
        Long ownerId = OwnerContext.currentOwnerId();
        NaverAccount account = ownerId == null ? null : repository.findByUserId(ownerId).orElse(null);
        if (account == null) {
            account = userRepository.findByEmail(PLATFORM_ADMIN_EMAIL)
                    .flatMap(admin -> repository.findByUserId(admin.getId()))
                    .orElseThrow(() -> new BadRequestException(
                            "키워드 조사에 쓸 네이버 검색광고 계정이 없습니다. 설정에서 내 계정을 연결하거나, 운영자가 관리자 계정에 네이버 키를 연결해야 합니다."));
        }
        return new NaverCredentials(account.getCustomerId(),
                cipher.decrypt(account.getApiKeyEnc()), cipher.decrypt(account.getSecretKeyEnc()));
    }

    @Transactional(readOnly = true)
    public AccountStatus status(Long userId) {
        return repository.findByUserId(userId)
                .map(account -> toStatus(account, isStub()
                        ? "연결 정보가 저장돼 있지만 서버가 Stub 모드라 가짜 데이터를 씁니다."
                        : "연결돼 있습니다."))
                .orElseGet(() -> new AccountStatus(properties.provider(), false, null, null, null, isStub()
                        ? "서버가 Stub 모드라 가짜 데이터를 씁니다. 실계정을 미리 연결해 둘 수 있습니다."
                        : "네이버 검색광고 계정을 연결하세요."));
    }

    /**
     * 자격증명을 저장한다. 실계정 모드에서는 저장 전에 그 키로 실제 조회가 되는지 확인하고,
     * 안 되면 저장하지 않는다.
     */
    @Transactional
    public AccountStatus connect(Long userId, ConnectRequest request) {
        String apiKey = request.apiKey().trim();
        String secretKey = request.secretKey().trim();
        String tail = apiKey.length() <= 4 ? apiKey : apiKey.substring(apiKey.length() - 4);

        NaverAccount account = repository.findByUserId(userId).orElse(null);
        if (account == null) {
            account = repository.save(new NaverAccount(userId, request.customerId().trim(),
                    cipher.encrypt(apiKey), cipher.encrypt(secretKey), tail));
        } else {
            account.update(request.customerId().trim(), cipher.encrypt(apiKey), cipher.encrypt(secretKey), tail);
        }
        repository.flush();

        if (isStub()) {
            return toStatus(account, "저장했습니다. 서버가 Stub 모드라 연결 확인은 하지 않았습니다.");
        }

        int campaigns;
        try {
            campaigns = OwnerContext.callAs(userId, () -> searchAdClient.listCampaigns().size());
        } catch (Exception e) {
            log.warn("네이버 계정 연결 확인 실패: user={} {}", userId, e.getMessage());
            // 예외가 나가면서 트랜잭션이 롤백돼 잘못된 키는 저장되지 않는다.
            throw new BadRequestException(
                    "입력한 키로 네이버에 연결하지 못했습니다. 고객 ID, 액세스 라이선스, 비밀키를 다시 확인하세요.");
        }
        return toStatus(account, "연결했습니다. 캠페인 " + campaigns + "개를 확인했습니다.");
    }

    /** 연결을 끊는다. 이미 쌓인 광고 데이터는 지우지 않는다. */
    @Transactional
    public AccountStatus disconnect(Long userId) {
        repository.findByUserId(userId).ifPresent(repository::delete);
        repository.flush();
        return status(userId);
    }

    /** 일 배치가 돌아야 하는 회원 목록. */
    @Transactional(readOnly = true)
    public List<Long> connectedUserIds() {
        return repository.findAll().stream().map(NaverAccount::getUserId).toList();
    }

    /** 예전 방식(환경변수)으로 넣어 둔 키를 지정한 회원의 계정으로 옮긴다. 이미 연결돼 있으면 건드리지 않는다. */
    @Transactional
    public boolean adoptLegacyCredentials(Long userId) {
        if (isBlank(properties.customerId()) || isBlank(properties.apiKey()) || isBlank(properties.secretKey())
                || repository.existsByUserId(userId)) {
            return false;
        }
        String apiKey = properties.apiKey().trim();
        repository.save(new NaverAccount(userId, properties.customerId().trim(), cipher.encrypt(apiKey),
                cipher.encrypt(properties.secretKey().trim()),
                apiKey.length() <= 4 ? apiKey : apiKey.substring(apiKey.length() - 4)));
        return true;
    }

    private AccountStatus toStatus(NaverAccount account, String message) {
        return new AccountStatus(properties.provider(), true, account.getCustomerId(),
                "****" + account.getApiKeyTail(), account.getConnectedAt(), message);
    }

    private boolean isStub() {
        return !"naver".equalsIgnoreCase(properties.provider());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
