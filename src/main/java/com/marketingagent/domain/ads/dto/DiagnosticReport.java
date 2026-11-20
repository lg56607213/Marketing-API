package com.marketingagent.domain.ads.dto;

import java.util.List;

/**
 * 연결 점검 전체 결과.
 *
 * @param ready 실패 항목이 하나도 없으면 true
 */
public record DiagnosticReport(String provider, boolean ready, List<Check> checks) {

    /**
     * 점검 한 항목.
     *
     * @param status OK(정상), WARN(동작하지만 확인 필요), FAIL(실패), SKIP(앞 단계 실패로 건너뜀)
     */
    public record Check(String name, String status, String detail) {

        public static Check ok(String name, String detail) {
            return new Check(name, "OK", detail);
        }

        public static Check warn(String name, String detail) {
            return new Check(name, "WARN", detail);
        }

        public static Check fail(String name, String detail) {
            return new Check(name, "FAIL", detail);
        }

        public static Check skip(String name, String detail) {
            return new Check(name, "SKIP", detail);
        }
    }
}
