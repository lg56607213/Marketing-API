package com.marketingagent.domain.content;

import com.marketingagent.common.ContentStatus;
import com.marketingagent.common.ContentType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRepository extends JpaRepository<Content, Long> {
    List<Content> findByAuthorId(Long authorId);
    List<Content> findByBrandId(Long brandId);
    List<Content> findByBrandIdAndStatus(Long brandId, ContentStatus status);
    List<Content> findByBrandIdAndContentType(Long brandId, ContentType contentType);
}
