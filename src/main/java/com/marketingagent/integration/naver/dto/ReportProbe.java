package com.marketingagent.integration.naver.dto;

/**
 * 리포트를 실제로 내려받아 본 결과. 연결 점검에서 문서와 형식이 맞는지 확인하는 데 쓴다.
 *
 * @param rows    데이터 줄 수
 * @param columns 첫 데이터 줄의 컬럼 수. 줄이 없으면 0
 */
public record ReportProbe(int rows, int columns) {}
