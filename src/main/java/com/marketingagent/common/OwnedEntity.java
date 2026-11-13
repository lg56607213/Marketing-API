package com.marketingagent.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;

/**
 * 회원별로 분리되는 데이터의 공통 부모.
 * owner_id 는 저장할 때 현재 소유자로 자동 채워지고, 조회할 때는 현재 소유자의 것만 보인다.
 */
@MappedSuperclass
public abstract class OwnedEntity extends BaseEntity {

    @TenantId
    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    public Long getOwnerId() {
        return ownerId;
    }
}
