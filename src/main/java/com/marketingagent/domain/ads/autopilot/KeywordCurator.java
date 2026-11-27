package com.marketingagent.domain.ads.autopilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.ai.AiProvider;
import com.marketingagent.ai.AiRequest;
import com.marketingagent.domain.ads.AdGroup;
import com.marketingagent.domain.ads.AdGroupRepository;
import com.marketingagent.domain.ads.AdKeyword;
import com.marketingagent.domain.ads.AdKeywordRepository;
import com.marketingagent.domain.ads.KeywordStatDailyRepository;
import com.marketingagent.domain.ads.SearchQueryDailyRepository;
import com.marketingagent.domain.ads.account.AccountScope;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.Action;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.RunResult;
import com.marketingagent.domain.ads.dto.KeywordAggregate;
import com.marketingagent.integration.naver.SearchAdClient;
import com.marketingagent.integration.naver.dto.NccKeyword;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 자동 운영의 키워드 관리: 키워드 추가, 제외 키워드, 실적 없는 키워드 정리.
 *
 * <ul>
 *   <li>추가: 최근 14일 유입 검색어와 목표 상담 키워드 중 업체 상품을 살 사람이 검색할 말만 넣는다. 하루 5개까지.
 *       입찰가는 목표 순위의 추정 입찰가로 하되 클릭 1회 최대 금액을 넘지 않는다.</li>
 *   <li>제외: 업체 상품과 무관하거나 살 생각이 없는 검색어(채용, 무료 정보, 다른 업종 등)를 그 검색어가 들어온
 *       광고그룹에 확장검색 제외로 막는다. 하루 10개까지.</li>
 *   <li>정리: 자동 운영이 실제로 끄거나 추가한 키워드 중 30일 넘게 클릭이 0인 것만 지운다. 업체가 직접 만든 키워드는
 *       지우지 않는다. 한 번에 5개까지.</li>
 * </ul>
 * 판단은 업종을 아는 AI 가 하고, AI 를 쓰지 않는 서버에서는 보수적인 규칙으로 판단한다(애매하면 그대로 둔다).
 */
@Slf4j
@Component
public class KeywordCurator {

    static final String TYPE_ADD = "키워드 추가";
    static final String TYPE_NEGATIVE = "제외 키워드";
    static final String TYPE_DELETE = "키워드 삭제";
    static final String TYPE_PAUSE = "키워드 중지";

    static final int MAX_ADDS_PER_DAY = 5;
    static final int MAX_NEGATIVES_PER_DAY = 10;
    static final int MAX_DELETES_PER_RUN = 5;
    private static final int LOOKBACK_DAYS = 14;
    private static final int CLEANUP_AFTER_DAYS = 30;
    private static final int MAX_CANDIDATES = 40;
    /** 확장검색으로 들어온 엉뚱한 검색어를 막는 제외 유형. 키워드 관리 화면의 기본값과 같다. */
    private static final String NEGATIVE_TYPE = "EXP_SEARCH";

    /** AI 를 쓰지 않을 때 제외 후보로 보는 말. 업종 설명에 들어 있는 말은 빼고 쓴다. */
    private static final List<String> NEGATIVE_WORDS = List.of("채용", "구인", "알바", "아르바이트", "무료", "뜻",
            "하는법", "하는 법", "후기", "자격증", "시험", "연봉", "월급", "폐차", "고장", "수리", "부품", "중고");

    private final AdKeywordRepository keywordRepository;
    private final AdGroupRepository adGroupRepository;
    private final SearchQueryDailyRepository searchQueryRepository;
    private final KeywordStatDailyRepository statRepository;
    private final AutopilotRunRepository runRepository;
    private final AutopilotGoals goals;
    private final AiProvider aiProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    @Value("${ai.provider:stub}")
    private String aiProviderName;

