package com.example.ccnotify.telegram;

import com.example.ccnotify.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 백그라운드 롱폴링 루프. getUpdates 로 업데이트를 받아 명령어 핸들러로 넘긴다.
 * 웹훅을 쓰지 않으므로 로컬에서 공개 URL 없이 동작한다.
 */
@Component
public class TelegramLongPollingRunner {

    private static final Logger log = LoggerFactory.getLogger(TelegramLongPollingRunner.class);
    private static final int LONG_POLL_TIMEOUT_SECONDS = 30;

    private final TelegramClient telegram;
    private final TelegramCommandHandler commandHandler;
    private final AppProperties props;

    private volatile boolean running = false;
    private Thread thread;
    private Long offset = null;

    public TelegramLongPollingRunner(TelegramClient telegram,
                                     TelegramCommandHandler commandHandler,
                                     AppProperties props) {
        this.telegram = telegram;
        this.commandHandler = commandHandler;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!props.telegram().configured()) {
            log.warn("Telegram bot-token 미설정 → 롱폴링 비활성화 (구독/명령 처리 불가)");
            return;
        }
        running = true;
        thread = new Thread(this::loop, "telegram-longpoll");
        thread.setDaemon(true);
        thread.start();
        log.info("Telegram 롱폴링 시작");
    }

    private void loop() {
        while (running) {
            try {
                List<TelegramClient.Update> updates = telegram.getUpdates(offset, LONG_POLL_TIMEOUT_SECONDS);
                for (TelegramClient.Update update : updates) {
                    try {
                        commandHandler.handle(update);
                    } catch (Exception e) {
                        log.error("업데이트 처리 실패 updateId={}", update.update_id(), e);
                    }
                    offset = update.update_id() + 1;
                }
            } catch (Exception e) {
                if (running) {
                    log.warn("getUpdates 실패, 3초 후 재시도: {}", e.getMessage());
                    sleep(3000);
                }
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
