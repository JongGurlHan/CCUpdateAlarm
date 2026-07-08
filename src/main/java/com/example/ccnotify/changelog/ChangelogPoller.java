package com.example.ccnotify.changelog;

import com.example.ccnotify.admin.AdminNotifier;
import com.example.ccnotify.config.AppProperties;
import com.example.ccnotify.dispatch.NotificationDispatcher;
import com.example.ccnotify.release.ProcessedRelease;
import com.example.ccnotify.release.ProcessedReleaseRepository;
import com.example.ccnotify.translate.TranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 주기 폴링 오케스트레이터: fetch → parse → 신규 감지 → 번역 → 저장 → 발송.
 */
@Component
public class ChangelogPoller {

    private static final Logger log = LoggerFactory.getLogger(ChangelogPoller.class);

    private final ChangelogFetcher fetcher;
    private final ChangelogParser parser;
    private final ProcessedReleaseRepository repository;
    private final TranslationService translationService;
    private final NotificationDispatcher dispatcher;
    private final AdminNotifier adminNotifier;
    private final AppProperties props;

    private final ReentrantLock lock = new ReentrantLock();

    public ChangelogPoller(ChangelogFetcher fetcher,
                           ChangelogParser parser,
                           ProcessedReleaseRepository repository,
                           TranslationService translationService,
                           NotificationDispatcher dispatcher,
                           AdminNotifier adminNotifier,
                           AppProperties props) {
        this.fetcher = fetcher;
        this.parser = parser;
        this.repository = repository;
        this.translationService = translationService;
        this.dispatcher = dispatcher;
        this.adminNotifier = adminNotifier;
        this.props = props;
    }

    /** 스케줄러가 호출하는 진입점 (스케줄 등록은 SchedulingConfig 참고). */
    public void scheduledPoll() {
        pollOnce();
    }

    /** 1회 폴링 실행. 동시 실행은 tryLock 으로 방지한다(스케줄러/수동 트리거 겹침 방지). */
    public PollResult pollOnce() {
        if (!lock.tryLock()) {
            log.info("이전 폴링이 진행 중 → 이번 실행 건너뜀");
            return PollResult.skippedBusy();
        }
        try {
            return doPoll();
        } catch (Exception e) {
            log.error("폴링 중 오류", e);
            adminNotifier.notifyError("CHANGELOG 폴링 실패", e);
            return PollResult.error(e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private PollResult doPoll() {
        String markdown = fetcher.fetch();
        List<ReleaseNote> notes = parser.parse(markdown); // 파일 순서: 최신 → 오래된

        // 오래된 → 최신 순으로 처리(발송 순서 보장)
        List<ReleaseNote> ordered = new ArrayList<>(notes);
        Collections.reverse(ordered);

        Set<String> processedVersions = new HashSet<>(repository.findAllVersions());

        // 최초 실행: baseline 만 저장하고 발송하지 않음
        if (processedVersions.isEmpty() && props.baselineOnFirstRun()) {
            for (ReleaseNote note : ordered) {
                repository.save(ProcessedRelease.baseline(note.version()));
            }
            log.info("최초 실행 baseline 저장: {}건 (발송 안 함)", ordered.size());
            return PollResult.baselineSaved(ordered.size());
        }

        List<ReleaseNote> newReleases = ordered.stream()
                .filter(note -> !processedVersions.contains(note.version()))
                .toList();

        if (newReleases.isEmpty()) {
            log.debug("신규 릴리즈 없음");
            return PollResult.noNew();
        }
        log.info("신규 릴리즈 {}건 감지: {}", newReleases.size(),
                newReleases.stream().map(ReleaseNote::version).toList());

        int processedCount = 0;
        for (ReleaseNote note : newReleases) {
            try {
                String translated = translationService.translate(note.version(), note.body());
                // 저장(캐시) 후 발송. 실패 시 저장하지 않아 다음 폴링에 재시도된다.
                repository.save(ProcessedRelease.notified(note.version(), note.body(), translated));
                dispatcher.dispatchRelease(note.version(), translated);
                processedCount++;
            } catch (Exception e) {
                log.error("버전 {} 처리 실패 → 저장하지 않고 다음 폴링에 재시도", note.version(), e);
                adminNotifier.notifyError("버전 " + note.version() + " 번역/발송 실패", e);
            }
        }
        return PollResult.processed(newReleases.size(), processedCount);
    }
}
