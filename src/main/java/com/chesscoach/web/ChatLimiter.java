package com.chesscoach.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Ограничивает число вопросов тренеру в час: каждый вопрос стоит денег. */
@Component
public class ChatLimiter {

    private static final long WINDOW_MS = Duration.ofHours(1).toMillis();

    private final int maxPerHour;
    private final Map<String, Deque<Long>> asked = new HashMap<>();

    public ChatLimiter(@Value("${coach.max-questions-per-hour}") int maxPerHour) {
        this.maxPerHour = maxPerHour;
    }

    public synchronized void acquire(String player) {
        long now = System.currentTimeMillis();
        Deque<Long> times = asked.computeIfAbsent(player, key -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) times.pollFirst();
        if (times.size() >= maxPerHour) throw new LimitExceeded();
        times.addLast(now);
    }

    public static final class LimitExceeded extends RuntimeException {
        LimitExceeded() {
            super("Лимит вопросов тренеру на этот час исчерпан");
        }
    }
}
