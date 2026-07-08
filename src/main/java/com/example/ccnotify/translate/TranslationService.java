package com.example.ccnotify.translate;

import org.springframework.stereotype.Service;

/**
 * 릴리즈 노트 원문을 한국어로 번역한다. 버전 1건의 모든 불릿을 1회 API 호출로 처리한다.
 */
@Service
public class TranslationService {

    private final ClaudeClient claude;

    public TranslationService(ClaudeClient claude) {
        this.claude = claude;
    }

    public String translate(String version, String originalBody) {
        return claude.complete(buildPrompt(version, originalBody));
    }

    private String buildPrompt(String version, String originalBody) {
        return """
                다음은 개발 도구 'Claude Code'의 릴리즈 노트(버전 %s)입니다.
                이를 자연스러운 한국어로 번역하세요.

                규칙:
                - 고유명사, CLI 플래그, 코드/식별자, 파일명, 훅(hook) 이름 등은 영어 원문을 유지합니다 (예: Claude Code, --flag, SessionStart).
                - 각 항목의 불릿(-) 구조를 그대로 보존합니다.
                - 부연 설명, 머리말, 코드펜스 없이 번역된 불릿만 출력합니다.

                릴리즈 노트:
                %s
                """.formatted(version, originalBody);
    }
}