    public KeywordCurator(AdKeywordRepository keywordRepository, AdGroupRepository adGroupRepository,
            SearchQueryDailyRepository searchQueryRepository, KeywordStatDailyRepository statRepository,
            AutopilotRunRepository runRepository, AutopilotGoals goals, AiProvider aiProvider,
            ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.keywordRepository = keywordRepository;
        this.adGroupRepository = adGroupRepository;
        this.searchQueryRepository = searchQueryRepository;
        this.statRepository = statRepository;
        this.runRepository = runRepository;
        this.goals = goals;
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 후보 하나. source 는 "유입 검색어" 또는 "상담 키워드". */
    record Candidate(String keyword, String source, String nccAdgroupId, Set<String> adgroups, long imp, long clk,
                     long cost) {}

    /** AI·규칙의 판단 하나. decision 은 ADD / EXCLUDE / KEEP. */
    record Judgment(String keyword, String decision, String reason) {}

    public void curate(AccountScope scope, AutopilotSettings settings, boolean preview, boolean dry, String idle,
            List<Action> actions, List<String> notes) {
        boolean add = settings == null || settings.isAddKeywords();
        boolean negatives = settings == null || settings.isAddNegatives();
        boolean cleanup = settings == null || settings.isCleanupKeywords();
        if (add || negatives) {
            addAndExclude(scope, settings, preview, dry, idle, add, negatives, actions, notes);
        }
        if (cleanup) {
            cleanup(scope, preview, dry, idle, actions, notes);
        }
    }

    // --- 추가 · 제외 ---

    private void addAndExclude(AccountScope scope, AutopilotSettings settings, boolean preview, boolean dry,
            String idle, boolean add, boolean negatives, List<Action> actions, List<String> notes) {
        List<AdGroup> adgroups = adGroupRepository.findByAdAccountIdOrderByNameAsc(scope.id());
        if (adgroups.isEmpty()) {
            notes.add("광고그룹이 없어 키워드를 추가하거나 막지 않았습니다. 네이버 광고 관리에서 캠페인과 광고그룹을 먼저 만들어 주세요.");
            return;
        }
        List<AdKeyword> registered = keywordRepository.findByAdAccountId(scope.id());
        Set<String> registeredKeys = new HashSet<>();
        registered.forEach(k -> registeredKeys.add(normalize(k.getKeyword())));

        AutopilotGoals.Goals g = goals.of(scope.id());
        List<Candidate> candidates = candidates(scope.id(), registeredKeys, g);
        if (candidates.isEmpty()) {
            return;
        }

        List<Judgment> judgments = judge(candidates, registered, g);
        Map<String, Candidate> byKey = new HashMap<>();
        candidates.forEach(c -> byKey.put(normalize(c.keyword()), c));

        int[] done = doneToday(scope.id());
        int addRoom = MAX_ADDS_PER_DAY - done[0];
        int negativeRoom = MAX_NEGATIVES_PER_DAY - done[1];
        long maxCpc = goals.maxCpc(scope.id(), settings);
        int targetRank = settings != null ? settings.getTargetRank() : 3;
        Map<String, Set<String>> existingNegatives = new HashMap<>();
        int skippedAdds = 0;
        int skippedNegatives = 0;

        for (Judgment j : judgments) {
            Candidate c = byKey.get(normalize(j.keyword()));
            if (c == null) {
                continue; // AI 가 목록에 없는 말을 지어냈다
            }
            if (add && "ADD".equals(j.decision())) {
                if (addRoom <= 0) {
                    skippedAdds++;
                    continue;
                }
                addRoom--;
                addKeyword(scope, c, j.reason(), adgroups, registered, maxCpc, targetRank, preview, dry, idle, actions);
            } else if (negatives && "EXCLUDE".equals(j.decision()) && "유입 검색어".equals(c.source())) {
                if (negativeRoom <= 0) {
                    skippedNegatives++;
                    continue;
                }
                negativeRoom--;
                exclude(scope, c, j.reason(), existingNegatives, preview, dry, idle, actions);
            }
        }
        if (skippedAdds > 0) {
            notes.add(String.format("추가할 키워드가 %d개 더 있지만 하루 %d개까지만 넣으므로 내일 이어서 판단합니다.",
                    skippedAdds, MAX_ADDS_PER_DAY));
        }
        if (skippedNegatives > 0) {
            notes.add(String.format("막을 검색어가 %d개 더 있지만 하루 %d개까지만 막으므로 내일 이어서 판단합니다.",
                    skippedNegatives, MAX_NEGATIVES_PER_DAY));
        }
    }

    /** 최근 14일 유입 검색어 중 아직 키워드가 아닌 것 + 상담에서 나온 키워드 중 아직 넣지 않은 것. */
    List<Candidate> candidates(Long accountId, Set<String> registeredKeys, AutopilotGoals.Goals g) {
        LocalDate until = LocalDate.now().minusDays(1);
        Map<String, Candidate> merged = new LinkedHashMap<>();
        for (Object[] row : searchQueryRepository.aggregateByQueryAndAdgroup(accountId, until.minusDays(LOOKBACK_DAYS - 1L), until)) {
            String query = row[0] != null ? row[0].toString().trim() : "";
            String key = normalize(query);
            if (query.isEmpty() || query.equals("-") || registeredKeys.contains(key)) {
                continue;
            }
            String adgroup = (String) row[1];
            long imp = number(row[2]);
            long clk = number(row[3]);
            long cost = number(row[4]);
            Candidate before = merged.get(key);
            if (before == null) {
                Set<String> groups = new HashSet<>();
                if (adgroup != null) {
                    groups.add(adgroup);
                }
                merged.put(key, new Candidate(query, "유입 검색어", adgroup, groups, imp, clk, cost));
            } else {
                // 같은 검색어가 여러 그룹에서 들어왔으면 합치고, 클릭이 가장 많은 그룹(먼저 온 줄)을 대표로 둔다
                if (adgroup != null) {
                    before.adgroups().add(adgroup);
                }
                merged.put(key, new Candidate(before.keyword(), before.source(), before.nccAdgroupId(),
                        before.adgroups(), before.imp() + imp, before.clk() + clk, before.cost() + cost));
            }
        }
        List<Candidate> list = new ArrayList<>(merged.values());
        list.sort(Comparator.comparingLong(Candidate::clk).thenComparingLong(Candidate::imp).reversed());
        if (list.size() > MAX_CANDIDATES) {
            list = new ArrayList<>(list.subList(0, MAX_CANDIDATES));
        }
        for (String seed : g.seedKeywords()) {
            String key = normalize(seed);
            if (!registeredKeys.contains(key) && !merged.containsKey(key)) {
                list.add(new Candidate(seed, "상담 키워드", null, new HashSet<>(), 0, 0, 0));
                merged.put(key, null);
            }
        }
        return list;
    }

    List<Judgment> judge(List<Candidate> candidates, List<AdKeyword> registered, AutopilotGoals.Goals g) {
        if (!"stub".equalsIgnoreCase(aiProviderName) && g.context() != null && !g.context().isBlank()) {
            try {
                List<Judgment> fromAi = judgeWithAi(candidates, registered, g);
                if (!fromAi.isEmpty()) {
                    return fromAi;
                }
            } catch (RuntimeException e) {
                log.warn("키워드 판단 AI 호출 실패, 규칙으로 판단합니다: {}", e.getMessage());
            }
        }
        return judgeByRules(candidates, registered, g);
    }

    private List<Judgment> judgeWithAi(List<Candidate> candidates, List<AdKeyword> registered, AutopilotGoals.Goals g) {
        String system = """
                너는 네이버 검색광고를 대신 운영하는 마케터다. 업체의 사업 설명을 보고 각 후보를 판단한다.
                - ADD: 업체 상품을 살 사람이 검색할 말이 분명할 때만. 너무 넓거나 정보만 찾는 말은 넣지 않는다.
                - EXCLUDE: 업체 상품과 무관하거나 살 생각이 없는 검색일 때만(채용·구인, 무료 정보, 뜻·방법, 다른 업종·다른 상품 등).
                  업체 상품과 관련이 있으면 클릭이 적어도 막지 않는다.
                - KEEP: 애매하면 KEEP. 상담 키워드는 EXCLUDE 하지 않는다.
                출력은 JSON 배열만: [{"keyword":"후보 그대로","decision":"ADD|EXCLUDE|KEEP","reason":"한국어 한 문장"}]
                """;
        StringBuilder user = new StringBuilder();
        user.append("[사업 설명]\n").append(g.context()).append("\n\n[이미 등록한 키워드]\n");
        registered.stream().limit(60).forEach(k -> user.append(k.getKeyword()).append(", "));
        user.append("\n\n[후보] (출처 / 최근 14일 노출·클릭·광고비)\n");
        for (Candidate c : candidates) {
            user.append(String.format("- %s (%s / 노출 %d, 클릭 %d, 광고비 %,d원)%n", c.keyword(), c.source(), c.imp(),
                    c.clk(), c.cost()));
        }
        String raw = aiProvider.generate(new AiRequest(system, user.toString(), null)).content();
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            List<Judgment> result = new ArrayList<>();
            for (JsonNode node : objectMapper.readTree(raw.substring(start, end + 1))) {
                String decision = node.path("decision").asText("KEEP").toUpperCase(Locale.ROOT);
                result.add(new Judgment(node.path("keyword").asText(), decision,
                        "AI 판단: " + node.path("reason").asText("")));
            }
            return result;
        } catch (Exception e) {
            log.warn("키워드 판단 AI 응답을 읽지 못했습니다: {}", e.getMessage());
            return List.of();
        }
    }

