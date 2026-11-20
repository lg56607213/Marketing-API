package com.marketingagent.domain.goal;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 목표 상담의 말 한 마디. */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "goal_messages", indexes = @Index(name = "ix_goal_messages_session", columnList = "session_id, id"))
public class GoalMessage extends BaseEntity {

    public static final String USER = "USER";
    public static final String ASSISTANT = "ASSISTANT";

    @Column(name = "ad_account_id", nullable = false)
    private Long adAccountId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(nullable = false, length = 20)
    private String role;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public GoalMessage(Long adAccountId, Long sessionId, String role, String content) {
        this.adAccountId = adAccountId;
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
    }
}
