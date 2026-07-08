package com.example.ccnotify.release;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 이미 처리한 릴리즈 버전. row 가 존재하면 "처리됨"으로 간주하여 다시 알림하지 않는다.
 * baseline=true 인 row 는 최초 실행 시 발송 없이 기준점으로만 기록된 것.
 */
@Entity
@Table(name = "processed_release")
public class ProcessedRelease {

    @Id
    @Column(nullable = false, length = 64)
    private String version;

    @Column(name = "original_text", length = 20000)
    private String originalText;

    @Column(name = "translated_text", length = 20000)
    private String translatedText;

    @Column(nullable = false)
    private boolean baseline;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProcessedRelease() {
    }

    private ProcessedRelease(String version, String originalText, String translatedText, boolean baseline) {
        this.version = version;
        this.originalText = originalText;
        this.translatedText = translatedText;
        this.baseline = baseline;
        this.createdAt = Instant.now();
    }

    public static ProcessedRelease baseline(String version) {
        return new ProcessedRelease(version, null, null, true);
    }

    public static ProcessedRelease notified(String version, String originalText, String translatedText) {
        return new ProcessedRelease(version, originalText, translatedText, false);
    }

    public String getVersion() {
        return version;
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getTranslatedText() {
        return translatedText;
    }

    public boolean isBaseline() {
        return baseline;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
