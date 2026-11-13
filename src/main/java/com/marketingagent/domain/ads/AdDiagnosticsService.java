package com.marketingagent.domain.ads;

import com.marketingagent.domain.ads.dto.DiagnosticReport;
import com.marketingagent.domain.ads.dto.DiagnosticReport.Check;
import com.marketingagent.integration.naver.NaverSearchAdProperties;
import com.marketingagent.integration.naver.SearchAdClient;
import com.marketingagent.integration.naver.StatReportParser;
import com.marketingagent.integration.naver.dto.NccAdgroup;
import com.marketingagent.integration.naver.dto.NccCampaign;
import com.marketingagent.integration.naver.dto.NccKeyword;
import com.marketingagent.integration.naver.dto.ReportProbe;
import com.marketingagent.integration.naver.dto.StatRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 실계정 연결을 단계별로 점검한다. 조회만 하고 계정에는 아무것도 쓰지 않는다.
 * (리포트 점검은 네이버 쪽에 리포트 작업을 하나씩 만든다. 광고 설정은 바뀌지 않는다.)
 *
 * <p>리포트는 실제로 내려받아 컬럼 수가 공식 문서와 같은지 본다. 다르면 파싱이 어긋나
 * 숫자가 틀리게 쌓이므로 수집을 돌리기 전에 반드시 확인해야 한다.
 */
@Service
@RequiredArgsConstructor
public class AdDiagnosticsService {

    private static final List<String> REPORT_TYPES = List.of(
            StatReportParser.AD_DETAIL, StatReportParser.AD_CONVERSION_DETAIL, StatReportParser.EXPKEYWORD);

    private final SearchAdClient searchAdClient;
    private final NaverSearchAdProperties properties;

    public DiagnosticReport run() {
        List<Check> checks = new ArrayList<>();
        boolean stub = !"naver".equalsIgnoreCase(properties.provider());

        if (stub) {
            checks.add(Check.warn("연동 모드",
                    "Stub 모드입니다. 아래 결과는 가짜 데이터 기준입니다. "
                            + "실계정은 NAVER_SEARCHAD_PROVIDER=naver 와 자격증명 3종을 설정하세요."));
        } else {
            checks.add(Check.ok("연동 모드", "네이버 실계정 모드입니다."));
            if (isBlank(properties.customerId()) || isBlank(properties.apiKey()) || isBlank(properties.secretKey())) {
                checks.add(Check.fail("자격증명",
                        "NAVER_CUSTOMER_ID / NAVER_API_KEY / NAVER_SECRET_KEY 중 비어 있는 값이 있습니다."));
                return report(checks);
            }
            checks.add(Check.ok("자격증명", "3종이 모두 설정돼 있습니다."));
        }

        String keywordId = checkStructure(checks);
        checkDailyStats(checks, keywordId);
        checkReports(checks);
        return report(checks);
    }

    /** 캠페인 → 광고그룹 → 키워드를 차례로 조회한다. 통계 점검에 쓸 키워드 ID 하나를 돌려준다. */
    private String checkStructure(List<Check> checks) {
        List<NccCampaign> campaigns;
        try {
            campaigns = searchAdClient.listCampaigns();
        } catch (Exception e) {
            checks.add(Check.fail("캠페인 조회", "호출 실패: " + e.getMessage()
                    + " (자격증명과 고객 ID, 서버 시계를 확인하세요)"));
            checks.add(Check.skip("광고그룹·키워드 조회", "캠페인 조회가 실패해 건너뜁니다."));
            return null;
        }
        if (campaigns.isEmpty()) {
            checks.add(Check.warn("캠페인 조회", "호출은 됐지만 캠페인이 0개입니다. 고객 ID가 맞는지 확인하세요."));
            checks.add(Check.skip("광고그룹·키워드 조회", "캠페인이 없어 건너뜁니다."));
            return null;
        }
        checks.add(Check.ok("캠페인 조회", "캠페인 " + campaigns.size() + "개"));

        try {
            int adgroupCount = 0;
            for (NccCampaign campaign : campaigns) {
                List<NccAdgroup> adgroups = searchAdClient.listAdgroups(campaign.nccCampaignId());
                adgroupCount += adgroups.size();
                for (NccAdgroup adgroup : adgroups) {
                    List<NccKeyword> keywords = searchAdClient.listKeywords(adgroup.nccAdgroupId());
                    if (!keywords.isEmpty()) {
                        checks.add(Check.ok("광고그룹·키워드 조회",
                                "키워드가 있는 광고그룹을 찾았습니다 (키워드 " + keywords.size() + "개)."));
                        return keywords.get(0).nccKeywordId();
                    }
                }
            }
            checks.add(Check.warn("광고그룹·키워드 조회",
                    "광고그룹 " + adgroupCount + "개를 봤지만 키워드가 없습니다. 파워링크 키워드가 등록돼 있는지 확인하세요."));
        } catch (Exception e) {
            checks.add(Check.fail("광고그룹·키워드 조회", "호출 실패: " + e.getMessage()));
        }
        return null;
    }

    private void checkDailyStats(List<Check> checks, String keywordId) {
        String name = "일자별 성과 조회";
        if (keywordId == null) {
            checks.add(Check.skip(name, "점검할 키워드가 없어 건너뜁니다."));
            return;
        }
        try {
            LocalDate until = LocalDate.now().minusDays(1);
            List<StatRow> rows = searchAdClient.dailyStats(List.of(keywordId), until.minusDays(6), until);
            if (rows.isEmpty()) {
                checks.add(Check.warn(name, "호출은 됐지만 최근 7일 성과가 0줄입니다. 광고가 노출되지 않았거나 응답 형식이 다릅니다."));
            } else {
                checks.add(Check.ok(name, "최근 7일 중 " + rows.size() + "일치 성과를 받았습니다."));
            }
        } catch (Exception e) {
            checks.add(Check.fail(name, "호출 실패: " + e.getMessage()));
        }
    }

    private void checkReports(List<Check> checks) {
        LocalDate date = LocalDate.now().minusDays(1);
        for (String reportTp : REPORT_TYPES) {
            String name = "리포트 " + reportTp;
            int expected = StatReportParser.expectedColumns(reportTp);
            try {
                ReportProbe probe = searchAdClient.probeReport(reportTp, date);
                if (probe.rows() == 0) {
                    checks.add(Check.warn(name, date + " 데이터가 0줄입니다. " + emptyHint(reportTp)));
                } else if (probe.columns() != expected) {
                    checks.add(Check.fail(name, "컬럼 수가 문서와 다릅니다 (문서 " + expected + "개, 실제 "
                            + probe.columns() + "개). 이 상태로 수집하면 숫자가 틀리게 쌓입니다."));
                } else {
                    checks.add(Check.ok(name, date + " " + probe.rows() + "줄, 컬럼 " + probe.columns() + "개 (문서와 일치)"));
                }
            } catch (Exception e) {
                checks.add(Check.fail(name, "내려받기 실패: " + e.getMessage()));
            }
        }
    }

    private String emptyHint(String reportTp) {
        if (StatReportParser.AD_CONVERSION_DETAIL.equals(reportTp)) {
            return "전환추적이 설치돼 있지 않거나 그날 전환이 없었습니다. 전환이 없으면 CPA·ROAS 기준 분석을 할 수 없습니다.";
        }
        return "그날 광고가 노출되지 않았을 수 있습니다.";
    }

    private DiagnosticReport report(List<Check> checks) {
        boolean ready = checks.stream().noneMatch(check -> "FAIL".equals(check.status()));
        return new DiagnosticReport(properties.provider(), ready, checks);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
