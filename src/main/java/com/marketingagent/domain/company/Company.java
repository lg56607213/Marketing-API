package com.marketingagent.domain.company;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서비스를 쓰는 업체(테넌트). 사용자·브랜드·광고 계정은 모두 업체에 속하며,
 * 다른 업체의 데이터는 조회도 변경도 할 수 없다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "companies")
public class Company extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    public Company(String name) {
        this.name = name;
    }
}
