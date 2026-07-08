package com.example.ccnotify.changelog;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CHANGELOG.md 마크다운을 {@link ReleaseNote} 목록으로 파싱한다.
 *
 * <p>구조: {@code # Changelog} 아래 {@code ## X.Y.ZZZ} 버전 헤딩(파일 내 최신이 위),
 * 각 버전 아래 {@code - } 플랫 불릿(멀티라인 가능).</p>
 *
 * <p>반환 순서는 파일 순서(최신 → 오래된)를 그대로 유지한다.</p>
 */
@Component
public class ChangelogParser {

    // "## 2.1.204" 처럼 두 개의 #, 공백, 숫자로 시작하는 버전 토큰. 뒤따르는 내용은 무시.
    private static final Pattern VERSION_HEADING = Pattern.compile("^##\\s+(\\d[^\\s]*).*$");

    public List<ReleaseNote> parse(String markdown) {
        List<ReleaseNote> result = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return result;
        }

        String[] lines = markdown.split("\r?\n", -1);
        String currentVersion = null;
        StringBuilder body = new StringBuilder();

        for (String line : lines) {
            Matcher m = VERSION_HEADING.matcher(line);
            if (m.matches()) {
                flush(result, currentVersion, body);
                currentVersion = m.group(1);
                body.setLength(0);
            } else if (currentVersion != null) {
                body.append(line).append('\n');
            }
        }
        flush(result, currentVersion, body);
        return result;
    }

    private void flush(List<ReleaseNote> result, String version, StringBuilder body) {
        if (version != null) {
            result.add(new ReleaseNote(version, body.toString().strip()));
        }
    }
}
