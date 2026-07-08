package com.example.ccnotify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 애플리케이션 설정. application.yml 의 {@code app.*} 값이 바인딩된다.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String changelogUrl,
        String changelogWebUrl,
        Duration pollInterval,
        Duration initialDelay,
        boolean dryRun,
        boolean baselineOnFirstRun,
        Duration connectTokenTtl,
        String publicBaseUrl,
        Telegram telegram,
        Anthropic anthropic
) {

    public record Telegram(
            String botToken,
            String botUsername,
            String adminChatId,
            String apiBaseUrl,
            long sendThrottleMs
    ) {
        public boolean configured() {
            return botToken != null && !botToken.isBlank();
        }

        public boolean adminConfigured() {
            return adminChatId != null && !adminChatId.isBlank();
        }
    }

    public record Anthropic(
            String apiKey,
            String baseUrl,
            String model,
            String version,
            int maxTokens
    ) {
        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
