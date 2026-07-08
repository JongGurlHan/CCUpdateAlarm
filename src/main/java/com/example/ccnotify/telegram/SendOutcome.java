package com.example.ccnotify.telegram;

/**
 * Telegram sendMessage 결과.
 */
public record SendOutcome(Type type, int retryAfterSeconds, String detail) {

    public enum Type {
        /** 정상 발송 */
        OK,
        /** 봇 차단/채팅 없음 등 영구 실패 → 구독자 비활성화 대상 (403/400) */
        BLOCKED,
        /** 레이트리밋 (429) → retryAfterSeconds 후 재시도 */
        RATE_LIMITED,
        /** 일시적 오류 → 다음 기회에 재시도 */
        TRANSIENT
    }

    public static SendOutcome ok() {
        return new SendOutcome(Type.OK, 0, null);
    }

    public static SendOutcome blocked(String detail) {
        return new SendOutcome(Type.BLOCKED, 0, detail);
    }

    public static SendOutcome rateLimited(int retryAfterSeconds) {
        return new SendOutcome(Type.RATE_LIMITED, retryAfterSeconds, null);
    }

    public static SendOutcome transientError(String detail) {
        return new SendOutcome(Type.TRANSIENT, 0, detail);
    }

    public boolean isOk() {
        return type == Type.OK;
    }
}
