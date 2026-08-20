package com.marketingagent.domain.ads;

import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.dto.SearchQueryAggregate;
import com.marketingagent.domain.ads.dto.SearchQueryPerformance;
import com.marketingagent.domain.ads.dto.SearchQuerySyncResult;
import com.marketingagent.integration.naver.SearchAdClient;
import com.marketingagent.integration.naver.dto.SearchQueryRow;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검색어 리포트를 내려받아 적재하고 집계한다.
 *
 * <p>리포트는 하루 단위로만 생성되므로 기간 조회는 날짜별로 반복 호출한다.
 * 네이버 쪽 부하를 고려해 한 번에 받을 수 있는 일수를 제한한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchQueryService {

    private static final int MAX_SYNC_DAYS = 14;

    private final SearchAdClient searchAdClient;
    private final SearchQueryDailyRepository repository;
    private final AdKeywordRepository keywordRepository;

    @Transactional
    public SearchQuerySyncResult sync(int days) {
        int span = Math.min(Math.max(days, 1), MAX_SYNC_DAYS);
        // 당일 리포트는 아직 집계되지 않아 조회가 거부되는 경우가 있어 전일까지만 받는다.
        LocalDate until = LocalDate.now().minusDays(1);
        LocalDate since = until.minusDays(span - 1L);

        int rowCount = 0;
        int failedDays = 0;
        for (LocalDate date = since; !date.isAfter(until); date = date.plusDays(1)) {
            try {
                List<SearchQueryRow> rows = searchAdClient.searchQueryReport(date);
                rows.forEach(this::upsert);
                rowCount += rows.size();
            } catch (Exception e) {
                // 광고가 나가지 않은 날은 지표가 없어 리포트 생성이 거부된다. 그 날만 건너뛴다.
                failedDays++;
                log.warn("{} 검색어 리포트를 건너뜁니다: {}", date, e.getMessage());
            }
        }

        log.info("검색어 리포트 동기화 완료: {}일 중 {}일 실패, {}행 ({} ~ {})",
                span, failedDays, rowCount, since, until);
        return new SearchQuerySyncResult(span, rowCount, since, until);
    }

    @Transactional(readOnly = true)
    public List<SearchQueryPerformance> performance(LocalDate since, LocalDate until) {
        List<SearchQueryAggregate> rows = repository.aggregate(since, until);
        if (rows.isEmpty()) {
            throw new BadRequestException("적재된 검색어 데이터가 없습니다. 먼저 검색어 리포트를 가져오세요.");
        }

        Set<String> registered = new HashSet<>(keywordRepository.findAll().stream()
                .map(AdKeyword::getKeyword)
                .toList());

        return rows.stream().map(row -> {
            long imp = nz(row.impCnt());
            long clk = nz(row.clkCnt());
            long sales = nz(row.salesAmt());
            return new SearchQueryPerformance(
                    row.searchQuery(),
                    registered.contains(row.searchQuery()),
                    imp, clk,
                    imp == 0 ? 0.0 : round2(clk * 100.0 / imp),
                    clk == 0 ? 0.0 : round2((double) sales / clk),
                    sales);
        }).toList();
    }

    private void upsert(SearchQueryRow row) {
        repository.findByStatDateAndNccAdgroupIdAndSearchQueryAndDevice(
                        row.statDate(), row.nccAdgroupId(), row.searchQuery(), row.device())
                .ifPresentOrElse(
                        existing -> existing.update(row.impCnt(), row.clkCnt(), row.salesAmt()),
                        () -> repository.save(new SearchQueryDaily(row.statDate(), row.nccCampaignId(),
                                row.nccAdgroupId(), row.searchQuery(), row.device(),
                                row.impCnt(), row.clkCnt(), row.salesAmt())));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private long nz(Long value) {
        return value != null ? value : 0L;
    }
}
