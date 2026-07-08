package com.example.ccnotify.changelog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangelogParserTest {

    private final ChangelogParser parser = new ChangelogParser();

    @Test
    void parsesVersionsInFileOrderWithBodies() {
        String md = """
                # Changelog

                ## 2.1.204

                - Fixed hook events not streaming during SessionStart hooks in headless
                sessions, which could cause remote workers to be idle-reaped mid-hook

                ## 2.1.203

                - Added a warning when your login is about to expire
                - Added a grey badge to the footer
                """;

        List<ReleaseNote> notes = parser.parse(md);

        assertThat(notes).hasSize(2);
        assertThat(notes.get(0).version()).isEqualTo("2.1.204");
        assertThat(notes.get(1).version()).isEqualTo("2.1.203");
    }

    @Test
    void preservesMultilineBulletContent() {
        String md = """
                ## 2.1.204

                - Fixed hook events not streaming during SessionStart hooks in headless
                sessions, which could cause remote workers to be idle-reaped mid-hook
                """;

        List<ReleaseNote> notes = parser.parse(md);

        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).body())
                .contains("Fixed hook events")
                .contains("idle-reaped mid-hook");
    }

    @Test
    void ignoresTitleAndSubheadings() {
        String md = """
                # Changelog
                ## 1.2.3
                - item one
                ### Sub notes
                - item two
                """;

        List<ReleaseNote> notes = parser.parse(md);

        // "# Changelog" (title) 와 "### Sub notes" (하위 헤딩) 는 버전으로 잡히지 않는다.
        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).version()).isEqualTo("1.2.3");
        assertThat(notes.get(0).body())
                .contains("item one")
                .contains("item two");
    }

    @Test
    void handlesEmptyAndNullInput() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse("# Changelog\n\nno versions here")).isEmpty();
    }
}
