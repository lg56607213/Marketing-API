package com.marketingagent.domain.ads;

import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.account.AdAccount;
import com.marketingagent.domain.ads.account.AdAccountService;
import com.marketingagent.domain.ads.dto.KeywordIdeaResponse;
import com.marketingagent.integration.naver.SearchAdClient;
import com.marketingagent.integration.naver.dto.NccKeyword;
import com.marketingagent.integration.naver.dto.RelatedKeyword;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 키워드 발굴과 등록/제외 관리.
 * 네이버 계정을 직접 바꾸는 작업이라 호출부에서 사용자 의사를 확인한 뒤 부른다.
 * 모든 작업은 로그인한 업체가 가진 광고 계정 안에서만 일어난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordManagementService {

    private static final int MAX_HINTS = 5;

    private final AdAccountService accountService;
    private final AdKeywordRepository keywordRepository;
    private final AdGroupRepository adGroupRepository;

    /** 키워드도구로 연관 키워드를 찾는다. 계정을 바꾸지 않는 읽기 전용 작업이다. */
    @Transactional(readOnly = true)
    public List<KeywordIdeaResponse> findIdeas(Long accountId, List<String> hints,
            boolean excludeRegistered, boolean mustContainHint) {
        AdAccount account = accountService.require(accountId);
        if (hints.isEmpty()) {
            throw new BadRequestException("검색할 키워드를 하나 이상 입력하세요.");
        }
        if (hints.size() > MAX_HINTS) {
            throw new BadRequestException("힌트 키워드는 최대 " + MAX_HINTS + "개까지 넣을 수 있습니다.");
        }

        Set<String> registered = new HashSet<>(keywordRepository.findByAdAccountId(account.getId()).stream()
                .map(AdKeyword::getKeyword)
                .toList());

        // 키워드도구는 업종 단위로 폭넓게 돌려준다. 힌트가 들어간 것만 남기면 실제로 쓸 키워드가 걸러진다.
        List<String> normalizedHints = hints.stream().map(h -> h.replace(" ", "")).toList();

        List<RelatedKeyword> related = accountService.client(account).relatedKeywords(hints);
        return related.stream()
                .filter(r -> !excludeRegistered || !registered.contains(r.keyword()))
                .filter(r -> !mustContainHint || containsAnyHint(r.keyword(), normalizedHints))
                .map(r -> KeywordIdeaResponse.from(r, registered.contains(r.keyword())))
                .sorted(Comparator.comparingLong(KeywordIdeaResponse::totalCount).reversed())
                .toList();
    }

    private boolean containsAnyHint(String keyword, List<String> hints) {
        String normalized = keyword.replace(" ", "");
        return hints.stream().anyMatch(normalized::contains);
    }

    /**
     * 광고그룹에 키워드를 등록한다.
     *
     * @param position 목표 평균노출순위. 지정하면 추정 입찰가를 적용하고, null 이면 그룹 입찰가를 따른다.
     */
    @Transactional
    public List<NccKeyword> addKeywords(Long accountId, String nccAdgroupId, List<String> keywords,
            Integer position, String device) {
        AdAccount account = accountService.require(accountId);
        if (keywords.isEmpty()) {
            throw new BadRequestException("등록할 키워드를 선택하세요.");
        }
        requireAdgroup(account, nccAdgroupId);
        SearchAdClient client = accountService.client(account);

        List<SearchAdClient.NewKeyword> request;
        if (position != null) {
            var estimates = client.estimateBidForPosition(keywords, position,
                    device != null ? device : "MOBILE");
            request = keywords.stream()
                    .map(k -> new SearchAdClient.NewKeyword(k, estimates.get(k)))
                    .toList();
        } else {
            request = keywords.stream()
                    .map(k -> new SearchAdClient.NewKeyword(k, null))
                    .toList();
        }

        List<NccKeyword> created = client.createKeywords(nccAdgroupId, request);
        created.forEach(k -> keywordRepository.findByAdAccountIdAndNccKeywordId(account.getId(), k.nccKeywordId())
                .ifPresentOrElse(
                        existing -> existing.update(k.keyword(), k.status(), k.bidAmt(), k.useGroupBidAmt()),
                        () -> keywordRepository.save(new AdKeyword(account.getId(), k.nccKeywordId(),
                                k.nccAdgroupId(), k.keyword(), k.status(), k.bidAmt(), k.useGroupBidAmt()))));

        log.info("키워드 {}개 등록: 계정={} 그룹={} 목표순위={}", created.size(), account.getId(), nccAdgroupId, position);
        return created;
    }

    @Transactional
    public void removeKeyword(Long accountId, String nccKeywordId) {
        AdAccount account = accountService.require(accountId);
        AdKeyword keyword = keywordRepository.findByAdAccountIdAndNccKeywordId(account.getId(), nccKeywordId)
                .orElseThrow(() -> new BadRequestException("키워드를 찾을 수 없습니다: " + nccKeywordId));
        accountService.client(account).deleteKeyword(nccKeywordId);
        keywordRepository.delete(keyword);
        log.info("키워드 삭제: 계정={} {}", account.getId(), nccKeywordId);
    }

    @Transactional(readOnly = true)
    public List<SearchAdClient.RestrictedKeyword> listRestricted(Long accountId, String nccAdgroupId) {
        AdAccount account = accountService.require(accountId);
        requireAdgroup(account, nccAdgroupId);
        return accountService.client(account).listRestrictedKeywords(nccAdgroupId);
    }

    /** 제외 키워드를 추가한다. 기본은 확장검색 제외(EXP_SEARCH). */
    @Transactional
    public List<SearchAdClient.RestrictedKeyword> addRestricted(Long accountId, String nccAdgroupId,
            List<String> keywords, String type) {
        AdAccount account = accountService.require(accountId);
        if (keywords.isEmpty()) {
            throw new BadRequestException("제외할 키워드를 입력하세요.");
        }
        requireAdgroup(account, nccAdgroupId);
        SearchAdClient client = accountService.client(account);

        Set<String> existing = new HashSet<>(client.listRestrictedKeywords(nccAdgroupId).stream()
                .map(SearchAdClient.RestrictedKeyword::keyword)
                .toList());
        List<String> fresh = keywords.stream().filter(k -> !existing.contains(k)).toList();
        if (fresh.isEmpty()) {
            return List.of();
        }
        return client.addRestrictedKeywords(nccAdgroupId, fresh, type != null ? type : "EXP_SEARCH");
    }

    @Transactional
    public void removeRestricted(Long accountId, String nccAdgroupId, String restrictedKeywordId) {
        AdAccount account = accountService.require(accountId);
        requireAdgroup(account, nccAdgroupId);
        accountService.client(account).deleteRestrictedKeyword(nccAdgroupId, restrictedKeywordId);
    }

    /** 광고그룹이 이 계정에 동기화된 것인지 확인한다. 다른 계정의 그룹 ID 로 호출하는 것을 막는다. */
    private void requireAdgroup(AdAccount account, String nccAdgroupId) {
        adGroupRepository.findByAdAccountIdAndNccAdgroupId(account.getId(), nccAdgroupId)
                .orElseThrow(() -> new BadRequestException("광고그룹을 찾을 수 없습니다: " + nccAdgroupId));
    }
}
