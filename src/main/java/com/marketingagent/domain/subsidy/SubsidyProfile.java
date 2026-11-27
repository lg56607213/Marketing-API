package com.marketingagent.domain.subsidy;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 지원사업을 찾을 사업체. 대표 생년·성별은 매칭에만 쓰고 소유 업체만 본다. */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "subsidy_profiles")
public class SubsidyProfile extends BaseEntity {

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String name;

    /** 법인 | 개인 | 예비창업자 */
    @Column(nullable = false, length = 20)
    private String entityType;

    private LocalDate foundedDate;

    @Column(length = 20)
    private String regionSido;

    @Column(length = 30)
    private String regionSigungu;

    @Column(length = 10)
    private String ksicCode;

    @Column(length = 100)
    private String industryName;

    private Long revenueKrw;

    private Integer employees;

    /** JSON 배열 문자열: 벤처 · 이노비즈 · 메인비즈 · 여성기업 등 */
    @Column(length = 500)
    private String certifications;

    private Integer ceoBirthYear;

    @Column(length = 10)
    private String ceoGender;

    /** 관심 분야·사업 설명 */
    @Column(length = 1000)
    private String keywords;

    @Column(nullable = false)
    private boolean emailOptIn = true;

    /** 쉼표로 구분한 받는 사람 */
    @Column(length = 1000)
    private String recipients;

    public SubsidyProfile(Long companyId) {
        this.companyId = companyId;
    }

    public void update(String name, String entityType, LocalDate foundedDate, String regionSido, String regionSigungu,
            String ksicCode, String industryName, Long revenueKrw, Integer employees, String certifications,
            Integer ceoBirthYear, String ceoGender, String keywords, boolean emailOptIn, String recipients) {
        this.name = name;
        this.entityType = entityType;
        this.foundedDate = foundedDate;
        this.regionSido = regionSido;
        this.regionSigungu = regionSigungu;
        this.ksicCode = ksicCode;
        this.industryName = industryName;
        this.revenueKrw = revenueKrw;
        this.employees = employees;
        this.certifications = certifications;
        this.ceoBirthYear = ceoBirthYear;
        this.ceoGender = ceoGender;
        this.keywords = keywords;
        this.emailOptIn = emailOptIn;
        this.recipients = recipients;
    }

    public void unsubscribe() {
        this.emailOptIn = false;
    }
}
