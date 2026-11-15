package com.marketingagent.domain.prompt;

import com.marketingagent.common.ContentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptRepository extends JpaRepository<PromptTemplate, Long> {
    List<PromptTemplate> findByBrandId(Long brandId);
    List<PromptTemplate> findByBrand_CompanyId(Long companyId);
    Optional<PromptTemplate> findFirstByBrandIdAndContentType(Long brandId, ContentType contentType);
}
