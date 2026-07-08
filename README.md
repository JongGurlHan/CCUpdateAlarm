# cc-notify — Claude Code 릴리즈 노트 한글 알림 (Telegram)

Claude Code의 [CHANGELOG.md](https://github.com/anthropics/claude-code/blob/main/CHANGELOG.md)를
주기적으로 폴링해, 새 릴리즈가 올라오면 **한국어로 번역(Claude Sonnet)** 하여 **Telegram** 구독자에게 알림을 보냅니다.

- 웹 페이지에서 "Telegram으로 받기" → 봇 딥링크 → 봇에서 **시작** → 구독 완료
- 릴리즈당 **1회 번역 후 캐시** → 구독자에게 fan-out (번역비는 유저 수와 무관)
- Slack 채널은 후순위(TODO)

## 사전 준비물 (시크릿)

| 값 | 설명 | 발급처 |
|---|---|---|
| 봇 토큰 | Telegram 봇 토큰 | [@BotFather](https://t.me/BotFather) `/newbot` |
| 봇 username | 봇 username (`@` 제외) | `/newbot` 때 지정한 이름 |
| 관리자 chat_id | 운영 장애 알림 받을 본인 chat_id | 아래 참고 |
| Anthropic API 키 | 번역(Claude Sonnet)용 | console.anthropic.com (결제/크레딧 필요) |

> **chat_id 확인:** 봇에게 아무 메시지나 보낸 뒤
> `https://api.telegram.org/bot<봇토큰>/getUpdates` 를 열면 `message.chat.id` 로 확인. 또는 [@userinfobot](https://t.me/userinfobot).

## 빌드 & 실행

### 1) 시크릿을 `application-local.yml` 로 관리 (권장)

프로젝트 루트의 `application-local.yml.example` 을 복사해 값을 채웁니다. `local` 프로파일로 실행하면
Spring 이 이 파일을 읽어 시크릿을 주입합니다. (외부 라이브러리 불필요, `.gitignore` 에 이미 제외됨)

```powershell
# 루트에 application-local.yml 생성 (한 번만)
Copy-Item application-local.yml.example application-local.yml
# → application-local.yml 을 열어 실제 값 기입

# 빌드 + 테스트
.\gradlew.bat build

# 실행 (local 프로파일은 build.gradle 의 bootRun 설정에 내장 → 옵션 불필요)
.\gradlew.bat bootRun
```

> **주의:** `application-local.yml` 은 반드시 **프로젝트 루트**에 둡니다. `src/main/resources/` 아래 두면
> 빌드 시 jar 안에 시크릿이 박히므로 금지. 값 얻는 방법은 `application-local.yml.example` 주석 참고.
>
> `bootRun` 은 `build.gradle` 에서 `spring.profiles.active=local` 을 자동으로 붙이도록 해두었습니다
> (npm 의 `start:dev` 처럼 옵션을 스크립트에 숨긴 것). 그래서 실행은 `.\gradlew.bat bootRun` 한 줄이면 됩니다.

### 2) 대안 — 환경변수 (CI/배포용)

`application.yml` 은 `${TELEGRAM_BOT_TOKEN:}` 등 환경변수 플레이스홀더도 그대로 지원합니다.

```powershell
$env:TELEGRAM_BOT_TOKEN="..."; $env:TELEGRAM_BOT_USERNAME="..."
$env:TELEGRAM_ADMIN_CHAT_ID="..."; $env:ANTHROPIC_API_KEY="sk-ant-..."
.\gradlew.bat bootRun
# 또는
java -jar build/libs/cc-notify-0.0.1-SNAPSHOT.jar
```

시크릿이 없어도 앱은 기동되며, Telegram 미설정 시 롱폴링만 비활성화됩니다(폴링/파싱은 동작).
앱이 뜨면 <http://localhost:8080> 에서 신청 페이지를 볼 수 있습니다.

## 엔드포인트

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/` | 신청 페이지 |
| POST | `/api/subscribe/telegram` | 딥링크(`https://t.me/<bot>?start=<token>`) 발급 |
| GET | `/unsubscribe?token=` | 웹 해지 (웰컴 메시지의 링크) |
| POST | `/api/admin/poll` | 스케줄을 기다리지 않고 즉시 1회 폴링 (테스트용) |

봇 명령어: `/start <token>`(연결), `/stop`(해지), `/status`(상태).

## 동작 방식

1. `ChangelogPoller` 가 `app.poll-interval`(기본 15분)마다 raw CHANGELOG.md를 폴링.
2. `ChangelogParser` 로 `## X.Y.Z` 버전 파싱 → DB(`processed_release`)에 없는 신규만 추림.
3. **최초 실행**: 현재 모든 버전을 `baseline` 으로만 저장(발송 안 함) → 이후 신규부터 알림.
4. 신규는 오래된→최신 순으로 **버전별 개별 메시지**로: 번역 → 저장(캐시) → `ACTIVE` 구독자 fan-out.
5. 번역/발송 실패 시 해당 버전은 저장하지 않아 다음 폴링에 재시도하고, 관리자에게 알림.

## 주요 설정 (`application.yml`, `app.*`)

- `poll-interval` (기본 `15m`) — 폴링 주기
- `dry-run` (기본 `false`) — `true` 면 실제 발송 대신 로그만 (개발용)
- `baseline-on-first-run` (기본 `true`) — 최초 실행 baseline 처리
- `connect-token-ttl` (기본 `10m`) — 웹 신청 후 `/start` 연결 확정 제한 시간
- `public-base-url` (기본 `http://localhost:8080`) — 해지 링크 생성용
- `anthropic.model` (기본 `claude-sonnet-5`)

## 테스트

```bash
./gradlew test          # ChangelogParser 단위 테스트
```

로컬 파이프라인 확인: 앱 실행 후 `POST /api/admin/poll` 로 즉시 폴링을 트리거하고 로그를 확인.
번역/발송까지 보려면 `baseline-on-first-run=false` 또는 이미 baseline이 저장된 상태에서 새 버전이
올라올 때를 기다리거나, `dry-run=true` 로 발송 없이 번역 로그만 확인할 수 있습니다.

## TODO

- Slack "Add to Slack" OAuth 채널 (공개 HTTPS 도메인 필요 → 배포 환경 결정 시)
- 배포(H2 → PostgreSQL 은 datasource 설정 교체), (release, subscriber) 발송 이력 테이블