    /** AI 를 못 쓸 때. 업종 말과 겹치는 검색어만 넣고, 흔한 무관 단어가 든 검색어만 막는다. */
    List<Judgment> judgeByRules(List<Candidate> candidates, List<AdKeyword> registered, AutopilotGoals.Goals g) {
        Set<String> tokens = new HashSet<>();
        registered.forEach(k -> tokens.addAll(tokens(k.getKeyword())));
        g.seedKeywords().forEach(s -> tokens.addAll(tokens(s)));
        String context = g.context() == null ? "" : g.context();

        List<Judgment> result = new ArrayList<>();
        for (Candidate c : candidates) {
            if ("상담 키워드".equals(c.source())) {
                result.add(new Judgment(c.keyword(), "ADD", "목표 상담에서 고른 키워드라 넣습니다."));
                continue;
            }
            String negative = NEGATIVE_WORDS.stream()
                    .filter(w -> c.keyword().contains(w) && !context.contains(w))
                    .findFirst().orElse(null);
            if (negative != null && (c.clk() > 0 || c.imp() >= 10)) {
                result.add(new Judgment(c.keyword(), "EXCLUDE",
                        String.format("'%s'가 들어간 검색은 상품을 사려는 검색이 아니라서 막습니다.", negative)));
                continue;
            }
            boolean related = tokens(c.keyword()).stream().anyMatch(tokens::contains);
            if (related && c.clk() >= 2) {
                result.add(new Judgment(c.keyword(), "ADD",
                        String.format("최근 14일 이 검색어로 %d번 클릭됐고 등록 키워드와 같은 말을 써서 직접 키워드로 넣습니다.", c.clk())));
            }
        }
        return result;
    }

