package com.marketingagent.domain.ads.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.AdKeywordRepository;
import com.marketingagent.domain.ads.KeywordStatDailyRepository;
import com.marketingagent.domain.ads.account.AccountScope;
import com.marketingagent.domain.ads.account.AdAccountService;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.Action;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.RunResult;
import com.marketingagent.domain.ads.autopilot.AutopilotRun;
import com.marketingagent.domain.ads.autopilot.AutopilotRunRepository;
import com.marketingagent.domain.ads.autopilot.AutopilotService;
import com.marketingagent.domain.ads.dto.KeywordAggregate;
import com.marketingagent.domain.ads.planner.BriefingService;
import com.marketingagent.domain.ads.planner.PlannerDtos.Briefing;
import com.marketingagent.domain.ads.planner.PlannerDtos.BriefingPeriod;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 매일 아침 대표에게 보내는 광고 보고 메일.
 *
 * <p>어제 하루 실적(노출·클릭·클릭률·전환·전환율·광고비), 남은 광고비(비즈머니)로 며칠 더 버틸 수 있는지,
 * 이번 달 지출과 한도, 자동 운영이 한 일, 그리고 "오늘 대표가 할 일"을 한 장에 담는다.
 * 광고를 잘 모르는 사람도 메일만 보고 충전·증액·유지를 정할 수 있게 쓴다.
 */
