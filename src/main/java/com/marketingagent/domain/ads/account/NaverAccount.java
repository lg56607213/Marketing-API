package com.marketingagent.domain.ads.account;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 한 명이 연결한 네이버 검색광고 계정. 회원당 하나다.
 * 액세스 라이선스와 비밀키는 암호화된 상태로만 저장한다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "naver_accounts")
public class NaverAccount extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    @Column(name = "api_key_enc", nullable = false, length = 1000)
    private String apiKeyEnc;

    @Column(name = "secret_key_enc", nullable = false, length = 1000)
    private String secretKeyEnc;

    /** 화면에 보여줄 액세스 라이선스 끝 4자리 */
    @Column(name = "api_key_tail", nullable = false, length = 10)
    private String apiKeyTail;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    public NaverAccount(Long userId, String customerId, String apiKeyEnc, String secretKeyEnc, String apiKeyTail) {
        this.userId = userId;
        update(customerId, apiKeyEnc, secretKeyEnc, apiKeyTail);
    }

    public void update(String customerId, String apiKeyEnc, String secretKeyEnc, String apiKeyTail) {
        this.customerId = customerId;
        this.apiKeyEnc = apiKeyEnc;
        this.secretKeyEnc = secretKeyEnc;
        this.apiKeyTail = apiKeyTail;
        this.connectedAt = LocalDateTime.now();
    }
}
