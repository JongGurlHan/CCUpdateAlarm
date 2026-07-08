package com.example.ccnotify.subscriber;

import com.example.ccnotify.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * 구독 생명주기: 연결토큰 발급 → /start 확정 → 해지.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SubscriberRepository repository;
    private final AppProperties props;

    public SubscriptionService(SubscriberRepository repository, AppProperties props) {
        this.repository = repository;
        this.props = props;
    }

    /** 결과 상태. */
    public enum ConnectResult {
        SUCCESS, INVALID, EXPIRED
    }

    /** 연결 확정 결과 + (성공 시) 웹 해지에 사용할 unsub 토큰. */
    public record ConnectOutcome(ConnectResult result, String unsubToken) {
    }

    /**
     * 웹 신청 처리: PENDING 구독자 생성 + 연결토큰 발급 후 Telegram 딥링크를 반환한다.
     */
    @Transactional
    public String createTelegramConnectLink() {
        String token = randomToken();
        Instant expiresAt = Instant.now().plus(props.connectTokenTtl());
        repository.save(Subscriber.pendingTelegram(token, expiresAt));
        String botUsername = props.telegram().botUsername();
        return "https://t.me/" + botUsername + "?start=" + token;
    }

    /**
     * /start &lt;token&gt; 수신 시 연결 확정. 동일 chat_id 의 기존 구독행은 정리(중복 방지)한다.
     */
    @Transactional
    public ConnectOutcome confirmConnect(String token, long chatId) {
        Optional<Subscriber> found = repository.findByConnectToken(token);
        if (found.isEmpty() || found.get().getStatus() != SubscriberStatus.PENDING) {
            return new ConnectOutcome(ConnectResult.INVALID, null);
        }
        Subscriber pending = found.get();
        if (pending.isTokenExpired(Instant.now())) {
            repository.delete(pending);
            return new ConnectOutcome(ConnectResult.EXPIRED, null);
        }

        // 같은 chat_id 의 기존 구독행 제거(중복 ACTIVE 방지)
        List<Subscriber> existing = repository.findByChannelAndChatId(Channel.TELEGRAM, chatId);
        if (!existing.isEmpty()) {
            repository.deleteAll(existing);
        }

        pending.activate(chatId, randomToken());
        repository.save(pending);
        log.info("Telegram 구독 확정 chatId={}", chatId);
        return new ConnectOutcome(ConnectResult.SUCCESS, pending.getUnsubToken());
    }

    /** 봇 /stop 명령: chat_id 기준 해지. 활성 구독이 있었는지 반환. */
    @Transactional
    public boolean unsubscribeByChatId(long chatId) {
        List<Subscriber> subs = repository.findByChannelAndChatId(Channel.TELEGRAM, chatId);
        boolean hadActive = false;
        for (Subscriber s : subs) {
            if (s.getStatus() == SubscriberStatus.ACTIVE) {
                hadActive = true;
                s.unsubscribe();
                repository.save(s);
            }
        }
        return hadActive;
    }

    /** 웹 해지 링크: unsub_token 기준 해지. 성공 여부 반환. */
    @Transactional
    public boolean unsubscribeByToken(String unsubToken) {
        Optional<Subscriber> found = repository.findByUnsubToken(unsubToken);
        if (found.isEmpty() || found.get().getStatus() != SubscriberStatus.ACTIVE) {
            return false;
        }
        Subscriber s = found.get();
        s.unsubscribe();
        repository.save(s);
        log.info("웹 해지 chatId={}", s.getChatId());
        return true;
    }

    @Transactional(readOnly = true)
    public boolean isActive(long chatId) {
        return repository.findByChannelAndChatId(Channel.TELEGRAM, chatId).stream()
                .anyMatch(s -> s.getStatus() == SubscriberStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Subscriber> activeTelegramSubscribers() {
        return repository.findByChannelAndStatus(Channel.TELEGRAM, SubscriberStatus.ACTIVE);
    }

    /** 발송 실패(봇 차단 등)로 더 이상 유효하지 않은 구독자를 비활성화한다. */
    @Transactional
    public void deactivate(Long subscriberId) {
        repository.findById(subscriberId).ifPresent(s -> {
            s.unsubscribe();
            repository.save(s);
            log.info("구독자 자동 비활성화 id={} chatId={}", s.getId(), s.getChatId());
        });
    }

    private static String randomToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
