package com.example.ccnotify.changelog;

/**
 * CHANGELOG.md 에서 파싱한 릴리즈 1건.
 *
 * @param version 버전 문자열 (예: "2.1.204")
 * @param body    해당 버전 아래의 원문 본문 (불릿 등, trim 됨)
 */
public record ReleaseNote(String version, String body) {
}
