package com.marketingagent.domain.ads.autopilot;

import com.marketingagent.common.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자동 운영이 한 번 돌 때마다 남기는 기록. 무엇을 왜 바꿨는지 나중에 확인할 수 있어야 맡길 수 있다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "autopilot_runs")
public class AutopilotRun extends OwnedEntity {

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    /** true 면 무엇을 할지만 계산하고 아무것도 바꾸지 않은 미리보기 */
    @Column(nullable = false)
    private boolean preview;

    /** true 면 서버가 dry-run 이라 네이버에는 반영하지 않고 기록만 남겼다 */
    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(nullable = false, length = 500)
    private String summary;

    /** 실행 내용 전체(AutopilotDtos.RunResult)를 JSON 으로 보관한다. */
    @Lob
    @Column(name = "content_json", nullable = false, columnDefinition = "TEXT")
    private String contentJson;

    public AutopilotRun(LocalDate runDate, boolean preview, boolean dryRun, String summary, String contentJson) {
        this.runDate = runDate;
        this.preview = preview;
        this.dryRun = dryRun;
        this.summary = summary;
        this.contentJson = contentJson;
    }
}
