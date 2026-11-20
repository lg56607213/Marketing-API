package com.marketingagent.domain.ads.account;

import com.marketingagent.common.crypto.SecretCipher;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.common.exception.DuplicateResourceException;
import com.marketingagent.common.exception.ResourceNotFoundException;
import com.marketingagent.common.security.CurrentUser;
import com.marketingagent.common.security.SystemTask;
import com.marketingagent.domain.ads.AdCampaign;
import com.marketingagent.domain.ads.AdCampaignRepository;
import com.marketingagent.domain.ads.AdGroupRepository;
import com.marketingagent.domain.ads.AdKeywordRepository;
import com.marketingagent.domain.ads.AdReportRepository;
import com.marketingagent.domain.ads.BidRecommendationRepository;
import com.marketingagent.domain.ads.KeywordStatDailyRepository;
import com.marketingagent.domain.ads.SearchQueryDailyRepository;
import com.marketingagent.integration.naver.SearchAdClient;
import com.marketingagent.integration.naver.SearchAdClientFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 광고 계정 등록과 접근 통제.
 *
 * <p>광고 데이터를 다루는 모든 서비스는 {@link #require} 로 계정을 받아야 한다.
 * 여기서 로그인한 사용자의 업체 소유인지 확인하므로, 다른 업체의 계정 ID 를 넣어도 조회되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdAccountService {

    private final AdAccountRepository accountRepository;
    private final AdCampaignRepository campaignRepository;
    private final AdGroupRepository adGroupRepository;
    private final AdKeywordRepository keywordRepository;
    private final KeywordStatDailyRepository statRepository;
    private final SearchQueryDailyRepository searchQueryRepository;
    private final BidRecommendationRepository recommendationRepository;
    private final AdReportRepository reportRepository;
    private final SearchAdClientFactory clientFactory;
    private final SecretCipher secretCipher;
    private final ApplicationEventPublisher events;

    /** 로그인한 사용자의 업체가 가진 계정만 돌려준다. 남의 계정은 없는 것으로 취급한다. */
    @Transactional(readOnly = true)
    public AdAccount require(Long accountId) {
        if (accountId == null) {
            throw new BadRequestException("광고 계정을 선택하세요. 등록된 계정이 없다면 먼저 광고 계정을 추가하세요.");
        }
        // 일 배치·자동 운영은 로그인 없이 돈다. 배치가 직접 연 계정 하나만 소유 확인 없이 허용한다.
        if (SystemTask.allows(accountId)) {
            return accountRepository.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("광고 계정을 찾을 수 없습니다: " + accountId));
        }
        Long companyId = CurrentUser.companyId();
        return accountRepository.findById(accountId)
                .filter(account -> companyId.equals(account.getCompanyId()))
                .orElseThrow(() -> new ResourceNotFoundException("광고 계정을 찾을 수 없습니다: " + accountId));
    }

    public SearchAdClient client(AdAccount account) {
        return clientFactory.forAccount(account);
    }

    /** 접근 확인을 마친 계정과 그 클라이언트를 묶어 돌려준다. */
    @Transactional(readOnly = true)
    public AccountScope scope(Long accountId) {
        return new AccountScope(require(accountId), this);
    }

    /** 캠페인 필터 값을 검증한다. 비어 있으면 null(계정 전체)을 돌려준다. */
    @Transactional(readOnly = true)
    public String normalizeCampaign(AdAccount account, String campaignId) {
        if (campaignId == null || campaignId.isBlank()) {
            return null;
        }
        return campaignRepository.findByAdAccountIdAndNccCampaignId(account.getId(), campaignId)
                .map(AdCampaign::getNccCampaignId)
                .orElseThrow(() -> new ResourceNotFoundException("캠페인을 찾을 수 없습니다: " + campaignId));
    }

    @Transactional(readOnly = true)
    public List<AdAccount> findMine() {
        return accountRepository.findByCompanyIdOrderByIdAsc(CurrentUser.companyId());
    }

    @Transactional
    public AdAccount create(AdAccountRequest request) {
        Long companyId = CurrentUser.companyId();
        if (accountRepository.existsByCompanyIdAndName(companyId, request.name().trim())) {
            throw new DuplicateResourceException("같은 이름의 광고 계정이 이미 있습니다: " + request.name());
        }

        boolean stub = AdAccount.PROVIDER_STUB.equalsIgnoreCase(request.provider());
        if (!stub && (isBlank(request.customerId()) || isBlank(request.apiKey()) || isBlank(request.secretKey()))) {
            throw new BadRequestException("고객 ID, 액세스 라이선스, 비밀키를 모두 입력하세요.");
        }

        AdAccount account = new AdAccount(companyId, request.name().trim(),
                stub ? AdAccount.PROVIDER_STUB : AdAccount.PROVIDER_NAVER,
                stub ? null : request.customerId().trim(),
                stub ? null : request.apiKey().trim(),
                stub ? null : secretCipher.encrypt(request.secretKey().trim()),
                positiveOrNull(request.targetCpa()));
        if (!stub) {
            verifyCredentials(account);
        }
        accountRepository.save(account);
        log.info("광고 계정 등록: 업체={} 계정={} ({})", companyId, account.getName(), account.getProvider());
        return account;
    }

    /** 이름과 목표 CPA 를 바꾼다. 자격증명은 값이 들어온 항목만 교체한다. */
    @Transactional
    public AdAccount update(Long accountId, AdAccountRequest request) {
        AdAccount account = require(accountId);
        String name = request.name().trim();
        if (!name.equals(account.getName())
                && accountRepository.existsByCompanyIdAndName(account.getCompanyId(), name)) {
            throw new DuplicateResourceException("같은 이름의 광고 계정이 이미 있습니다: " + name);
        }
        account.update(name, positiveOrNull(request.targetCpa()));

        if (!account.isStub()) {
            account.updateCredentials(
                    isBlank(request.customerId()) ? account.getCustomerId() : request.customerId().trim(),
                    isBlank(request.apiKey()) ? account.getApiKey() : request.apiKey().trim(),
                    isBlank(request.secretKey()) ? account.getSecretKeyEnc()
                            : secretCipher.encrypt(request.secretKey().trim()));
            boolean credentialsChanged = !isBlank(request.customerId()) || !isBlank(request.apiKey())
                    || !isBlank(request.secretKey());
            if (credentialsChanged) {
                clientFactory.forget(account.getId());
                verifyCredentials(account);
            }
        }
        return account;
    }

    /** 계정 기본 목표 CPA 를 바꾼다. 목표 상담에서 확정한 값이 입찰 추천의 기준이 되도록 한다. */
    @Transactional
    public void changeTargetCpa(Long accountId, Long targetCpa) {
        AdAccount account = require(accountId);
        account.update(account.getName(), positiveOrNull(targetCpa));
    }

    /**
     * 입력한 키로 네이버에 실제로 한 번 물어본다. 오타가 있는 키를 저장해 두면 나중에 동기화가 이유 없이 실패한 것처럼 보이므로
     * 저장하기 전에 확인한다. 실패하면 아무것도 저장하지 않는다(트랜잭션이 되돌려진다).
     */
    private void verifyCredentials(AdAccount account) {
        try {
            clientFactory.forAccount(account).listCampaigns();
        } catch (Exception e) {
            log.warn("광고 계정 키 확인 실패: 업체={} 계정={} {}", account.getCompanyId(), account.getName(), e.getMessage());
            throw new BadRequestException("네이버에서 이 키를 확인하지 못했습니다. 검색광고 관리자 > 도구 > API 사용 관리에서 "
                    + "고객 ID, 액세스 라이선스, 비밀키를 다시 복사해 넣어 주세요. 앞뒤 공백이나 다른 계정의 키가 섞이지 않았는지도 확인하세요.");
        }
    }

    /** 계정과 그 계정으로 쌓은 데이터를 모두 지운다. 네이버 쪽 광고에는 영향이 없다. */
    @Transactional
    public void delete(Long accountId) {
        AdAccount account = require(accountId);
        events.publishEvent(new AdAccountDeleted(accountId));
        statRepository.deleteAllByAccount(accountId);
        searchQueryRepository.deleteAllByAccount(accountId);
        recommendationRepository.deleteAllByAccount(accountId);
        reportRepository.deleteAllByAccount(accountId);
        keywordRepository.deleteAllByAccount(accountId);
        adGroupRepository.deleteAllByAccount(accountId);
        campaignRepository.deleteAllByAccount(accountId);
        accountRepository.delete(account);
        clientFactory.forget(accountId);
        log.info("광고 계정 삭제: {} ({})", account.getName(), accountId);
    }

    private Long positiveOrNull(Long value) {
        return value != null && value > 0 ? value : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
