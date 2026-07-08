package com.example.ccnotify.telegram;

import com.example.ccnotify.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Telegram Bot API 래퍼 (sendMessage, getUpdates).
 */
@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);
    private static final Pattern RETRY_AFTER = Pattern.compile("\"retry_after\"\\s*:\\s*(\\d+)");

    private final RestClient telegramRestClient;
    private final String botToken;

    public TelegramClient(RestClient telegramRestClient, AppProperties props) {
        this.telegramRestClient = telegramRestClient;
        this.botToken = props.telegram().botToken();
    }

    /** HTML parse mode 로 단일 메시지(≤4096자) 발송. 결과를 분류해 반환한다. */
    public SendOutcome sendMessage(long chatId, String htmlText, boolean disablePreview) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", htmlText);
        body.put("parse_mode", "HTML");
        body.put("disable_web_page_preview", disablePreview);

        try {
            telegramRestClient.post()
                    .uri("/bot" + botToken + "/sendMessage")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return SendOutcome.ok();
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            if (code == 429) {
                return SendOutcome.rateLimited(parseRetryAfter(responseBody));
            }
            if (code == 403 || code == 400) {
                return SendOutcome.blocked(responseBody);
            }
            return SendOutcome.transientError("HTTP " + code + " " + responseBody);
        } catch (Exception e) {
            return SendOutcome.transientError(e.getMessage());
        }
    }

    /** 롱폴링. offset 이후의 업데이트를 timeout 초 동안 대기하며 받는다. */
    public List<Update> getUpdates(Long offset, int timeoutSeconds) {
        UpdatesResponse resp = telegramRestClient.get()
                .uri(ub -> {
                    ub.path("/bot" + botToken + "/getUpdates")
                            .queryParam("timeout", timeoutSeconds);
                    if (offset != null) {
                        ub.queryParam("offset", offset);
                    }
                    return ub.build();
                })
                .retrieve()
                .body(UpdatesResponse.class);

        if (resp == null || !resp.ok() || resp.result() == null) {
            return List.of();
        }
        return resp.result();
    }

    private int parseRetryAfter(String responseBody) {
        if (responseBody != null) {
            Matcher m = RETRY_AFTER.matcher(responseBody);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return 1;
    }

    // --- Telegram getUpdates DTO (미지 필드는 무시) ---

    public record UpdatesResponse(boolean ok, List<Update> result) {
    }

    public record Update(long update_id, Message message) {
    }

    public record Message(long message_id, Chat chat, String text) {
    }

    public record Chat(long id, String type) {
    }
}