    private void addKeyword(AccountScope scope, Candidate c, String reason, List<AdGroup> adgroups,
            List<AdKeyword> registered, long maxCpc, int targetRank, boolean preview, boolean dry, String idle,
            List<Action> actions) {
        String adgroup = c.nccAdgroupId() != null ? c.nccAdgroupId() : closestAdgroup(c.keyword(), adgroups, registered);
        String groupName = adgroups.stream().filter(a -> a.getNccAdgroupId().equals(adgroup))
                .map(AdGroup::getName).findFirst().orElse(adgroup);
        Long bid = null;
        if (maxCpc > 0) {
            try {
                Long estimate = scope.client().estimateBidForPosition(List.of(c.keyword()), targetRank, "MOBILE")
                        .get(c.keyword());
                bid = estimate == null ? maxCpc : Math.min(estimate, maxCpc);
            } catch (RuntimeException e) {
                bid = maxCpc;
            }
        }
        String status = idle;
        if (!preview && !dry) {
            try {
                List<NccKeyword> created = scope.client().createKeywords(adgroup,
                        List.of(new SearchAdClient.NewKeyword(c.keyword(), bid))).stream()
                        .filter(k -> k.nccKeywordId() != null && !k.nccKeywordId().isBlank())
                        .toList();
                if (created.isEmpty()) {
                    // 네이버가 ID 를 돌려주지 않으면 등록됐는지 알 수 없다. 저장하지 않고 다음 동기화에 맡긴다
                    log.warn("자동 운영 키워드 추가: 네이버가 키워드 ID 를 돌려주지 않았습니다: {}", c.keyword());
                    status = AutopilotService.STATUS_FAILED;
                }
                tx.executeWithoutResult(s -> created.forEach(k -> keywordRepository
                        .findByAdAccountIdAndNccKeywordId(scope.id(), k.nccKeywordId())
                        .ifPresentOrElse(e -> e.update(k.keyword(), k.status(), k.bidAmt(), k.useGroupBidAmt()),
                                () -> keywordRepository.save(new AdKeyword(scope.id(), k.nccKeywordId(),
                                        k.nccAdgroupId(), k.keyword(), k.status(), k.bidAmt(), k.useGroupBidAmt())))));
            } catch (RuntimeException e) {
                log.error("자동 운영 키워드 추가 실패: {}", c.keyword(), e);
                status = AutopilotService.STATUS_FAILED;
            }
        }
        actions.add(new Action(TYPE_ADD, c.keyword(), "광고그룹 " + groupName,
                bid != null ? String.format("입찰가 %,d원", bid) : "그룹 입찰가", sourceNote(c) + reason, status));
    }

