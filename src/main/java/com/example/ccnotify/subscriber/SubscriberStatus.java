package com.example.ccnotify.subscriber;

public enum SubscriberStatus {
    /** 웹에서 신청했지만 아직 /start 로 연결 확정 전 */
    PENDING,
    /** 연결 확정되어 발송 대상 */
    ACTIVE,
    /** 해지됨 */
    UNSUBSCRIBED
}
