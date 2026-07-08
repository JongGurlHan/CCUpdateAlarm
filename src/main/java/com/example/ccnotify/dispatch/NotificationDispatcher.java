package com.example.ccnotify.dispatch;

import com.example.ccnotify.config.AppProperties;
import com.example.ccnotify.subscriber.Subscriber;
import com.example.ccnotify.subscriber.SubscriptionService;
import com.example.ccnotify.telegram.SendOutcome;
import com.example.ccnotify.telegram.TelegramClient;
import com.example.ccnotify.util.TelegramText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 번역된 릴리즈 메시지를 ACTIVE Telegram 구독자에게 fan-out 발송한다.
 * - 4096자 초과 시 분할, 발송 간 스로틀
 * - 403/400(차단) → 구독자 자동 비활성화
 * - 429(레이트리밋) → retry_after 후 재시도
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int MAX_RATE_LIMIT_RETRIES = 3;

    private final TelegramClient telegram;
    private final SubscriptionService subscriptions;
    private final AppProperties props;

    public NotificationDispatcher(TelegramClient telegram,
                                  SubscriptionService subscriptions,
                                  AppProperties props) {
        this.telegram = telegram;
        this.subscriptions = subscriptions;
        this.props = props;
    }

    public void dispatchRelease(String version, String translatedBody) {
        String message = buildMessage(version, translatedBody);
        List<String> chunks = TelegramText.split(message, TelegramText.MAX_LENGTH);
        List<Subscriber> subscribers = subscriptions.activeTelegramSubscribers();

        if (props.dryRun()) {
            log.info("[DRY-RUN] 버전 {} → 구독자 {}명에게 발송 예정. 메시지:\n{}",
                    version, subscribers.size(), message);
            return;
        }

        int sent = 0;
        for (Subscriber s : subscribers) {
            if (deliver(s, chunks)) {
                sent++;
            }
            throttle();
        }
        log.info("버전 {} 발송 완료: 대상 {}명 중 {}명 성공", version, subscribers.size(), sent);
    }

    /** 한 구독자에게 모든 청크 발송. 완전 성공 시 true. */
    private boolean deliver(Subscriber s, List<String> chunks) {
        for (String chunk : chunks) {
            SendOutcome outcome = sendWithRateLimitRetry(s.getChatId(), chunk);
            switch (outcome.type()) {
                case OK -> {
                    // 다음 청크
                }
                case BLOCKED -> {
                    log.info("구독자 chatId={} 차단/채팅없음 → 비활성화", s.getChatId());
                    subscriptions.deactivate(s.getId());
                    return false;
                }
                case RATE_LIMITED, TRANSIENT -> {
                    log.warn("구독자 chatId={} 발송 실패({}): {}", s.getChatId(), outcome.type(), outcome.detail());
                    return false;
                }
            }
        }
        return true;
    }

    private SendOutcome sendWithRateLimitRetry(Long chatId, String chunk) {
        SendOutcome outcome = telegram.sendMessage(chatId, chunk, true);
        int retries = 0;
        while (outcome.type() == SendOutcome.Type.RATE_LIMITED && retries < MAX_RATE_LIMIT_RETRIES) {
            sleep(Math.max(1, outcome.retryAfterSeconds()) * 1000L);
            outcome = telegram.sendMessage(chatId, chunk, true);
            retries++;
        }
        return outcome;
    }

    private String buildMessage(String version, String translatedBody) {
        String safeVersion = TelegramText.escapeHtml(version);
        String safeBody = TelegramText.escapeHtml(translatedBody);
        String link = props.changelogWebUrl();
        return "<b>🚀 Claude Code " + safeVersion + "</b>\n\n"
                + safeBody + "\n\n"
                + "🔗 <a href=\"" + link + "\">전체 CHANGELOG 보기</a>";
    }

    private void throttle() {
        sleep(props.telegram().sendThrottleMs());
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
