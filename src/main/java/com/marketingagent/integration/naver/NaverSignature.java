package com.marketingagent.integration.naver;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 네이버 검색광고 API 요청 서명.
 * 서명 원문은 "{timestamp}.{method}.{path}" 이며 쿼리스트링은 제외한다.
 * 비밀키로 HMAC-SHA256 후 Base64 인코딩한 값을 X-Signature 헤더에 담는다.
 */
public final class NaverSignature {

    private static final String ALGORITHM = "HmacSHA256";

    private NaverSignature() {}

    public static String sign(String secretKey, long timestamp, String method, String path) {
        String message = timestamp + "." + method + "." + path;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("네이버 검색광고 API 서명 생성에 실패했습니다", e);
        }
    }
}
