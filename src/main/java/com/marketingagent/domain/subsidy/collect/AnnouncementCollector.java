package com.marketingagent.domain.subsidy.collect;

import com.marketingagent.domain.subsidy.SubsidyAnnouncement.Normalized;
import java.util.List;

/** 공식 API 로 접수 중인 공고를 받아 공통 모양으로 바꾼다. 화면 크롤링은 하지 않는다. */
public interface AnnouncementCollector {

    String source();

    /** 키가 있어 수집할 수 있는지. */
    boolean configured();

    List<Normalized> fetchOpen();
}
