---
name: cc-notify-review
description: >-
  cc-notify(Claude Code 릴리즈 노트 한국어 Telegram 알림 서비스)의 코드를 리뷰할 때 사용합니다.
  이 저장소에서 diff/PR/변경 파일을 리뷰하거나, Java 변경을 커밋·푸시하기 전에 점검하거나,
  "코드 리뷰", "리뷰해줘", "이 변경 괜찮아?", "커밋 전에 봐줘" 같은 요청이 있을 때 반드시 이 스킬을 사용하세요.
  이 프로젝트에는 조용히 깨지는 불변식(save-before-dispatch 멱등성, 파서/폴러 순서 역전, 최초 실행 baseline,
  HTML 이스케이프, 릴리즈당 번역 1회 캐시, 논블로킹 폴 락)이 있어서, 일반적인 리뷰로는 놓치기 쉽습니다.
  버그·정확성, 보안, 성능·테스트, 컨벤션·가독성을 이 프로젝트 맥락에 맞춰 한국어로 리뷰합니다.
---

# cc-notify 코드 리뷰

## 이 스킬이 하는 일

cc-notify는 Claude Code CHANGELOG를 폴링 → Anthropic API로 한국어 번역 → Telegram 구독자에게 팬아웃하는
Spring Boot(Java 21) 서비스입니다. 이 도메인에는 **테스트를 통과하고 컴파일도 되지만 운영에서 조용히 사고를
내는** 불변식들이 있습니다(중복 알림, 최초 실행 스팸, 메시지 깨짐, 번역 비용 폭증, 시크릿 노출). 이 스킬은
그 불변식을 중심으로 변경을 검토하고, **심각도 순으로 정리된 한국어 리뷰**를 제공합니다.

일반적인 스타일 지적이 목표가 아닙니다. **이 프로젝트에서만 알 수 있는 함정**을 잡는 것이 목표입니다.

## 리뷰 진행 방법

1. **범위 확정.** 사용자가 파일/PR을 지정했으면 그 범위를, 아니면 작업 중인 변경을 대상으로 합니다.
   - 커밋 전 변경: `git diff HEAD`, 스테이징 포함 시 `git diff HEAD` (unstaged+staged 모두), 새 파일 포함은 `git status`로 확인.
   - 브랜치 변경 전체: `git diff main...HEAD`.
   - diff만 보면 맥락이 부족할 때가 많습니다. **변경된 파일은 전체를 읽어** 주변 코드와의 정합성을 확인하세요.
2. **변경의 의도 파악.** 이 변경이 폴 파이프라인·번역·디스패치·구독 중 어디를 건드리는지 먼저 식별합니다.
   건드린 모듈의 불변식(아래)을 우선 적용합니다.
3. **불변식 우선 검토 → 4개 영역 체크리스트 → 리포트 작성.** 불변식 위반이 최우선입니다.
4. 필요하면 근거 확인을 위해 실제로 검증하세요. 예: 파서/문자열 로직 변경이면
   `.\gradlew.bat test --tests "com.example.ccnotify.changelog.ChangelogParserTest"`.

## 최우선: 조용히 깨지는 불변식

아래는 이 프로젝트에서 **가장 사고가 큰 지점**입니다. 변경이 이 영역에 닿으면 최우선으로 확인하세요.
각 항목의 파일:라인은 "정답(canonical) 패턴"의 위치입니다 — 변경이 이 패턴을 유지하는지 대조하세요.

### 1. save-before-dispatch = 멱등성 (`changelog/ChangelogPoller.java:110-112`)
릴리즈별 처리 순서는 반드시 **번역 → `repository.save(ProcessedRelease.notified(...))` → `dispatcher.dispatchRelease(...)`** 입니다.
- 이 순서가 뒤집히거나(발송 후 저장), save와 dispatch가 다른 try 블록으로 갈라지면 **중복 알림**이 발생합니다.
  저장된 버전은 다시 처리되지 않는다는 것이 유일한 중복 방지 장치입니다.
- 번역·저장이 예외를 던지면 **행을 저장하면 안 됩니다**(다음 폴에서 재시도). 예외를 삼키고 저장으로 넘어가면
  실패한 릴리즈가 "처리됨"으로 굳어 **영구 누락**됩니다.
- 루프 안의 try/catch가 개별 릴리즈를 격리하는 구조(한 건 실패가 나머지를 막지 않음)를 유지하는지 확인하세요.

### 2. 파서/폴러 순서 역전 (`changelog/ChangelogParser.java:16`, `ChangelogPoller.java:80-82`)
파서는 **파일 순서(최신 → 과거)** 를 그대로 반환하고, 폴러가 `Collections.reverse`로 **과거 → 최신** 으로
뒤집어 알림이 릴리즈 순서대로 도착합니다. 둘 중 하나만 바꾸면 **알림 순서가 뒤집힙니다**.
파서 정렬을 바꾸는 변경은 폴러의 reverse와 짝이 맞는지 반드시 함께 확인하세요.

