package com.marketingagent.domain.approval;

import com.marketingagent.common.BaseEntity;
import com.marketingagent.domain.content.Content;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "approvals")
@Getter
@NoArgsConstructor
public class Approval extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalAction action;

    @Column(columnDefinition = "TEXT")
    private String comment;

    public Approval(Content content, Long actorId, ApprovalAction action, String comment) {
        this.content = content;
        this.actorId = actorId;
        this.action = action;
        this.comment = comment;
    }
}
