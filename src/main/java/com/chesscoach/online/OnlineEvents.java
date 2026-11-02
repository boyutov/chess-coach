package com.chesscoach.online;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Сообщает браузерам игроков, что пора обновить данные. Открытый поток заодно означает, что игрок в сети.
 * Отправка идёт в отдельном потоке, чтобы медленный клиент не держал блокировки сервиса.
 */
@Component
public class OnlineEvents {

    private static final long HEARTBEAT_SECONDS = 20;

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor();

    public OnlineEvents() {
        // Запись в оборванное соединение падает с ошибкой: так находим тех, кто исчез молча.
        sender.scheduleAtFixedRate(this::heartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /** @param onClosed вызывается один раз, когда поток закрылся */
    public SseEmitter subscribe(String player, Runnable onClosed) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean closed = new AtomicBoolean();
        Runnable close = () -> {
            if (closed.compareAndSet(false, true)) {
                emitters.getOrDefault(player, List.of()).remove(emitter);
                onClosed.run();
            }
        };
        emitter.onCompletion(close);
        emitter.onTimeout(close);
        emitter.onError(error -> close.run());
        emitters.computeIfAbsent(player, key -> new CopyOnWriteArrayList<>()).add(emitter);
        notifyPlayers(List.of(player));
        return emitter;
    }

    public boolean isOnline(String player) {
        return !emitters.getOrDefault(player, List.of()).isEmpty();
    }

    public void notifyPlayers(Collection<String> players) {
        List<String> targets = List.copyOf(players);
        sender.execute(() -> targets.forEach(player -> send(player, SseEmitter.event().name("update").data("{}"))));
    }

    private void heartbeat() {
        emitters.keySet().forEach(player -> send(player, SseEmitter.event().comment("ping")));
    }

    private void send(String player, SseEmitter.SseEventBuilder event) {
        for (SseEmitter emitter : emitters.getOrDefault(player, List.of())) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
            }
        }
    }

    @PreDestroy
    void shutdown() {
        sender.shutdownNow();
        emitters.values().forEach(list -> list.forEach(SseEmitter::complete));
    }
}
