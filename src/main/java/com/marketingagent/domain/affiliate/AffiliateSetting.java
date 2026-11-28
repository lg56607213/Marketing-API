package com.marketingagent.domain.affiliate;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 업체별 네이버 검색 API 키. Secret 은 암호화해서만 둔다. */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "affiliate_settings")
public class AffiliateSetting extends BaseEntity {

    @Column(nullable = false, unique = true)
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String naverClientId;

    @Column(nullable = false, length = 500)
    private String naverClientSecretEnc;

    private LocalDateTime verifiedAt;

    private Long updatedBy;

    public AffiliateSetting(Long companyId) {
        this.companyId = companyId;
    }

    public void change(String clientId, String secretEnc, Long by) {
        this.naverClientId = clientId;
        this.naverClientSecretEnc = secretEnc;
        this.verifiedAt = LocalDateTime.now();
        this.updatedBy = by;
    }
}
