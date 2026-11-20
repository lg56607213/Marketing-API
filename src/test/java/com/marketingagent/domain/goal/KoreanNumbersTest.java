package com.marketingagent.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KoreanNumbersTest {

    @Test
    void readsMoneyTheWayPeopleWriteIt() {
        assertThat(KoreanNumbers.money("40만원")).contains(400_000L);
        assertThat(KoreanNumbers.money("월 2천만원 정도요")).contains(20_000_000L);
        assertThat(KoreanNumbers.money("2천5백만")).contains(25_000_000L);
        assertThat(KoreanNumbers.money("1,200,000원")).contains(1_200_000L);
        assertThat(KoreanNumbers.money("3억")).contains(300_000_000L);
        assertThat(KoreanNumbers.money("1억 2천만원")).contains(120_000_000L);
        assertThat(KoreanNumbers.money("천만원")).contains(10_000_000L);
        assertThat(KoreanNumbers.money("2.5만")).contains(25_000L);
        assertThat(KoreanNumbers.money("35000")).contains(35_000L);
        // 비율은 금액이 아니다.
        assertThat(KoreanNumbers.money("수수료 30%")).isEmpty();
        assertThat(KoreanNumbers.money("50만원 중 30%")).contains(500_000L);
        assertThat(KoreanNumbers.money("잘 모르겠어요")).isEmpty();
    }

    @Test
    void readsRatesAsPercent() {
        assertThat(KoreanNumbers.percent("10건 중 3건")).contains(30.0);
        assertThat(KoreanNumbers.percent("문의 10건에 2건 정도 계약돼요")).contains(20.0);
        assertThat(KoreanNumbers.percent("30%")).contains(30.0);
        assertThat(KoreanNumbers.percent("25프로")).contains(25.0);
        assertThat(KoreanNumbers.percent("0.15")).contains(15.0);
        assertThat(KoreanNumbers.percent("모름")).isEmpty();
        assertThat(KoreanNumbers.percent("150%")).isEmpty();
    }

    @Test
    void recognisesShortAnswers() {
        assertThat(KoreanNumbers.unknown("잘 모르겠어요")).isTrue();
        assertThat(KoreanNumbers.unknown("30%")).isFalse();
        assertThat(KoreanNumbers.none("없어요")).isTrue();
        assertThat(KoreanNumbers.none("200만원")).isFalse();
        assertThat(KoreanNumbers.yes("네 좋아요")).isTrue();
        assertThat(KoreanNumbers.yes("아니요")).isFalse();
        assertThat(KoreanNumbers.count("계약 20건")).contains(20.0);
    }
}
