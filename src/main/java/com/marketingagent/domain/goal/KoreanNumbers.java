package com.marketingagent.domain.goal;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 대화에서 사람이 쓴 금액·비율·건수를 숫자로 읽는다. "40만원", "2천5백만", "1,200,000원", "3억", "30%", "10건 중 2건" 같은 표현을 다룬다.
 * 읽지 못하면 비워 두고, 추측해서 채우지 않는다.
 */
public final class KoreanNumbers {

    private static final Pattern RATIO = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*건?\\s*(?:중|에)\\s*(?:에\\s*)?(\\d+(?:\\.\\d+)?)\\s*건?");
    private static final Pattern PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:%|퍼센트|프로)");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
    /** 숫자 + 선택적 천/백/십 + 선택적 큰 단위(억/만) 를 한 덩어리로 읽는다. */
    private static final Pattern MONEY_PART = Pattern.compile("(\\d+(?:\\.\\d+)?)?\\s*(천|백|십)?\\s*(억|만)?");

    private KoreanNumbers() {
    }

    /** 금액(원). "월 2천만원" → 20,000,000, "40만" → 400,000, "35000원" → 35,000. */
    public static Optional<Long> money(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String s = text.replace(",", "").replace(" ", "");
        // 퍼센트가 붙은 숫자는 금액이 아니다.
        s = PERCENT.matcher(s).replaceAll("");
        Matcher start = Pattern.compile("\\d|[천백십](?=억|만|원)").matcher(s);
        if (!start.find()) {
            return Optional.empty();
        }
        s = s.substring(start.start());

        double total = 0;
        double pending = 0;
        boolean any = false;
        Matcher m = MONEY_PART.matcher(s);
        int pos = 0;
        while (pos < s.length() && m.find(pos) && m.start() == pos && m.end() > pos) {
            String num = m.group(1);
            String small = m.group(2);
            String big = m.group(3);
            double value = num != null ? Double.parseDouble(num) : (small != null ? 1 : 0);
            if (small != null) {
                value *= switch (small) {
                    case "천" -> 1_000;
                    case "백" -> 100;
                    default -> 10;
                };
            }
            pending += value;
            if (big != null) {
                total += pending * ("억".equals(big) ? 100_000_000L : 10_000L);
                pending = 0;
            }
            any = true;
            pos = m.end();
        }
        if (!any) {
            return Optional.empty();
        }
        long result = Math.round(total + pending);
        return result > 0 ? Optional.of(result) : Optional.empty();
    }

    /** 비율(%). "30%", "30프로", "10건 중 2건"(→20), "0.2"(→20) 처럼 쓴다. */
    public static Optional<Double> percent(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String s = text.replace(",", "");
        Matcher ratio = RATIO.matcher(s);
        if (ratio.find()) {
            double whole = Double.parseDouble(ratio.group(1));
            double part = Double.parseDouble(ratio.group(2));
            if (whole > 0 && part <= whole) {
                return Optional.of(round2(part * 100.0 / whole));
            }
        }
        Matcher pct = PERCENT.matcher(s);
        if (pct.find()) {
            double value = Double.parseDouble(pct.group(1));
            return value > 0 && value <= 100 ? Optional.of(value) : Optional.empty();
        }
        Matcher plain = PLAIN_NUMBER.matcher(s);
        if (plain.find()) {
            double value = Double.parseDouble(plain.group());
            if (value > 0 && value < 1) {
                return Optional.of(round2(value * 100));
            }
            if (value >= 1 && value <= 100) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** 건수나 횟수. "20건" → 20, "1.5번" → 1.5. */
    public static Optional<Double> count(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher plain = PLAIN_NUMBER.matcher(text.replace(",", ""));
        if (plain.find()) {
            double value = Double.parseDouble(plain.group());
            return value > 0 ? Optional.of(value) : Optional.empty();
        }
        return Optional.empty();
    }

    /** "모름", "몰라요", "잘 모르겠" 처럼 답을 모른다는 말인지. */
    public static boolean unknown(String text) {
        if (text == null) {
            return false;
        }
        String s = text.replace(" ", "");
        return s.contains("모름") || s.contains("모르") || s.contains("몰라") || s.contains("글쎄") || s.equals("?");
    }

    /** "없음", "없어요", "상관없" 처럼 해당 없다는 말인지. */
    public static boolean none(String text) {
        if (text == null) {
            return false;
        }
        String s = text.replace(" ", "");
        return s.startsWith("없") || s.contains("없음") || s.contains("없어") || s.contains("상관없") || s.contains("제한없");
    }

    /** "네", "예", "좋아요", "맞아요" 처럼 동의하는 말인지. */
    public static boolean yes(String text) {
        if (text == null) {
            return false;
        }
        String s = text.replace(" ", "");
        return s.startsWith("네") || s.startsWith("예") || s.startsWith("응") || s.startsWith("좋") || s.startsWith("맞")
                || s.startsWith("그래") || s.equalsIgnoreCase("ok") || s.equalsIgnoreCase("yes");
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
