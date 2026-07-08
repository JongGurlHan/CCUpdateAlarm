package com.example.ccnotify.admin;

import com.example.ccnotify.config.AppProperties;
import com.example.ccnotify.telegram.TelegramClient;
import com.example.ccnotify.util.TelegramText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 운영 장애를 관리자 Telegram chat 으로 알린다. (dry-run 과 무관하게 항상 시도)
 */
@Component
public class AdminNotifier {

    private static final Logger log = LoggerFactory.getLogger(AdminNotifier.class);

    private final TelegramClient telegram;
    private final AppProperties props;

    public AdminNotifier(TelegramClient telegram, AppProperties props) {
        this.telegram = telegram;
        this.props = props;
    }

    public void notifyError(String context, Throwable t) {
        String detail = t == null ? "" : (t.getClass().getSimpleName() + ": " + t.getMessage());
        notify("⚠️ [CC-Notify] " + context + "\n" + detail);
    }

    public void notify(String message) {
        log.warn("ADMIN ALERT: {}", message);
        if (!props.telegram().adminConfigured() || !props.telegram().configured()) {
            return;
        }
        long adminChatId;
        try {
            adminChatId = Long.parseLong(props.telegram().adminChatId().trim());
        } catch (NumberFormatException e) {
            log.error("admin-chat-id 형식 오류: {}", props.telegram().adminChatId());
            return;
        }
        String text = TelegramText.escapeHtml(message);
        for (String chunk : TelegramText.split(text, TelegramText.MAX_LENGTH)) {
            var outcome = telegram.sendMessage(adminChatId, chunk, true);
            if (!outcome.isOk()) {
                log.error("관리자 알림 발송 실패: {} {}", outcome.type(), outcome.detail());
                break;
            }
        }
    }
}
