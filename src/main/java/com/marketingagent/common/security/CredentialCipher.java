package com.marketingagent.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 회원이 맡긴 외부 서비스 키를 DB 에 넣기 전에 암호화한다 (AES-256-GCM).
 * 암호화 키는 CREDENTIAL_ENCRYPTION_KEY 환경변수에서 온다. 이 값을 바꾸면 기존에 저장된 키는 풀 수 없다.
 */
@Slf4j
@Component
public class CredentialCipher {

    static final String DEFAULT_KEY = "dev-only-credential-key-change-me";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CredentialCipher(@Value("${security.credential-key:" + DEFAULT_KEY + "}") String secret) {
        if (DEFAULT_KEY.equals(secret)) {
            log.warn("CREDENTIAL_ENCRYPTION_KEY 가 기본값입니다. 운영 환경에서는 반드시 고유한 값으로 바꾸세요.");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화 키를 준비하지 못했습니다", e);
        }
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화에 실패했습니다", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] raw = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            return new String(cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "저장된 키를 풀 수 없습니다. CREDENTIAL_ENCRYPTION_KEY 가 바뀌었다면 계정을 다시 연결하세요.", e);
        }
    }
}
