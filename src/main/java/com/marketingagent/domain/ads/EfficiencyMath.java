package com.marketingagent.domain.ads;

/**
 * 광고비 대비 효율 계산에 쓰는 공식 모음. 계획·입찰가 계산·자동 운영이 같은 식을 쓰게 한 곳에 둔다.
 */
public final class EfficiencyMath {

    /** 90% 신뢰구간에 해당하는 z 값. */
    public static final double Z90 = 1.645;

    private EfficiencyMath() {
    }

    /**
     * 비율의 윌슨 신뢰구간. 표본이 적을수록 구간이 넓어져서 "몇 건 안 되는 실적"을 과신하지 않게 한다.
     *
     * @return [하한, 상한] (0~1)
     */
    public static double[] wilson(long successes, long trials, double z) {
        if (trials <= 0) {
            return new double[] {0.0, 1.0};
        }
        double p = Math.min(1.0, successes / (double) trials);
        double z2 = z * z;
        double denominator = 1 + z2 / trials;
        double center = (p + z2 / (2 * trials)) / denominator;
        double margin = z * Math.sqrt(p * (1 - p) / trials + z2 / (4.0 * trials * trials)) / denominator;
        return new double[] {Math.max(0.0, center - margin), Math.min(1.0, center + margin)};
    }

    /**
     * 키워드 전환율을 계정 평균 쪽으로 당겨 잡는다. 클릭이 적은 키워드는 우연히 전환이 0건이거나 몰릴 수 있어서,
     * 계정 평균을 priorClicks 회의 가상 클릭으로 섞는다. 클릭이 쌓일수록 키워드 자신의 값에 가까워진다.
     *
     * @return 전환율 (0~1)
     */
    public static double shrunkRate(long conversions, long clicks, double accountRate, double priorClicks) {
        double denominator = clicks + priorClicks;
        return denominator <= 0 ? accountRate : (conversions + accountRate * priorClicks) / denominator;
    }

    /**
     * 전환 1건의 가치와 목표 수익률에서 클릭 한 번에 쓸 수 있는 최대 금액을 구한다.
     *
     * @param conversionRate   전환율 (0~1)
     * @param valuePerConversion 전환 1건에 쓸 수 있는 돈. 목표 CPA 이거나 (전환 1건 매출 ÷ 목표 수익률)
     */
    public static long maxCpc(double conversionRate, double valuePerConversion) {
        return Math.round(conversionRate * valuePerConversion);
    }

    /** 목표 광고수익률(%)에서 전환 1건에 쓸 수 있는 돈. 수익률 100% 가 본전이다. */
    public static double allowableCpa(double revenuePerConversion, double targetRoasPct) {
        double roas = targetRoasPct > 0 ? targetRoasPct : 100.0;
        return revenuePerConversion * 100.0 / roas;
    }
}
