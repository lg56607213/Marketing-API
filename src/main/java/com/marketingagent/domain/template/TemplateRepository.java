package com.marketingagent.domain.template;

import com.marketingagent.common.ContentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateRepository extends JpaRepository<Template, Long> {
    List<Template> findByBrandId(Long brandId);
    List<Template> findByBrandIdAndContentType(Long brandId, ContentType contentType);
    Optional<Template> findFirstByBrandIdAndContentType(Long brandId, ContentType contentType);
}
