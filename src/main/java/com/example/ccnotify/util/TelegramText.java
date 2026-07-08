package com.example.ccnotify.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Telegram 메시지 텍스트 유틸.
 */
public final class TelegramText {

    /** Telegram 단일 메시지 최대 길이. */
    public static final int MAX_LENGTH = 4096;

    private TelegramText() {
    }

    /** parse_mode=HTML 사용 시 텍스트에 들어갈 동적 값의 특수문자를 이스케이프한다. */
    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * 최대 길이를 넘는 메시지를 줄 경계 기준으로 분할한다.
     * 헤더/링크 등 태그가 있는 줄은 짧으므로 줄 단위 분할로 태그가 깨지지 않는다.
     */
    public static List<String> split(String text, int maxLength) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            chunks.add("");
            return chunks;
        }
        if (text.length() <= maxLength) {
            chunks.add(text);
            return chunks;
        }

        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.length() > maxLength) {
                // 아주 긴 단일 줄은 하드 분할
                flush(chunks, current);
                for (int i = 0; i < line.length(); i += maxLength) {
                    chunks.add(line.substring(i, Math.min(line.length(), i + maxLength)));
                }
                continue;
            }
            int extra = current.isEmpty() ? line.length() : line.length() + 1;
            if (current.length() + extra > maxLength) {
                flush(chunks, current);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        flush(chunks, current);
        return chunks;
    }

    private static void flush(List<String> chunks, StringBuilder current) {
        if (!current.isEmpty()) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }
}
