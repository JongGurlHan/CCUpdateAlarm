package com.example.ccnotify.changelog;

import com.example.ccnotify.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * raw CHANGELOG.md 를 조회한다.
 */
@Component
public class ChangelogFetcher {

    private final RestClient changelogRestClient;
    private final AppProperties props;

    public ChangelogFetcher(RestClient changelogRestClient, AppProperties props) {
        this.changelogRestClient = changelogRestClient;
        this.props = props;
    }

    public String fetch() {
        return changelogRestClient.get()
                .uri(props.changelogUrl())
                .retrieve()
                .body(String.class);
    }
}
