package com.example.ccnotify.subscriber;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "subscriber", indexes = {
        @Index(name = "idx_sub_channel_chat", columnList = "channel, chat_id"),
        @Index(name = "idx_sub_connect_token", columnList = "connect_token"),
        @Index(name = "idx_sub_unsub_token", columnList = "unsub_token")
})
public class Subscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Channel channel;

    /** Telegram chat id. 연결 확정(/start) 전에는 null. */
    @Column(name = "chat_id")
    private Long chatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriberStatus status;

    @Column(name = "connect_token", length = 64)
    private String connectToken;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "unsub_token", length = 64)
    private String unsubToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscriber() {
    }

    public static Subscriber pendingTelegram(String connectToken, Instant tokenExpiresAt) {
        Subscriber s = new Subscriber();
        s.channel = Channel.TELEGRAM;
        s.status = SubscriberStatus.PENDING;
        s.connectToken = connectToken;
        s.tokenExpiresAt = tokenExpiresAt;
        s.createdAt = Instant.now();
        s.updatedAt = s.createdAt;
        return s;
    }

    /** 연결 확정: chat_id 를 채우고 ACTIVE 로 전환, unsub 토큰 발급, connect 토큰 제거. */
    public void activate(Long chatId, String unsubToken) {
        this.chatId = chatId;
        this.status = SubscriberStatus.ACTIVE;
        this.unsubToken = unsubToken;
        this.connectToken = null;
        this.tokenExpiresAt = null;
        this.updatedAt = Instant.now();
    }

    public void unsubscribe() {
        this.status = SubscriberStatus.UNSUBSCRIBED;
        this.updatedAt = Instant.now();
    }

    public boolean isTokenExpired(Instant now) {
        return tokenExpiresAt != null && tokenExpiresAt.isBefore(now);
    }

    public Long getId() {
        return id;
    }

    public Channel getChannel() {
        return channel;
    }

    public Long getChatId() {
        return chatId;
    }

    public SubscriberStatus getStatus() {
        return status;
    }

    public String getConnectToken() {
        return connectToken;
    }

    public Instant getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public String getUnsubToken() {
        return unsubToken;
    }
}