    private void exclude(AccountScope scope, Candidate c, String reason, Map<String, Set<String>> existing,
            boolean preview, boolean dry, String idle, List<Action> actions) {
        String status = idle;
        for (String adgroup : c.adgroups()) {
            if (!preview && !dry) {
                try {
                    Set<String> already = existing.computeIfAbsent(adgroup, id -> {
                        Set<String> set = new HashSet<>();
                        scope.client().listRestrictedKeywords(id).forEach(r -> set.add(normalize(r.keyword())));
                        return set;
                    });
                    if (already.add(normalize(c.keyword()))) {
                        scope.client().addRestrictedKeywords(adgroup, List.of(c.keyword()), NEGATIVE_TYPE);
                    }
                } catch (RuntimeException e) {
                    log.error("자동 운영 제외 키워드 등록 실패: {}", c.keyword(), e);
                    status = AutopilotService.STATUS_FAILED;
                }
            }
        }
        actions.add(new Action(TYPE_NEGATIVE, c.keyword(), "노출됨", "제외",
                String.format("최근 14일 노출 %d, 클릭 %d, 광고비 %,d원. %s", c.imp(), c.clk(), c.cost(), reason), status));
    }

    // --- 정리 ---

    private void cleanup(AccountScope scope, boolean preview, boolean dry, String idle, List<Action> actions,
            List<String> notes) {
        LocalDate today = LocalDate.now();
        LocalDateTime touchedBefore = today.minusDays(CLEANUP_AFTER_DAYS).atStartOfDay();
        Set<String> touched = new HashSet<>();
        for (AutopilotRun run : runRepository.findByAdAccountIdAndPreviewFalseAndDryRunFalseAndRunDateBetweenOrderByIdAsc(
                scope.id(), today.minusDays(180), today.minusDays(CLEANUP_AFTER_DAYS))) {
            RunResult result = read(run.getContentJson());
            if (result == null || result.ranAt() == null || result.ranAt().isAfter(touchedBefore)) {
                continue;
            }
            result.actions().stream()
                    .filter(a -> (TYPE_PAUSE.equals(a.type()) || TYPE_ADD.equals(a.type()))
                            && AutopilotService.STATUS_APPLIED.equals(a.status()))
                    .forEach(a -> touched.add(normalize(a.target())));
        }
        if (touched.isEmpty()) {
            return;
        }
        LocalDate yesterday = today.minusDays(1);
        Map<String, Long> clicks = new HashMap<>();
        for (KeywordAggregate row : statRepository.aggregateByKeyword(scope.id(), yesterday.minusDays(CLEANUP_AFTER_DAYS - 1L), yesterday)) {
            clicks.put(row.nccKeywordId(), row.clkCnt() != null ? row.clkCnt() : 0);
        }
        int deleted = 0;
        for (AdKeyword keyword : keywordRepository.findByAdAccountId(scope.id())) {
            if (!touched.contains(normalize(keyword.getKeyword())) || clicks.getOrDefault(keyword.getNccKeywordId(), 0L) > 0) {
                continue;
            }
            if (deleted >= MAX_DELETES_PER_RUN) {
                notes.add("정리할 키워드가 더 있지만 한 번에 " + MAX_DELETES_PER_RUN + "개까지만 지웁니다.");
                break;
            }
            deleted++;
            String status = idle;
            if (!preview && !dry) {
                try {
                    scope.client().deleteKeyword(keyword.getNccKeywordId());
                    tx.executeWithoutResult(s -> keywordRepository
                            .findByAdAccountIdAndNccKeywordId(scope.id(), keyword.getNccKeywordId())
                            .ifPresent(keywordRepository::delete));
                } catch (RuntimeException e) {
                    log.error("자동 운영 키워드 삭제 실패: {}", keyword.getKeyword(), e);
                    status = AutopilotService.STATUS_FAILED;
                }
            }
            actions.add(new Action(TYPE_DELETE, keyword.getKeyword(), "PAUSED".equals(keyword.getStatus()) ? "중지됨" : "노출 중",
                    "삭제", String.format("자동 운영이 %d일 전에 손댄 키워드인데 최근 %d일 동안 클릭이 한 번도 없어 정리합니다.",
                            CLEANUP_AFTER_DAYS, CLEANUP_AFTER_DAYS), status));
        }
    }

