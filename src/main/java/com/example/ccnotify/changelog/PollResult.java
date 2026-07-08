package com.example.ccnotify.changelog;

/**
 * 폴링 1회 실행 결과 요약 (수동 트리거 응답/로그용).
 */
public record PollResult(String status, int detected, int processed, String message) {

    public static PollResult skippedBusy() {
        return new PollResult("SKIPPED_BUSY", 0, 0, "이전 폴링이 진행 중");
    }

    public static PollResult baselineSaved(int count) {
        return new PollResult("BASELINE_SAVED", count, 0, "최초 실행 baseline 저장(발송 안 함)");
    }

    public static PollResult noNew() {
        return new PollResult("NO_NEW", 0, 0, "신규 릴리즈 없음");
    }

    public static PollResult processed(int detected, int processed) {
        return new PollResult("PROCESSED", detected, processed, null);
    }

    public static PollResult error(String message) {
        return new PollResult("ERROR", 0, 0, message);
    }
}
