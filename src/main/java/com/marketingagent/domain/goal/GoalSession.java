package com.marketingagent.domain.goal;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 광고 계정 하나의 목표 상담. 대화로 사업 구조를 채우고(INTERVIEW), 계산과 실현 가능성 검토를 거쳐(ANALYZED),
 * 사용자가 안을 고르면 운영 목표로 확정된다(CONFIRMED). 새로 상담하면 새 세션을 만들고 이전 세션은 기록으로 남는다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "goal_sessions", indexes = @Index(name = "ix_goal_sessions_account", columnList = "ad_account_id, id"))
public class GoalSession extends BaseEntity {

    public static final String INTERVIEW = "INTERVIEW";
    public static final String ANALYZED = "ANALYZED";
    public static final String CONFIRMED = "CONFIRMED";

    @Column(name = "ad_account_id", nullable = false)
    private Long adAccountId;

    @Column(nullable = false, length = 20)
    private String status;

    /** BusinessModel 을 JSON 으로 */
    @Lob
    @Column(name = "model_json", nullable = false, columnDefinition = "TEXT")
    private String modelJson;

    /** 마지막 검토 결과(GoalDtos.Analysis)를 JSON 으로. 검토 전이면 null. 키워드가 많으면 64KB 를 넘을 수 있어 V11 과 같게 MEDIUMTEXT */
    @Lob
    @Column(name = "analysis_json", columnDefinition = "MEDIUMTEXT")
    private String analysisJson;

    @Column(name = "confirmed_option", length = 20)
    private String confirmedOption;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    public GoalSession(Long adAccountId, String modelJson) {
        this.adAccountId = adAccountId;
        this.modelJson = modelJson;
        this.status = INTERVIEW;
    }

    /** 사업 구조가 바뀌면 이전 검토는 더 이상 맞지 않으므로 지운다. 확정된 세션은 확정 상태를 유지한다. */
    public void updateModel(String modelJson) {
        if (!modelJson.equals(this.modelJson)) {
            this.modelJson = modelJson;
            this.analysisJson = null;
            if (!CONFIRMED.equals(status)) {
                this.status = INTERVIEW;
            }
        }
    }

    public void analyzed(String analysisJson) {
        this.analysisJson = analysisJson;
        if (!CONFIRMED.equals(status)) {
            this.status = ANALYZED;
        }
    }

    public void confirm(String option) {
        this.status = CONFIRMED;
        this.confirmedOption = option;
        this.confirmedAt = LocalDateTime.now();
    }
}
