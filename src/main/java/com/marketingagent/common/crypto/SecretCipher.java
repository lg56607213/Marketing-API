package com.marketingagent.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 업체가 등록한 광고 API 비밀키를 DB에 평문으로 두지 않기 위한 암호화.
 * AES-256-GCM 을 쓰며, 키는 APP_ENCRYPTION_KEY 에서 파생한다.
 * 이 값을 바꾸면 기존에 저장한 비밀키는 복호화할 수 없으니 다시 입력해야 한다.
 */
@Component
public class SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.encryption-key}") String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("암호화 키 초기화에 실패했습니다", e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception e) {
            throw new IllegalStateException("비밀키 암호화에 실패했습니다", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            return new String(cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "저장된 비밀키를 복호화할 수 없습니다. APP_ENCRYPTION_KEY 가 바뀌었다면 광고 계정의 비밀키를 다시 입력하세요.", e);
        }
    }
}
