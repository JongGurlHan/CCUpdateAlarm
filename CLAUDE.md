# CLAUDE.md

이 파일은 Claude Code(claude.ai/code)가 이 저장소에서 작업할 때 참고하는 가이드입니다.

## 이 프로젝트는 무엇인가

`cc-notify`는 Claude Code [CHANGELOG.md](https://github.com/anthropics/claude-code/blob/main/CHANGELOG.md)를 폴링하여, 새 릴리즈마다 Anthropic Messages API(Claude Sonnet)로 한국어로 번역한 뒤 Telegram 구독자들에게 팬아웃(fan-out)합니다. 하나의 릴리즈는 **한 번만 번역되어 캐시**되므로, 번역 비용은 구독자 수와 무관합니다. 사용자에게 노출되는 문자열과 코드 주석은 한국어로 작성합니다.

Spring Boot 3.3.4 · Java 21 · Gradle · 파일 기반 H2 데이터베이스 위의 JPA.

## 명령어

Windows/PowerShell이 기본 셸입니다(`.\gradlew.bat`). 

```powershell
.\gradlew.bat build          # 컴파일 + 테스트 실행
.\gradlew.bat bootRun        # 로컬 실행 (`local` 프로파일을 자동 활성화 — 아래 참고)
.\gradlew.bat test           # 테스트 실행
.\gradlew.bat test --tests "com.example.ccnotify.changelog.ChangelogParserTest"   # 단일 테스트 클래스
```

스케줄을 기다리지 않고 즉시 폴링을 트리거하기(로컬, 인증 불필요):


H2 웹 콘솔: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:file:./data/ccnotify`, 사용자 `sa`, 비밀번호 없음).

## 설정 & 시크릿

- 비밀이 아닌 설정은 `src/main/resources/application.yml`의 `app.*` 프리픽스 아래에 있으며, `AppProperties` 레코드(`config/AppProperties.java`)에 바인딩됩니다.
- 시크릿(Telegram 봇 토큰/사용자명, 관리자 chat id, Anthropic API 키)은 두 가지 방식으로 주입됩니다:
  - **로컬 개발(권장):** `application-local.yml.example` → `application-local.yml`을 **프로젝트 루트에** 복사한 뒤 값을 채우세요. `bootRun`은 `local` 프로파일을 자동 활성화하며(커맨드라인이 아니라 `build.gradle`에 연결되어 있음), 이 파일을 로드합니다. `java -jar`는 이를 활성화하지 **않습니다**.
  - **CI/배포:** `application.yml`에 이미 들어 있는 환경 변수 플레이스홀더(`${TELEGRAM_BOT_TOKEN:}`, `${ANTHROPIC_API_KEY:}` 등).


## 아키텍처

### 폴 파이프라인 (`changelog/ChangelogPoller.java`)

오케스트레이터입니다. `SchedulingConfig`가 `scheduledPoll`을 고정 지연(fixed-delay) 태스크로 등록합니다(`app.poll-interval`, 기본 15분). 스케줄러와 수동 `/api/admin/poll` 트리거 모두 `pollOnce()`를 호출하며, `ReentrantLock.tryLock()`으로 가드되어 중첩 실행은 큐잉되지 않고 건너뜁니다.

한 번의 폴은 다음을 수행합니다: **fetch → parse → 새 릴리즈 탐지 → (릴리즈별로 translate → save → dispatch)**.

- `ChangelogFetcher`가 원본 마크다운을 가져오고, `ChangelogParser`가 `## X.Y.Z` 헤딩을 기준으로 `ReleaseNote`들로 분할합니다. **파서는 최신→과거(파일 순서)로 반환하며, 폴러가 과거→최신으로 뒤집어** 알림이 릴리즈 순서대로 도착하도록 합니다.
- "새 릴리즈"란 `processed_release` 테이블에 존재하지 않는 버전을 의미합니다.
- **첫 실행**(빈 테이블 + `app.baseline-on-first-run=true`): 현재의 모든 버전이 `baseline` 행으로 저장되고 **아무것도 전송되지 않습니다**. 그 이후에 등장하는 버전만 알림됩니다.
- 새 릴리즈마다: 번역하고, **`ProcessedRelease` 행을 저장한 뒤 dispatch**합니다. 번역이나 저장이 예외를 던지면 행이 **저장되지 않으므로** 다음 폴에서 재시도되며, `AdminNotifier`가 관리자에게 알립니다. 이 save-before-dispatch 순서가 멱등성(idempotency) 메커니즘입니다 — 저장된 버전은 절대 재처리되지 않습니다.

### 번역 (`translate/`)

`TranslationService`가 한국어 번역 프롬프트를 구성하고(릴리즈당 API 호출 1회, 모든 불릿을 한 번에 처리), `ClaudeClient`가 Anthropic Messages API를 감쌉니다. 실패는 예외로 전파되어 폴러가 catch/재시도합니다.

### 디스패치 & Telegram 전송 (`dispatch/NotificationDispatcher.java`, `telegram/`)

`NotificationDispatcher`가 번역된 릴리즈를 `ACTIVE` 상태의 Telegram 구독자들에게 팬아웃하며, `app.telegram.send-throttle-ms`로 스로틀링합니다. 메시지는 HTML(`TelegramText.escapeHtml`)이며 Telegram의 4096자 제한(`TelegramText.split`)에서 분할됩니다. `app.dry-run=true`이면 전송 대신 메시지를 로깅합니다. 구독자별 `SendOutcome`가 동작을 결정합니다: `BLOCKED`(403/400)는 구독자를 자동 비활성화하고, `RATE_LIMITED`(429)는 `retry_after` 이후 재시도합니다.

### 구독 라이프사이클 (`subscriber/`)

`Subscriber` 엔티티의 상태 머신: **PENDING → ACTIVE → UNSUBSCRIBED**.

1. 웹 "Telegram으로 받기"(`POST /api/subscribe/telegram`)가 랜덤 `connect_token`(TTL `app.connect-token-ttl`)을 가진 PENDING 행을 생성하고 `https://t.me/<bot>?start=<token>` 딥링크를 반환합니다.
2. `TelegramLongPollingRunner`(`ApplicationReadyEvent`에서 시작되는 백그라운드 데몬 스레드, `getUpdates` 롱폴링 사용 — 공개 URL/웹훅 불필요)가 업데이트를 `TelegramCommandHandler`로 전달합니다.
3. `/start <token>` → `SubscriptionService.confirmConnect`가 토큰을 검증하고 `chat_id`를 채운 뒤 ACTIVE로 전환하고 `unsub_token`을 발급합니다. `/stop`은 chat id로 구독을 해지하고, `/status`는 상태를 보고합니다.
4. 웹 구독 해지(`GET /unsubscribe?token=`)는 `unsub_token`을 사용합니다.

### HTTP 클라이언트 (`config/HttpClientConfig.java`)

서로 다른 타임아웃을 가진 세 개의 `RestClient` 빈: `changelogRestClient`, `anthropicRestClient`(`x-api-key`/`anthropic-version` 헤더 추가), `telegramRestClient`(`getUpdates` 롱폴링이 동작하도록 긴 read 타임아웃).

### 영속성

`./data/ccnotify`의 H2 파일 DB, `ddl-auto=update`(마이그레이션 없음). 두 개의 테이블: `processed_release`(중복 제거 + 번역 캐시, `baseline` 플래그는 첫 실행 전용 행을 표시)와 `subscriber`. 도메인 상태 전이는 서비스가 아니라 엔티티의 팩토리/뮤테이터 메서드(예: `Subscriber.activate`, `ProcessedRelease.baseline/notified`)에 정의되어 있습니다.

## 참고 사항

- `ChangelogParserTest`만 존재합니다. 파서가 테스트할 가치가 있는 주요 순수 로직입니다.
- 실제 릴리즈를 기다리지 않고 로컬에서 번역/디스패치를 실습하려면: `app.baseline-on-first-run=false`(기존 버전을 새 릴리즈로 취급)로 설정하거나, `app.dry-run=true`(전송 없이 번역 결과 확인)로 설정하세요.
