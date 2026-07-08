package com.example.ccnotify.translate;

import com.example.ccnotify.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Anthropic Messages API 호출 래퍼.
 */
@Component
public class ClaudeClient {

    private final RestClient anthropicRestClient;
    private final AppProperties.Anthropic cfg;

    public ClaudeClient(RestClient anthropicRestClient, AppProperties props) {
        this.anthropicRestClient = anthropicRestClient;
        this.cfg = props.anthropic();
    }

    /**
     * 단일 user 메시지를 보내고 텍스트 응답을 받는다.
     * 실패(HTTP 오류/빈 응답)는 예외로 전파되어 호출측이 재시도/알림을 처리한다.
     */
    public String complete(String userPrompt) {
        var request = new MessagesRequest(
                cfg.model(),
                cfg.maxTokens(),
                List.of(new Message("user", userPrompt))
        );

        MessagesResponse resp = anthropicRestClient.post()
                .uri("/v1/messages")
                .body(request)
                .retrieve()
                .body(MessagesResponse.class);

        if (resp == null || resp.content() == null || resp.content().isEmpty()) {
            throw new IllegalStateException("Anthropic 응답이 비어 있습니다");
        }

        String text = resp.content().stream()
                .filter(b -> "text".equals(b.type()) && b.text() != null)
                .map(ContentBlock::text)
                .collect(Collectors.joining());

        if (text.isBlank()) {
            throw new IllegalStateException("Anthropic 응답에 텍스트가 없습니다");
        }
        return text.strip();
    }

    // --- Anthropic Messages API DTO ---

    record MessagesRequest(String model, int max_tokens, List<Message> messages) {
    }

    record Message(String role, String content) {
    }

    record MessagesResponse(List<ContentBlock> content) {
    }

    record ContentBlock(String type, String text) {
    }
}
