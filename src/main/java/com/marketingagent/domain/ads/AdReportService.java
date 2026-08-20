package com.marketingagent.domain.ads;

import com.marketingagent.ai.AiProvider;
import com.marketingagent.ai.AiRequest;
import com.marketingagent.ai.AiResponse;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.dto.KeywordAggregate;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 광고 성과 분석 리포트를 만든다.
 *
 * <p>데이터 섹션은 항상 DB에서 직접 계산해 정확성을 보장하고,
 * 해석 섹션만 AI에 맡긴다. AI가 stub 이면 규칙 기반 해석으로 대체하며
 * 어느 쪽이 생성했는지 리포트에 명시한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdReportService {

    private static final int TOP_N = 5;

    private final KeywordStatDailyRepository statRepository;
    private final AdKeywordRepository keywordRepository;
    private final AdReportRepository reportRepository;
    private final AiProvider aiProvider;

    @Value("${ai.provider:stub}")
    private String aiProviderName;

    @Value("${ai.openai.model:}")
    private String aiModel;

    @Transactional
    public AdReport generate(int days) {
        LocalDate until = LocalDate.now().minusDays(1);
        LocalDate since = until.minusDays(Math.max(days, 1) - 1L);
        LocalDate prevUntil = since.minusDays(1);
        LocalDate prevSince = prevUntil.minusDays(Math.max(days, 1) - 1L);

        Period current = period(since, until);
        if (current.rows().isEmpty()) {
            throw new BadRequestException("분석할 성과 데이터가 없습니다. 먼저 검색광고 동기화를 실행하세요.");
        }
        Period previous = period(prevSince, prevUntil);

        String facts = renderFacts(current, previous);
        boolean useAi = !"stub".equalsIgnoreCase(aiProviderName);
        String insight = useAi ? aiInsight(facts) : ruleInsight(current, previous);
        String generatedBy = useAi ? aiProviderName + ":" + aiModel : "rule-based";

        String title = String.format("검색광고 성과 리포트 (%s ~ %s)", since, until);
        String body = facts + "\n\n" + insight
                + "\n\n---\n생성: " + generatedBy;

        AdReport report = reportRepository.save(new AdReport(title, body, since, until, generatedBy));
        log.info("광고 리포트 생성: {} ({}건 키워드, {})", title, current.rows().size(), generatedBy);
        return report;
    }

    @Transactional(readOnly = true)
    public List<AdReport> findRecent() {
        return reportRepository.findTop20ByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public AdReport findById(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("리포트를 찾을 수 없습니다: " + id));
    }

    // --- 데이터 섹션 (항상 계산) ---

    private String renderFacts(Period current, Period previous) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 1. 기간 요약\n\n");
        sb.append(String.format("분석 구간: %s ~ %s (직전 구간 %s ~ %s 과 비교)\n\n",
                current.since(), current.until(), previous.since(), previous.until()));

        sb.append("| 지표 | 이번 기간 | 직전 기간 | 증감 |\n");
        sb.append("|---|---:|---:|---:|\n");
        sb.append(metricRow("노출수", current.impCnt(), previous.impCnt(), "회"));
        sb.append(metricRow("클릭수", current.clkCnt(), previous.clkCnt(), "회"));
        sb.append(metricRow("광고비", current.salesAmt(), previous.salesAmt(), "원"));
        sb.append(metricRow("전환수", current.ccnt(), previous.ccnt(), "건"));
        sb.append(rateRow("CTR", current.ctr(), previous.ctr(), "%"));
        sb.append(rateRow("CPC", current.cpc(), previous.cpc(), "원"));
        sb.append(rateRow("CPA", current.cpa(), previous.cpa(), "원"));

        sb.append("\n## 2. 광고비 상위 키워드\n\n");
        sb.append(keywordTable(current.rows().stream()
                .sorted(Comparator.comparingLong((KeywordAggregate r) -> nz(r.salesAmt())).reversed())
                .limit(TOP_N)
                .toList(), current.names()));

        List<KeywordAggregate> wasted = current.rows().stream()
                .filter(r -> nz(r.ccnt()) == 0 && nz(r.salesAmt()) > 0)
                .sorted(Comparator.comparingLong((KeywordAggregate r) -> nz(r.salesAmt())).reversed())
                .limit(TOP_N)
                .toList();
        sb.append("\n## 3. 전환 없이 비용만 발생한 키워드\n\n");
        sb.append(wasted.isEmpty() ? "해당 키워드가 없습니다.\n" : keywordTable(wasted, current.names()));

        List<KeywordAggregate> efficient = current.rows().stream()
                .filter(r -> nz(r.ccnt()) > 0)
                .sorted(Comparator.comparingLong(r -> nz(r.salesAmt()) / Math.max(nz(r.ccnt()), 1)))
                .limit(TOP_N)
                .toList();
        sb.append("\n## 4. 전환 효율이 좋은 키워드\n\n");
        sb.append(efficient.isEmpty() ? "전환 데이터가 없습니다.\n" : keywordTable(efficient, current.names()));

        return sb.toString();
    }

    private String keywordTable(List<KeywordAggregate> rows, Map<String, String> names) {
        StringBuilder sb = new StringBuilder();
        sb.append("| 키워드 | 노출 | 클릭 | CTR | 광고비 | 전환 | CPA |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|\n");
        for (KeywordAggregate r : rows) {
            long imp = nz(r.impCnt());
            long clk = nz(r.clkCnt());
            long sales = nz(r.salesAmt());
            long conv = nz(r.ccnt());
            sb.append(String.format("| %s | %,d | %,d | %.2f%% | %,d원 | %d | %s |\n",
                    names.getOrDefault(r.nccKeywordId(), r.nccKeywordId()),
                    imp, clk, imp == 0 ? 0.0 : clk * 100.0 / imp, sales, conv,
                    conv == 0 ? "-" : String.format("%,d원", sales / conv)));
        }
        return sb.toString();
    }

    private String metricRow(String label, long now, long before, String unit) {
        return String.format("| %s | %,d%s | %,d%s | %s |\n", label, now, unit, before, unit, delta(now, before));
    }

    private String rateRow(String label, double now, double before, String unit) {
        String arrow = before == 0 ? "-" : String.format("%+.1f%%", (now - before) / before * 100);
        return String.format("| %s | %.2f%s | %.2f%s | %s |\n", label, now, unit, before, unit, arrow);
    }

    private String delta(long now, long before) {
        if (before == 0) {
            return "-";
        }
        return String.format("%+.1f%%", (now - before) * 100.0 / before);
    }

    // --- 해석 섹션 ---

    private String aiInsight(String facts) {
        String systemPrompt = """
                당신은 네이버 검색광고를 운영하는 퍼포먼스 마케팅 분석가입니다.
                주어진 성과 데이터만 근거로 분석하고, 데이터에 없는 사실은 절대 지어내지 마십시오.
                한국어로, 실행 가능한 조치 위주로 간결하게 작성하십시오.
                """;
        String userPrompt = """
                아래 검색광고 성과 데이터를 분석해 주십시오.

                %s

                다음 형식으로 작성하십시오.

                ## 5. 분석 및 개선안

                ### 총평
                (2~3문장)

                ### 잘 되고 있는 것
                (항목별 1줄, 근거 수치 포함)

                ### 문제점
                (항목별 1줄, 근거 수치 포함)

                ### 이번 주 실행할 조치
                (우선순위 순으로 3~5개, 각 항목에 대상 키워드와 구체적 행동 명시)
                """.formatted(facts);

        try {
            AiResponse response = aiProvider.generate(new AiRequest(systemPrompt, userPrompt, null));
            return response.content();
        } catch (Exception e) {
            log.error("AI 리포트 생성 실패. 규칙 기반으로 대체합니다.", e);
            return "## 5. 분석 및 개선안\n\nAI 생성에 실패했습니다: " + e.getMessage();
        }
    }

    /** AI 미설정 시 사용하는 규칙 기반 해석. 지어내지 않고 계산된 수치만 말한다. */
    private String ruleInsight(Period current, Period previous) {
        StringBuilder sb = new StringBuilder("## 5. 분석 및 개선안\n\n");

        sb.append("### 총평\n\n");
        sb.append(String.format("이번 기간 광고비 %,d원으로 클릭 %,d회, 전환 %d건을 얻었습니다. ",
                current.salesAmt(), current.clkCnt(), current.ccnt()));
        if (previous.salesAmt() > 0) {
            double spendChange = (current.salesAmt() - previous.salesAmt()) * 100.0 / previous.salesAmt();
            sb.append(String.format("직전 기간 대비 광고비는 %+.1f%% 변했고, ", spendChange));
            if (previous.cpa() > 0 && current.cpa() > 0) {
                double cpaChange = (current.cpa() - previous.cpa()) / previous.cpa() * 100;
                sb.append(String.format("전환당 비용은 %+.1f%% 변했습니다. ", cpaChange));
                sb.append(cpaChange > 10 ? "효율이 나빠지고 있어 점검이 필요합니다.\n"
                        : cpaChange < -10 ? "효율이 개선되고 있습니다.\n" : "효율은 비슷한 수준을 유지하고 있습니다.\n");
            } else {
                sb.append("전환 데이터가 부족해 효율 변화는 판단하기 어렵습니다.\n");
            }
        } else {
            sb.append("비교할 직전 기간 데이터가 없습니다.\n");
        }

        List<KeywordAggregate> wasted = current.rows().stream()
                .filter(r -> nz(r.ccnt()) == 0 && nz(r.salesAmt()) > 0)
                .sorted(Comparator.comparingLong((KeywordAggregate r) -> nz(r.salesAmt())).reversed())
                .toList();
        long wastedSpend = wasted.stream().mapToLong(r -> nz(r.salesAmt())).sum();

        sb.append("\n### 문제점\n\n");
        if (wasted.isEmpty()) {
            sb.append("- 전환 없이 비용만 소진한 키워드는 없습니다.\n");
        } else {
            sb.append(String.format("- 전환 0건인 키워드가 %d개이며 여기에 %,d원(전체 광고비의 %.1f%%)이 쓰였습니다.\n",
                    wasted.size(), wastedSpend,
                    current.salesAmt() == 0 ? 0.0 : wastedSpend * 100.0 / current.salesAmt()));
            wasted.stream().limit(3).forEach(r -> sb.append(String.format("  - %s: %,d원 소진, 클릭 %,d회\n",
                    current.names().getOrDefault(r.nccKeywordId(), r.nccKeywordId()),
                    nz(r.salesAmt()), nz(r.clkCnt()))));
        }

        List<KeywordAggregate> efficient = current.rows().stream()
                .filter(r -> nz(r.ccnt()) > 0)
                .sorted(Comparator.comparingLong(r -> nz(r.salesAmt()) / Math.max(nz(r.ccnt()), 1)))
                .toList();

        sb.append("\n### 잘 되고 있는 것\n\n");
        if (efficient.isEmpty()) {
            sb.append("- 전환 데이터가 없어 효율 우수 키워드를 판단할 수 없습니다. 네이버 전환추적 설정을 확인하세요.\n");
        } else {
            efficient.stream().limit(3).forEach(r -> sb.append(String.format("- %s: CPA %,d원, 전환 %d건\n",
                    current.names().getOrDefault(r.nccKeywordId(), r.nccKeywordId()),
                    nz(r.salesAmt()) / Math.max(nz(r.ccnt()), 1), nz(r.ccnt()))));
        }

        sb.append("\n### 이번 주 실행할 조치\n\n");
        int step = 1;
        if (!wasted.isEmpty()) {
            sb.append(String.format("%d. 전환 0건 키워드 %d개의 입찰가를 낮추거나 제외를 검토하세요. 「입찰가 조정」에서 조정안을 확인할 수 있습니다.\n",
                    step++, wasted.size()));
        }
        if (!efficient.isEmpty()) {
            sb.append(String.format("%d. CPA가 낮은 상위 키워드(%s)의 입찰가 상향으로 노출을 늘리세요.\n", step++,
                    current.names().getOrDefault(efficient.get(0).nccKeywordId(), "-")));
        }
        if (current.ccnt() == 0) {
            sb.append(String.format("%d. 전환 데이터가 전혀 없습니다. 사이트에 네이버 전환추적 스크립트가 설치돼 있는지 확인하세요.\n", step++));
        }
        sb.append(String.format("%d. 조정 후 다음 주 같은 리포트로 CPA 변화를 다시 확인하세요.\n", step));

        return sb.toString();
    }

    // --- 집계 ---

    private Period period(LocalDate since, LocalDate until) {
        List<KeywordAggregate> rows = statRepository.aggregateByKeyword(since, until);
        Map<String, String> names = keywordRepository.findAll().stream()
                .collect(Collectors.toMap(AdKeyword::getNccKeywordId, AdKeyword::getKeyword,
                        (a, b) -> a, LinkedHashMap::new));

        long imp = rows.stream().mapToLong(r -> nz(r.impCnt())).sum();
        long clk = rows.stream().mapToLong(r -> nz(r.clkCnt())).sum();
        long sales = rows.stream().mapToLong(r -> nz(r.salesAmt())).sum();
        long conv = rows.stream().mapToLong(r -> nz(r.ccnt())).sum();

        return new Period(since, until, rows, names, imp, clk, sales, conv,
                imp == 0 ? 0.0 : clk * 100.0 / imp,
                clk == 0 ? 0.0 : (double) sales / clk,
                conv == 0 ? 0.0 : (double) sales / conv);
    }

    private long nz(Long value) {
        return value != null ? value : 0L;
    }

    private record Period(
            LocalDate since, LocalDate until,
            List<KeywordAggregate> rows, Map<String, String> names,
            long impCnt, long clkCnt, long salesAmt, long ccnt,
            double ctr, double cpc, double cpa) {}
}
