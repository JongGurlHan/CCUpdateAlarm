package com.example.ccnotify.config;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 외부 호출용 {@link RestClient} 빈 구성.
 * - changelogRestClient : raw CHANGELOG.md 조회
 * - anthropicRestClient : Anthropic Messages API (x-api-key / anthropic-version 헤더 포함)
 * - telegramRestClient  : Telegram Bot API (getUpdates 롱폴링을 위해 read timeout 을 길게)
 */
@Configuration
public class HttpClientConfig {

    @Bean
    RestClient changelogRestClient() {
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    @Bean
    RestClient anthropicRestClient(AppProperties props) {
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(Duration.ofSeconds(120));
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .baseUrl(props.anthropic().baseUrl())
                .defaultHeader("x-api-key", props.anthropic().apiKey())
                .defaultHeader("anthropic-version", props.anthropic().version())
                .defaultHeader("content-type", "application/json")
                .build();
    }

    @Bean
    RestClient telegramRestClient(AppProperties props) {
        // 롱폴링 timeout(30s) 보다 read timeout 이 커야 한다.
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(Duration.ofSeconds(45));
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .baseUrl(props.telegram().apiBaseUrl())
                .build();
    }
}
