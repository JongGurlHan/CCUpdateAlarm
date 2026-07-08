package com.example.ccnotify.subscriber;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {

    Optional<Subscriber> findByConnectToken(String connectToken);

    Optional<Subscriber> findByUnsubToken(String unsubToken);

    List<Subscriber> findByChannelAndChatId(Channel channel, Long chatId);

    List<Subscriber> findByChannelAndStatus(Channel channel, SubscriberStatus status);
}