### 3. 최초 실행 baseline (`ChangelogPoller.java:87-93`)
빈 테이블 + `baseline-on-first-run=true`이면 현재 모든 버전을 `baseline` 행으로 저장하고 **아무것도 발송하지
않습니다**. 이 가드가 약해지면 첫 실행에서 **수십 개 과거 릴리즈를 전 구독자에게 폭탄 발송**합니다.
빈 테이블 판정(`processedVersions.isEmpty()`)과 baseline 저장 경로가 온전한지 확인하세요.

### 4. HTML 이스케이프 (`util/TelegramText.java:18`, `dispatch/NotificationDispatcher.java:93-100`)
Telegram 메시지는 `parse_mode=HTML`입니다. 메시지에 삽입되는 **동적/외부 유래 값**(버전, 번역문 등)은 반드시
`TelegramText.escapeHtml`을 거쳐야 합니다. 누락하면 CHANGELOG의 `<`, `>`, `&` 때문에 메시지가 깨지거나
Telegram이 400을 반환합니다. 새 메시지 조립 경로나 새 삽입 값이 있으면 이스케이프 여부를 확인하세요.
(상수 링크 URL처럼 코드에 하드코딩된 정적 값은 이스케이프 대상이 아닙니다.)

### 5. 릴리즈당 번역 1회 = 비용 (`translate/TranslationService.java`, `ChangelogPoller.java:109-111`)
번역은 **릴리즈당 API 호출 1회**, 결과는 `translated_text`로 캐시됩니다. 그래서 번역 비용이 구독자 수와
무관합니다. 번역을 구독자 루프 안으로 옮기거나, 캐시를 우회해 재번역하거나, 불릿마다 호출로 쪼개는 변경은
**비용을 수십~수백 배로 키웁니다**. 디스패치(팬아웃)와 번역(1회)의 경계가 유지되는지 확인하세요.

### 6. 논블로킹 폴 락 (`ChangelogPoller.java:60-74`)
`pollOnce`는 `lock.tryLock()`으로 가드되어, 겹치는 실행은 **큐잉되지 않고 건너뜁니다**. 이걸 블로킹 `lock()`으로
바꾸면 스케줄러/수동 트리거가 밀려 쌓입니다. `unlock()`이 `finally`에 있는지, tryLock 실패 경로가
`skippedBusy()`로 빠지는지 확인하세요.

### 7. 시크릿 노출
봇 토큰, 관리자 chat id, Anthropic API 키는 절대 로그·예외 메시지·커밋에 남으면 안 됩니다.
- `application-local.yml`(gitignore 대상)이나 실제 키 값이 diff/커밋에 들어오지 않았는지 확인.
- 새 로그/예외 문자열에 토큰·키·전체 요청 헤더가 찍히지 않는지 확인.
- 시크릿은 `application.yml`의 `${ENV:}` 플레이스홀더나 `application-local.yml`로만 주입합니다.

## 영역별 체크리스트

### 버그·정확성
- 위 불변식 1·2·3·6 위반.
- **구독 상태 머신**(`subscriber/Subscriber.java`, `SubscriptionService.java`): PENDING → ACTIVE → UNSUBSCRIBED
  전이가 엔티티 팩토리/뮤테이터(`activate`, `unsubscribe`)로만 일어나는가. 서비스에서 상태를 직접 세팅하지 않는가.
  같은 `chat_id` 중복 ACTIVE 방지 로직(`confirmConnect`의 기존 행 삭제, `ChangelogPoller`와 무관)이 유지되는가.
- **연결 토큰**: TTL 만료 검사(`isTokenExpired`), 유효하지 않은/PENDING 아닌 토큰 거부가 살아있는가.
  토큰은 `SecureRandom` 기반이어야 함(`Math.random()`/`UUID` 예측 가능성 금지, `SubscriptionService.java:130-134` 참고).
- **발송 결과 분기**(`dispatch/NotificationDispatcher.java:61-91`): BLOCKED(403/400) → 구독자 비활성화,
  RATE_LIMITED(429) → `retry_after` 후 **횟수 제한** 재시도(`MAX_RATE_LIMIT_RETRIES`), TRANSIENT → 중단.
  새 전송 경로가 무한 재시도하거나 모든 실패를 동일 취급하지 않는가.
- **스레드 인터럽트**(`NotificationDispatcher.java:106-115`): `Thread.sleep` 사용 시 `InterruptedException`을 잡고
  `Thread.currentThread().interrupt()`로 플래그를 복원하는가(삼키면 종료 신호 유실).
- **널/경계**: `escapeHtml`/`split`은 null·빈 문자열을 방어함. 새 유틸/파서 로직도 동일하게 방어하는가.
- **JPA/트랜잭션**: 상태를 바꾸는 서비스 메서드에 `@Transactional`이 있는가. `open-in-view: false`이므로
  지연 로딩을 트랜잭션 밖에서 건드리지 않는가. 조회 전용은 `@Transactional(readOnly = true)`.

