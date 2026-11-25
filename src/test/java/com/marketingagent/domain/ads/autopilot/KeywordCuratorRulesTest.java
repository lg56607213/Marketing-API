package com.marketingagent.domain.ads.autopilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketingagent.domain.ads.AdKeyword;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** AI 를 쓰지 않는 서버에서 키워드를 판단하는 규칙. 애매하면 손대지 않는 것이 원칙이다. */
class KeywordCuratorRulesTest {

    private final KeywordCurator curator = new KeywordCurator(null, null, null, null, null, null, null, null,
            new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
                }

                @Override
                protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {
                }

                @Override
                protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {
                }
            });

    @Test
    void addsRelatedClickedQueriesExcludesJobSearchesAndKeepsIndustryWords() {
        List<AdKeyword> registered = List.of(new AdKeyword(1L, "k1", "g1", "화물차 리스", "ELIGIBLE", 500L, false));
        AutopilotGoals.Goals goals = new AutopilotGoals.Goals(49500, 165000, 3_000_000, "중고 화물차 리스",
                "업종: 중고 화물차 리스", List.of("특장차 리스 견적"));
        List<KeywordCurator.Candidate> candidates = List.of(
                candidate("화물차 리스 조건", 3, 40),      // 등록 키워드와 같은 말 + 클릭 3 → 추가
                candidate("화물차 기사 채용", 2, 30),      // 채용 → 제외
                candidate("중고 화물차 리스", 1, 20),      // '중고'는 업종 설명에 있으므로 막지 않는다. 클릭 1이라 추가도 안 함
                candidate("날씨", 0, 3),                   // 무관하지만 근거가 약하면 그대로 둔다
                new KeywordCurator.Candidate("특장차 리스 견적", "상담 키워드", null, new HashSet<>(), 0, 0, 0));

        Map<String, String> decisions = curator.judgeByRules(candidates, registered, goals).stream()
                .collect(Collectors.toMap(KeywordCurator.Judgment::keyword, KeywordCurator.Judgment::decision));

        assertThat(decisions).containsEntry("화물차 리스 조건", "ADD")
                .containsEntry("화물차 기사 채용", "EXCLUDE")
                .containsEntry("특장차 리스 견적", "ADD")
                .doesNotContainKey("중고 화물차 리스")
                .doesNotContainKey("날씨");
    }

    private static KeywordCurator.Candidate candidate(String query, long clicks, long impressions) {
        return new KeywordCurator.Candidate(query, "유입 검색어", "g1", new HashSet<>(List.of("g1")), impressions, clicks,
                clicks * 1000);
    }
}