@Slf4j
@Service
public class DailyReportService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_RECIPIENTS = 10;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN);

    private final ReportSettingsRepository settingsRepository;
    private final ReportMailer mailer;
    private final AdAccountService accountService;
    private final KeywordStatDailyRepository statRepository;
    private final AdKeywordRepository keywordRepository;
    private final AutopilotRunRepository runRepository;
    private final AutopilotService autopilotService;
    private final BriefingService briefingService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    @Value("${app.public-url:https://marketagent.co.kr}")
    private String publicUrl;

    public DailyReportService(ReportSettingsRepository settingsRepository, ReportMailer mailer,
            AdAccountService accountService, KeywordStatDailyRepository statRepository,
            AdKeywordRepository keywordRepository, AutopilotRunRepository runRepository,
            AutopilotService autopilotService, BriefingService briefingService, ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.settingsRepository = settingsRepository;
        this.mailer = mailer;
        this.accountService = accountService;
        this.statRepository = statRepository;
        this.keywordRepository = keywordRepository;
        this.runRepository = runRepository;
        this.autopilotService = autopilotService;
        this.briefingService = briefingService;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // --- 설정 ---

    public record SettingsView(boolean enabled, List<String> recipients, LocalDate lastSentOn, boolean mailConfigured,
                               String sender) {}

    public record SettingsRequest(boolean enabled, List<String> recipients) {}

    public record Preview(String subject, String html) {}

    public SettingsView settings(Long accountId) {
        AccountScope scope = accountService.scope(accountId);
        ReportSettings s = settingsRepository.findByAdAccountId(scope.id()).orElse(null);
        return new SettingsView(s == null || s.isEnabled(), s == null ? List.of() : s.recipientList(),
                s == null ? null : s.getLastSentOn(), mailer.configured(), mailer.sender());
    }

    public SettingsView updateSettings(Long accountId, SettingsRequest request) {
        AccountScope scope = accountService.scope(accountId);
        List<String> recipients = request.recipients() == null ? List.of() : request.recipients().stream()
                .map(String::trim).filter(r -> !r.isEmpty()).distinct().toList();
        if (recipients.size() > MAX_RECIPIENTS) {
            throw new BadRequestException("받는 사람은 " + MAX_RECIPIENTS + "명까지 정할 수 있습니다.");
        }
        recipients.stream().filter(r -> !EMAIL.matcher(r).matches()).findFirst().ifPresent(bad -> {
            throw new BadRequestException("이메일 주소 형식이 아닙니다: " + bad);
        });
        tx.executeWithoutResult(status -> {
            ReportSettings s = settingsRepository.findByAdAccountId(scope.id())
                    .orElseGet(() -> settingsRepository.save(new ReportSettings(scope.id())));
            s.update(request.enabled(), recipients);
        });
        log.info("보고 메일 설정 변경: 계정 {} 받는 사람 {}명 사용={}", scope.id(), recipients.size(), request.enabled());
        return settings(scope.id());
    }

    // --- 발송 ---

    /** 일 배치에서 부른다. 켜 두고 받는 사람이 있으며 오늘 아직 안 보냈을 때만 보낸다. */
    public boolean sendIfDue(Long accountId) {
        AccountScope scope = accountService.scope(accountId);
        ReportSettings s = settingsRepository.findByAdAccountId(scope.id()).orElse(null);
        LocalDate today = LocalDate.now();
        if (s == null || !s.isEnabled() || s.recipientList().isEmpty() || today.equals(s.getLastSentOn())) {
            return false;
        }
        if (!mailer.configured()) {
            log.warn("메일 서버가 설정되지 않아 보고 메일을 보내지 않았습니다: 계정 {}", scope.id());
            return false;
        }
        Preview mail = preview(scope.id());
        mailer.send(s.recipientList(), mail.subject(), mail.html());
        tx.executeWithoutResult(status -> settingsRepository.findByAdAccountId(scope.id())
                .ifPresent(found -> found.markSent(today)));
        return true;
    }

    /** 화면의 "지금 보내 보기". 정해 둔 받는 사람에게 바로 보낸다. 오늘 보낸 기록은 남기지 않는다. */
    public List<String> sendNow(Long accountId) {
        AccountScope scope = accountService.scope(accountId);
        List<String> to = settingsRepository.findByAdAccountId(scope.id())
                .map(ReportSettings::recipientList).orElse(List.of());
        if (to.isEmpty()) {
            throw new BadRequestException("받는 사람 이메일을 먼저 저장해 주세요.");
        }
        Preview mail = preview(scope.id());
        mailer.send(to, mail.subject(), mail.html());
        return to;
    }

    public Preview preview(Long accountId) {
        DailyReport report = build(accountId);
        return new Preview(subject(report), render(report));
    }

    // --- 보고서 계산 ---

    /** 하루 숫자. */
    record Numbers(long imp, long clk, long cost, long conv) {
        double ctr() { return imp == 0 ? 0 : clk * 100.0 / imp; }
        double cvr() { return clk == 0 ? 0 : conv * 100.0 / clk; }
        long cpc() { return clk == 0 ? 0 : cost / clk; }
        long cpa() { return conv == 0 ? 0 : cost / conv; }
        Numbers perDay(int days) { return new Numbers(imp / days, clk / days, cost / days, conv / days); }
    }

    record KeywordLine(String keyword, long clk, long cost, long conv) {}

    /**
     * @param balance  비즈머니 잔액. 모르면 -1
     * @param daysLeft 잔액으로 버틸 수 있는 날수. 계산할 수 없으면 null
     */
    record DailyReport(String accountName, LocalDate day, Numbers yesterday, Numbers base, long balance,
                       long avgDailySpend, Integer daysLeft, long monthSpent, long monthCap, long projectedMonth,
                       String verdict, String verdictReason, List<String> todos, String autopilotSummary,
                       List<Action> autopilotActions, boolean recordOnly, List<KeywordLine> topKeywords) {}

    DailyReport build(Long accountId) {
        AccountScope scope = accountService.scope(accountId);
        LocalDate today = LocalDate.now();
        LocalDate day = today.minusDays(1);

        Numbers yesterday = sum(statRepository.aggregateByKeyword(scope.id(), day, day));
        Numbers base = sum(statRepository.aggregateByKeyword(scope.id(), day.minusDays(7), day.minusDays(1))).perDay(7);
        Numbers last7 = sum(statRepository.aggregateByKeyword(scope.id(), day.minusDays(6), day));
        Numbers last14 = sum(statRepository.aggregateByKeyword(scope.id(), day.minusDays(13), day));
        long avgDailySpend = last7.cost() / 7;

        long balance;
        try {
            balance = scope.client().bizmoneyBalance();
        } catch (RuntimeException e) {
            balance = -1;
        }
        Integer daysLeft = balance >= 0 && avgDailySpend > 0 ? (int) (balance / avgDailySpend) : null;

        LocalDate monthStart = day.withDayOfMonth(1);
        long monthSpent = sum(statRepository.aggregateByKeyword(scope.id(), monthStart, day)).cost();
        long monthCap = autopilotService.monthlyCap(scope.id());
        long projectedMonth = monthSpent * day.lengthOfMonth() / day.getDayOfMonth();

        Briefing briefing = briefingService.recent(scope.id()).stream()
                .filter(b -> b.period() == BriefingPeriod.DAILY && day.equals(b.until()))
                .findFirst().orElse(null);
        String verdict = briefing == null || briefing.verdict() == null ? null : switch (briefing.verdict()) {
            case INCREASE -> "광고비를 늘려도 됩니다";
            case HOLD -> "지금 수준을 유지하세요";
            case DECREASE -> "광고비를 줄이거나 새는 곳부터 막으세요";
            case NEED_DATA -> "아직 판단할 데이터가 모자랍니다";
        };

        List<Action> actions = new ArrayList<>();
        for (AutopilotRun run : runRepository.findByAdAccountIdAndPreviewFalseAndRunDateBetweenOrderByIdAsc(scope.id(), day, today)) {
            RunResult result = read(run.getContentJson());
            if (result != null) {
                actions.addAll(result.actions());
            }
        }
        Map<String, Long> byType = new LinkedHashMap<>();
        actions.forEach(a -> byType.merge(a.type(), 1L, Long::sum));
        List<String> summaries = new ArrayList<>();
        byType.forEach((type, count) -> summaries.add(type + " " + count + "건"));
        boolean recordOnly = autopilotService.recordOnly(scope.id());

        List<String> todos = todos(balance, daysLeft, last14, briefing, verdict, monthCap, projectedMonth, recordOnly);

        Map<String, String> names = new HashMap<>();
        keywordRepository.findByAdAccountId(scope.id()).forEach(k -> names.put(k.getNccKeywordId(), k.getKeyword()));
        List<KeywordLine> top = statRepository.aggregateByKeyword(scope.id(), day, day).stream()
                .sorted(Comparator.comparingLong((KeywordAggregate r) -> nz(r.salesAmt())).reversed())
                .filter(r -> nz(r.salesAmt()) > 0 || nz(r.clkCnt()) > 0)
                .limit(5)
                .map(r -> new KeywordLine(names.getOrDefault(r.nccKeywordId(), r.nccKeywordId()), nz(r.clkCnt()),
                        nz(r.salesAmt()), nz(r.ccnt())))
                .toList();

        return new DailyReport(scope.account().getName(), day, yesterday, base, balance, avgDailySpend, daysLeft,
                monthSpent, monthCap, projectedMonth, verdict, briefing == null ? null : briefing.verdictReason(),
                todos, summaries.isEmpty() ? "어제부터 지금까지 자동 운영이 바꾼 것은 없습니다." : String.join(", ", summaries),
                actions.size() > 20 ? actions.subList(actions.size() - 20, actions.size()) : actions, recordOnly, top);
    }

    /** 대표가 오늘 해야 할 일. 급한 것부터. */
    private List<String> todos(long balance, Integer daysLeft, Numbers last14, Briefing briefing, String verdict,
            long monthCap, long projectedMonth, boolean recordOnly) {
        List<String> todos = new ArrayList<>();
        if (balance == 0) {
            todos.add("🚨 비즈머니 잔액이 0원입니다. 충전해야 광고가 나갑니다.");
        } else if (daysLeft != null && daysLeft < 3) {
            todos.add(String.format("🚨 남은 광고비(비즈머니) %,d원으로 약 %d일 치입니다. 오늘 충전하지 않으면 광고가 멈춥니다.", balance, daysLeft));
        } else if (daysLeft != null && daysLeft < 7) {
            todos.add(String.format("⚠️ 남은 광고비(비즈머니) %,d원으로 약 %d일 치입니다. 이번 주 안에 충전해 주세요.", balance, daysLeft));
        }
        if (last14.clk() >= 20 && last14.conv() == 0) {
            todos.add(String.format("⚠️ 최근 14일 클릭 %,d회인데 네이버에 잡힌 문의(전환)가 0건입니다. 실제 문의가 있었다면 홈페이지에 네이버 전환 추적이 설치되지 않은 것입니다. "
                    + "전환이 잡혀야 자동 운영이 돈이 되는 키워드에 더 쓰고 새는 키워드를 막을 수 있습니다.", last14.clk()));
        }
        if (monthCap > 0 && projectedMonth > monthCap * 1.05) {
            todos.add(String.format("이대로면 이번 달 약 %,d원을 써서 한도 %,d원을 넘습니다. 자동 운영이 일예산과 입찰가를 낮춰 한도를 지킵니다.",
                    projectedMonth, monthCap));
        }
        if (verdict != null) {
            todos.add("광고비 판단: " + verdict + ". " + (briefing.verdictReason() != null ? briefing.verdictReason() : ""));
        }
        if (recordOnly) {
            todos.add("지금은 시험 모드라 자동 운영이 바꾸려는 내용을 기록만 하고 네이버에는 반영하지 않습니다. 아래 기록을 보시고 괜찮으면 실제 반영으로 바꾸세요.");
        }
        if (todos.isEmpty()) {
            todos.add("특별히 하실 일은 없습니다. 자동 운영이 계속 지켜봅니다.");
        }
        return todos;
    }

    // --- 메일 본문 ---

    String subject(DailyReport r) {
        String alert = r.balance() == 0 || (r.daysLeft() != null && r.daysLeft() < 3) ? "[충전 필요] " : "";
        return String.format("%s[%s] %s 광고 보고 · 광고비 %,d원 · 클릭 %,d · 전환 %,d", alert, r.accountName(),
                r.day().format(DateTimeFormatter.ofPattern("M/d")), r.yesterday().cost(), r.yesterday().clk(),
                r.yesterday().conv());
    }

    String render(DailyReport r) {
        Numbers y = r.yesterday();
        Numbers b = r.base();
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>")
                .append("<body style=\"margin:0;background:#f4f5f7;font-family:-apple-system,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;color:#1f2937;\">")
                .append("<div style=\"max-width:640px;margin:0 auto;padding:20px 14px;\">");
        h.append("<p style=\"margin:0 0 4px;font-size:13px;color:#6b7280;\">").append(esc(r.accountName())).append(" · ")
                .append(r.day().format(DAY)).append(" 실적</p>")
                .append("<h1 style=\"margin:0 0 16px;font-size:20px;\">어제 광고 보고</h1>");

        h.append(card("오늘 확인하실 일"));
        h.append("<ul style=\"margin:0;padding-left:18px;line-height:1.6;font-size:14px;\">");
        r.todos().forEach(t -> h.append("<li style=\"margin-bottom:6px;\">").append(esc(t)).append("</li>"));
        h.append("</ul></div>");

        h.append(card("어제 실적 (괄호는 직전 7일 하루 평균)"));
        h.append("<table role=\"presentation\" style=\"width:100%;border-collapse:collapse;font-size:14px;\">");
        row(h, "광고비(소진)", won(y.cost()), won(b.cost()));
        row(h, "노출", num(y.imp()), num(b.imp()));
        row(h, "클릭", num(y.clk()), num(b.clk()));
        row(h, "클릭률", pct(y.ctr()), pct(b.ctr()));
        row(h, "전환(문의)", num(y.conv()), num(b.conv()));
        row(h, "전환율", pct(y.cvr()), pct(b.cvr()));
        row(h, "클릭당 비용", won(y.cpc()), won(b.cpc()));
        row(h, "전환당 비용", y.conv() == 0 ? "-" : won(y.cpa()), b.conv() == 0 ? "-" : won(b.cpa()));
        h.append("</table></div>");

        h.append(card("광고비 잔액과 이번 달 지출"));
        h.append("<table role=\"presentation\" style=\"width:100%;border-collapse:collapse;font-size:14px;\">");
        row(h, "남은 광고비(비즈머니)", r.balance() >= 0 ? won(r.balance()) : "확인 못함", null);
        row(h, "최근 7일 하루 평균 지출", won(r.avgDailySpend()), null);
        row(h, "잔액으로 버틸 수 있는 날", r.daysLeft() != null ? "약 " + r.daysLeft() + "일" : "-", null);
        row(h, "이번 달 쓴 돈", won(r.monthSpent()), null);
        row(h, "이번 달 예상 지출", won(r.projectedMonth()), null);
        row(h, "한 달 한도", r.monthCap() > 0 ? won(r.monthCap()) : "정하지 않음", null);
        h.append("</table></div>");

        h.append(card("자동 운영이 한 일"));
        h.append("<p style=\"margin:0 0 8px;font-size:14px;\">").append(esc(r.autopilotSummary()))
                .append(r.recordOnly() ? " <span style=\"color:#b45309;\">(시험 모드: 기록만)</span>" : "").append("</p>");
        if (!r.autopilotActions().isEmpty()) {
            h.append("<table role=\"presentation\" style=\"width:100%;border-collapse:collapse;font-size:13px;\">");
            for (Action a : r.autopilotActions()) {
                h.append("<tr><td style=\"padding:6px 0;border-top:1px solid #eef0f3;vertical-align:top;\"><b>")
                        .append(esc(a.type())).append("</b> ").append(esc(a.target()))
                        .append("<br><span style=\"color:#6b7280;\">").append(esc(a.before())).append(" → ")
                        .append(esc(a.after())).append(" · ").append(esc(a.status())).append("</span></td></tr>");
            }
            h.append("</table>");
        }
        h.append("</div>");

        if (!r.topKeywords().isEmpty()) {
            h.append(card("어제 돈을 많이 쓴 키워드"));
            h.append("<table role=\"presentation\" style=\"width:100%;border-collapse:collapse;font-size:13px;\">")
                    .append("<tr style=\"color:#6b7280;\"><td>키워드</td><td style=\"text-align:right;\">클릭</td>")
                    .append("<td style=\"text-align:right;\">광고비</td><td style=\"text-align:right;\">전환</td></tr>");
            for (KeywordLine k : r.topKeywords()) {
                h.append("<tr><td style=\"padding:6px 0;border-top:1px solid #eef0f3;\">").append(esc(k.keyword()))
                        .append("</td><td style=\"text-align:right;border-top:1px solid #eef0f3;\">").append(num(k.clk()))
                        .append("</td><td style=\"text-align:right;border-top:1px solid #eef0f3;\">").append(won(k.cost()))
                        .append("</td><td style=\"text-align:right;border-top:1px solid #eef0f3;\">").append(num(k.conv()))
                        .append("</td></tr>");
            }
            h.append("</table></div>");
        }

        h.append("<p style=\"text-align:center;margin:20px 0 6px;\"><a href=\"").append(esc(publicUrl))
                .append("\" style=\"display:inline-block;background:#4f46e5;color:#fff;text-decoration:none;padding:10px 18px;border-radius:8px;font-size:14px;\">자세히 보기</a></p>")
                .append("<p style=\"text-align:center;font-size:12px;color:#9ca3af;margin:0;\">마케팅 에이전트가 매일 아침 보내 드리는 메일입니다. 받는 사람은 자동 운영 화면에서 바꿀 수 있습니다.</p>")
                .append("</div></body></html>");
        return h.toString();
    }

    private static String card(String title) {
        return "<div style=\"background:#fff;border-radius:10px;padding:16px;margin-bottom:12px;\">"
                + "<h2 style=\"margin:0 0 10px;font-size:15px;\">" + esc(title) + "</h2>";
    }

    private static void row(StringBuilder h, String label, String value, String compare) {
        h.append("<tr><td style=\"padding:6px 0;border-top:1px solid #eef0f3;color:#4b5563;\">").append(esc(label))
                .append("</td><td style=\"padding:6px 0;border-top:1px solid #eef0f3;text-align:right;font-weight:600;\">")
                .append(esc(value));
        if (compare != null) {
            h.append(" <span style=\"color:#9ca3af;font-weight:400;\">(").append(esc(compare)).append(")</span>");
        }
        h.append("</td></tr>");
    }

    // --- 보조 ---

    private Numbers sum(List<KeywordAggregate> rows) {
        long imp = 0;
        long clk = 0;
        long cost = 0;
        long conv = 0;
        for (KeywordAggregate r : rows) {
            imp += nz(r.impCnt());
            clk += nz(r.clkCnt());
            cost += nz(r.salesAmt());
            conv += nz(r.ccnt());
        }
        return new Numbers(imp, clk, cost, conv);
    }

    private RunResult read(String json) {
        try {
            return objectMapper.readValue(json, RunResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    private static String won(long v) {
        return String.format("%,d원", v);
    }

    private static String num(long v) {
        return String.format("%,d", v);
    }

    private static String pct(double v) {
        return String.format("%.2f%%", v);
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