    // --- 보조 ---

    /** 오늘 이미 실행해서 반영(또는 기록)한 추가·제외 건수. 하루 한도를 여러 번 실행해도 지키려고. */
    private int[] doneToday(Long accountId) {
        int[] done = new int[2];
        for (AutopilotRun run : runRepository.findByAdAccountIdAndPreviewFalseAndRunDateOrderByIdAsc(accountId, LocalDate.now())) {
            RunResult result = read(run.getContentJson());
            if (result == null) {
                continue;
            }
            for (Action a : result.actions()) {
                if (AutopilotService.STATUS_FAILED.equals(a.status())) {
                    continue;
                }
                if (TYPE_ADD.equals(a.type())) {
                    done[0]++;
                } else if (TYPE_NEGATIVE.equals(a.type())) {
                    done[1]++;
                }
            }
        }
        return done;
    }

    /** 상담 키워드처럼 들어온 그룹이 없으면, 등록 키워드와 같은 말을 가장 많이 쓰는 그룹에 넣는다. */
    private String closestAdgroup(String keyword, List<AdGroup> adgroups, List<AdKeyword> registered) {
        Set<String> words = tokens(keyword);
        Map<String, Integer> score = new HashMap<>();
        Map<String, Integer> size = new HashMap<>();
        for (AdKeyword k : registered) {
            size.merge(k.getNccAdgroupId(), 1, Integer::sum);
            int overlap = (int) tokens(k.getKeyword()).stream().filter(words::contains).count();
            if (overlap > 0) {
                score.merge(k.getNccAdgroupId(), overlap, Integer::sum);
            }
        }
        return score.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                .or(() -> size.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey))
                .orElse(adgroups.get(0).getNccAdgroupId());
    }

    private RunResult read(String json) {
        try {
            return objectMapper.readValue(json, RunResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String sourceNote(Candidate c) {
        return "유입 검색어".equals(c.source())
                ? String.format("최근 14일 유입 검색어(노출 %d, 클릭 %d). ", c.imp(), c.clk())
                : "목표 상담 키워드. ";
    }

    static String normalize(String keyword) {
        return keyword == null ? "" : keyword.replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private static Set<String> tokens(String keyword) {
        Set<String> set = new HashSet<>();
        for (String t : keyword.split("\\s+")) {
            if (t.length() >= 2) {
                set.add(t.toLowerCase(Locale.ROOT));
            }
        }
        return set;
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
