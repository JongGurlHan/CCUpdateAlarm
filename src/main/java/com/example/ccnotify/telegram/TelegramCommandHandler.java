package com.example.ccnotify.telegram;

import com.example.ccnotify.config.AppProperties;
import com.example.ccnotify.subscriber.SubscriptionService;
import com.example.ccnotify.subscriber.SubscriptionService.ConnectOutcome;
import com.example.ccnotify.util.TelegramText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 봇 명령어 처리: /start &lt;token&gt;, /stop, /status.
 */
@Component
public class TelegramCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(TelegramCommandHandler.class);

    private final SubscriptionService subscriptions;
    private final TelegramClient telegram;
    private final AppProperties props;

    public TelegramCommandHandler(SubscriptionService subscriptions,
                                  TelegramClient telegram,
                                  AppProperties props) {
        this.subscriptions = subscriptions;
        this.telegram = telegram;
        this.props = props;
    }

    public void handle(TelegramClient.Update update) {
        TelegramClient.Message message = update.message();
        if (message == null || message.chat() == null || message.text() == null) {
            return;
        }
        long chatId = message.chat().id();
        String text = message.text().trim();
        String[] parts = text.split("\\s+");
        String command = parts[0].toLowerCase();
        // "/start@botname" 형태 정규화
        int at = command.indexOf('@');
        if (at >= 0) {
            command = command.substring(0, at);
        }

        switch (command) {
            case "/start" -> handleStart(chatId, parts.length > 1 ? parts[1] : null);
            case "/stop" -> handleStop(chatId);
            case "/status" -> handleStatus(chatId);
            default -> reply(chatId, helpText());
        }
    }

    private void handleStart(long chatId, String token) {
        if (token == null || token.isBlank()) {
            reply(chatId, "구독하려면 웹사이트에서 <b>Telegram으로 받기</b> 버튼을 눌러 연결해 주세요.");
            return;
        }
        ConnectOutcome outcome = subscriptions.confirmConnect(token, chatId);
        switch (outcome.result()) {
            case SUCCESS -> reply(chatId, welcomeText(outcome.unsubToken()));
            case EXPIRED -> reply(chatId, "연결 링크가 만료되었습니다. 웹사이트에서 다시 신청해 주세요.");
            case INVALID -> reply(chatId, "유효하지 않은 연결 링크입니다. 웹사이트에서 다시 신청해 주세요.");
        }
    }

    private void handleStop(long chatId) {
        boolean hadActive = subscriptions.unsubscribeByChatId(chatId);
        reply(chatId, hadActive
                ? "구독이 해지되었습니다. 언제든 다시 신청할 수 있어요."
                : "현재 활성화된 구독이 없습니다.");
    }

    private void handleStatus(long chatId) {
        boolean active = subscriptions.isActive(chatId);
        reply(chatId, active
                ? "✅ 구독 중입니다. 새 Claude Code 릴리즈가 올라오면 알림을 보내드려요."
                : "구독 중이 아닙니다. 웹사이트에서 신청해 주세요.");
    }

    private String welcomeText(String unsubToken) {
        String unsubLink = props.publicBaseUrl() + "/unsubscribe?token=" + unsubToken;
        return "🎉 구독이 완료되었습니다!\n"
                + "이제 새 Claude Code 릴리즈 노트를 한국어로 번역해 보내드립니다.\n\n"
                + "• 해지: /stop 명령 또는 <a href=\"" + unsubLink + "\">이 링크</a>\n"
                + "• 상태 확인: /status";
    }

    private String helpText() {
        return "사용 가능한 명령어:\n"
                + "/start - 연결 (웹에서 받은 링크로)\n"
                + "/status - 구독 상태 확인\n"
                + "/stop - 구독 해지";
    }

    private void reply(long chatId, String htmlText) {
        for (String chunk : TelegramText.split(htmlText, TelegramText.MAX_LENGTH)) {
            SendOutcome outcome = telegram.sendMessage(chatId, chunk, true);
            if (!outcome.isOk()) {
                log.warn("명령 응답 실패 chatId={} {} {}", chatId, outcome.type(), outcome.detail());
                break;
            }
        }
    }
}
