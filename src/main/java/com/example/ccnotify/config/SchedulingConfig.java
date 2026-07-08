package com.example.ccnotify.config;

import com.example.ccnotify.changelog.ChangelogPoller;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 폴링 스케줄을 프로그램적으로 등록한다. AppProperties 의 Duration("15m" 등)을 그대로 사용하여
 * fixedDelayString 문자열 파싱 제약을 피한다.
 */
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    private final ChangelogPoller poller;
    private final AppProperties props;

    public SchedulingConfig(ChangelogPoller poller, AppProperties props) {
        this.poller = poller;
        this.props = props;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new IntervalTask(
                poller::scheduledPoll,
                props.pollInterval(),
                props.initialDelay()
        ));
    }
}
