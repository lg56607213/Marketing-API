package com.marketingagent.domain.approval;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {
    List<Approval> findByContentIdOrderByCreatedAtDesc(Long contentId);
}
