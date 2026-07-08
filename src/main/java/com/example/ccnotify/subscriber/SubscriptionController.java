package com.example.ccnotify.subscriber;

import com.example.ccnotify.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final AppProperties props;

    public SubscriptionController(SubscriptionService subscriptionService, AppProperties props) {
        this.subscriptionService = subscriptionService;
        this.props = props;
    }

    /** 웹 "Telegram으로 받기" → 딥링크 반환. 프론트가 이 링크로 이동시킨다. */
    @PostMapping("/api/subscribe/telegram")
    public ResponseEntity<?> subscribeTelegram() {
        if (!props.telegram().configured() || isBlank(props.telegram().botUsername())) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", "Telegram 봇이 설정되지 않았습니다 (bot-token/bot-username 확인)"));
        }
        String deepLink = subscriptionService.createTelegramConnectLink();
        return ResponseEntity.ok(Map.of("deepLink", deepLink));
    }

    /** 웹 해지 링크(웰컴 메시지에 포함되어 전달됨). */
    @GetMapping(value = "/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribe(@RequestParam(required = false) String token) {
        boolean ok = token != null && !token.isBlank() && subscriptionService.unsubscribeByToken(token);
        String message = ok
                ? "구독이 해지되었습니다. 더 이상 알림을 보내지 않습니다."
                : "유효하지 않거나 이미 해지된 링크입니다.";
        return ResponseEntity.ok(htmlPage(message));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String htmlPage(String message) {
        return """
                <!doctype html>
                <html lang="ko"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>구독 해지</title></head>
                <body style="font-family:system-ui,sans-serif;max-width:480px;margin:80px auto;text-align:center;padding:0 16px">
                <h2>Claude Code 릴리즈 알림</h2>
                <p>%s</p>
                </body></html>
                """.formatted(message);
    }
}