### 보안
- 위 불변식 4(HTML 인젝션)·7(시크릿).
- 외부 입력(Telegram 업데이트, CHANGELOG 본문)이 로그/메시지/쿼리로 흘러갈 때 신뢰하지 않는가.
- 관리자 전용 트리거(`/api/admin/poll`)나 H2 콘솔이 의도치 않게 공개 노출되도록 바뀌지 않았는가.
- 토큰·해지 링크가 로그로 새지 않는가.

### 성능·테스트
- 위 불변식 5(번역 비용).
- **N+1/불필요 조회**: 구독자·릴리즈 조회가 루프 안에서 반복 쿼리를 유발하지 않는가.
- **스로틀**(`send-throttle-ms`, `sendThrottleMs`): 팬아웃 발송 간 스로틀이 유지되는가(Telegram 레이트리밋 대비).
- **DB 컬럼 길이**: `original_text`/`translated_text`는 `length = 20000`, `version`은 `length = 64`
  (`release/ProcessedRelease.java:19-26`). 더 긴 값이 들어올 수 있는 변경이면 초과 저장 실패를 검토하세요.
- **RestClient 선택**(`config/HttpClientConfig.java`): 3개 빈이 타임아웃이 다릅니다. Telegram 롱폴링
  (`getUpdates`)은 긴 read 타임아웃 빈을 써야 하고, 번역 호출은 `anthropicRestClient`(인증 헤더 포함)를 써야
  합니다. 새 호출이 맞는 클라이언트를 주입받는지 확인하세요.
- **테스트**: 이 저장소는 순수 로직만 단위 테스트합니다(현재 `ChangelogParserTest`만 존재).
  파서·`TelegramText.split`/`escapeHtml`처럼 **네트워크·스프링 컨텍스트 없이 테스트 가능한 순수 로직을
  추가/변경**했다면 그에 대한 테스트를 기대하세요. 스프링 통합/네트워크 코드에 무거운 테스트를 강요하지는 마세요.

### 컨벤션·가독성
- **한국어**: 사용자 노출 문자열·로그·코드 주석은 한국어입니다(CLAUDE.md 규칙). 새 코드가 이를 따르는가.
- **도메인 로직 위치**: 상태 전이·불변식은 서비스가 아니라 **엔티티의 팩토리/뮤테이터**에 둡니다
  (`Subscriber.activate`, `ProcessedRelease.baseline/notified`). JPA용 `protected` 무인자 생성자 유지, public
  setter로 상태를 열지 않는가.
- **설정 바인딩**: 비밀 아닌 설정은 `application.yml`의 `app.*` → `config/AppProperties`(record)에 바인딩됩니다.
  하드코딩된 상수(URL, 간격, 임계값) 대신 `AppProperties`를 통하는가.
- 기존 파일의 주석 밀도·네이밍·관용구와 결이 맞는가. 과한 주석/불필요한 추상화는 지적하되, 프로젝트가 이미
  쓰는 패턴은 존중하세요.

## 리뷰 리포트 형식

한국어로, **심각도 순**으로 작성합니다. 각 지적은 **파일:라인 + 무엇이 문제인가 + 왜 문제인가(어떤 상황에서
깨지는가) + 어떻게 고치나**를 포함합니다. 추측이 아니라 코드 근거를 대세요. 문제가 없으면 없다고 명확히 말하고,
사소한 것을 억지로 만들지 마세요.

심각도 표기:
- 🔴 **심각**: 불변식 위반(중복 알림/누락/스팸/메시지 깨짐), 보안(시크릿 노출·인젝션), 비용 폭증.
- 🟡 **주의**: 정확성 결함, 경계·널 처리 누락, 성능 저하, 테스트 누락.
- 🔵 **제안**: 컨벤션·가독성·경미한 개선.

형식 예시:

```
## 코드 리뷰: <범위 요약>

### 🔴 심각
1. **save-before-dispatch 순서 뒤집힘** — `changelog/ChangelogPoller.java:112`
   - 문제: dispatch가 save보다 먼저 호출됩니다.
   - 영향: 발송은 됐는데 save 전에 예외가 나면 다음 폴에서 같은 릴리즈를 재발송 → 전 구독자 중복 알림.
   - 수정: `repository.save(...)`를 `dispatcher.dispatchRelease(...)` 앞으로 되돌리세요.

### 🟡 주의
...

### 🔵 제안
...

### 좋은 점
- (지킨 불변식/잘한 부분을 짧게 인정)

### 총평
- 머지 가능 여부와 남은 필수 수정 1~2줄 요약.
```

지적이 하나도 없을 때는 어떤 불변식을 확인했고 왜 안전한지 근거를 짧게 남겨, "대충 봤다"는 인상을 주지 마세요.
