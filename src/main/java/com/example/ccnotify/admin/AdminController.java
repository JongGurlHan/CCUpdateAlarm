package com.example.ccnotify.admin;

import com.example.ccnotify.changelog.ChangelogPoller;
import com.example.ccnotify.changelog.PollResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영/테스트용 엔드포인트. (로컬 개발용 - 인증 없음)
 */
@RestController
public class AdminController {

    private final ChangelogPoller poller;

    public AdminController(ChangelogPoller poller) {
        this.poller = poller;
    }

    /** 스케줄을 기다리지 않고 즉시 1회 폴링을 실행한다. */
    @PostMapping("/api/admin/poll")
    public PollResult triggerPoll() {
        return poller.pollOnce();
    }
}
