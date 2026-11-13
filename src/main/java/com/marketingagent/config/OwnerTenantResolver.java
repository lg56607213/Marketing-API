package com.marketingagent.config;

import com.marketingagent.common.security.OwnerContext;
import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * 광고 데이터(OwnedEntity)를 회원별로 나누는 기준을 Hibernate 에 알려준다.
 * 조회에는 자동으로 owner_id 조건이 붙고, 저장할 때는 owner_id 가 자동으로 채워진다.
 */
@Component
public class OwnerTenantResolver implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long ownerId = OwnerContext.currentOwnerId();
        return ownerId != null ? ownerId : OwnerContext.NO_OWNER;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
