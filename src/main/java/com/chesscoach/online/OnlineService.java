package com.chesscoach.online;

import com.chesscoach.archive.ArchiveService;
import com.chesscoach.chess.Board;
import com.chesscoach.chess.Move;
import com.chesscoach.game.GameNotFoundException;
import com.chesscoach.game.GameStateException;
import com.chesscoach.game.IllegalMoveException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Друзья, вызовы, подбор соперника и партии между людьми. Запросов мало и они быстрые, поэтому блокировка одна. */
@Service
public class OnlineService {

    private final PlayerStore players;
    private final OnlineEvents events;
    private final OnlineGameStore store;
    private final ArchiveService archive;
    private final Set<String> queue = new LinkedHashSet<>();
    /** Вызовы: кто → кого. */
    private final Map<String, Set<String>> challenges = new HashMap<>();
    private final Map<String, OnlineGame> games = new HashMap<>();
    private final Map<String, OnlineGame> activeByPlayer = new HashMap<>();

    public OnlineService(PlayerStore players, OnlineEvents events, OnlineGameStore store,
                         ArchiveService archive) {
        this.archive = archive;
        this.players = players;
        this.events = events;
        this.store = store;
        for (OnlineGame game : store.loadAll()) {
            games.put(game.id(), game);
            if (game.isActive()) {
                activeByPlayer.put(game.white(), game);
                activeByPlayer.put(game.black(), game);
            } else {
                archive.saveIfMissing(game);
            }
        }
    }

    // ---------- Присутствие ----------

    public SseEmitter subscribe(String me) {
        SseEmitter emitter = events.subscribe(me, () -> onDisconnected(me));
        notifyFriends(me);
        return emitter;
    }

    private synchronized void onDisconnected(String me) {
        if (events.isOnline(me)) return;
        queue.remove(me);
        notifyFriends(me);
    }

    private void notifyFriends(String me) {
        players.find(me).ifPresent(player -> events.notifyPlayers(player.friends()));
    }

    // ---------- Состояние ----------

    public synchronized OnlineState state(String me) {
        PlayerAccount player = account(me);
        OnlineGame active = activeByPlayer.get(me);
        List<OnlineState.Friend> friends = player.friends().stream()
                .map(friend -> new OnlineState.Friend(nickname(friend), events.isOnline(friend),
                        activeByPlayer.containsKey(friend)))
                .sorted(Comparator.comparing(OnlineState.Friend::nickname, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<String> outgoing = players.all().stream()
                .filter(other -> other.incoming().contains(me))
                .map(PlayerAccount::nickname).sorted().toList();
        List<String> incomingChallenges = challenges.entrySet().stream()
                .filter(entry -> entry.getValue().contains(me))
                .map(entry -> nickname(entry.getKey())).sorted().toList();
        List<String> outgoingChallenges = challenges.getOrDefault(me, Set.of()).stream()
                .map(this::nickname).sorted().toList();
        return new OnlineState(player.nickname(), friends,
                player.incoming().stream().map(this::nickname).sorted().toList(), outgoing,
                incomingChallenges, outgoingChallenges, queue.contains(me),
                active == null ? null : active.id(),
                active == null ? null : nickname(active.opponentOf(me)));
    }

    public synchronized String nickname(String key) {
        return players.find(key).map(PlayerAccount::nickname).orElse(key);
    }

    // ---------- Друзья ----------

    /** Если этот игрок уже прислал заявку вам, встречная заявка сразу делает вас друзьями. */
    public synchronized void requestFriend(String me, String nickname) {
        PlayerAccount other = players.find(nickname == null ? "" : nickname)
                .orElseThrow(() -> new IllegalArgumentException("Игрок с таким ником не найден"));
        String target = PlayerStore.key(other.nickname());
        if (target.equals(me)) throw new IllegalArgumentException("Нельзя добавить в друзья самого себя");
        PlayerAccount player = account(me);
        if (player.friends().contains(target)) throw new GameStateException("Вы уже друзья");
        if (player.incoming().contains(target)) {
            acceptFriend(me, other.nickname());
            return;
        }
        other.incoming().add(me);
        players.save();
        events.notifyPlayers(List.of(me, target));
    }

    public synchronized void acceptFriend(String me, String nickname) {
        String from = PlayerStore.key(nickname);
        PlayerAccount player = account(me);
        if (!player.incoming().remove(from)) throw new GameStateException("Заявки от этого игрока нет");
        player.friends().add(from);
        account(from).friends().add(me);
        players.save();
        events.notifyPlayers(List.of(me, from));
    }

    public synchronized void declineFriend(String me, String nickname) {
        String from = PlayerStore.key(nickname);
        if (account(me).incoming().remove(from)) players.save();
        events.notifyPlayers(List.of(me, from));
    }

    public synchronized void removeFriend(String me, String nickname) {
        String other = PlayerStore.key(nickname);
        account(me).friends().remove(other);
        players.find(other).ifPresent(friend -> friend.friends().remove(me));
        challenges.getOrDefault(me, new HashSet<>()).remove(other);
        challenges.getOrDefault(other, new HashSet<>()).remove(me);
        players.save();
        events.notifyPlayers(List.of(me, other));
    }

    // ---------- Вызовы друзей ----------

    /** Встречный вызов сразу начинает партию. */
    public synchronized void challenge(String me, String nickname) {
        String target = PlayerStore.key(nickname);
        if (!account(me).friends().contains(target)) throw new GameStateException("Вызвать можно только друга");
        requireFree(me, "Сначала закончите текущую партию");
        if (challenges.getOrDefault(target, Set.of()).contains(me)) {
            acceptChallenge(me, nickname);
            return;
        }
        challenges.computeIfAbsent(me, key -> new HashSet<>()).add(target);
        events.notifyPlayers(List.of(me, target));
    }

    public synchronized void acceptChallenge(String me, String nickname) {
        String from = PlayerStore.key(nickname);
        if (!challenges.getOrDefault(from, Set.of()).contains(me)) throw new GameStateException("Вызов уже отменён");
        requireFree(me, "Сначала закончите текущую партию");
        requireFree(from, "Соперник уже играет другую партию");
        startGame(from, me);
    }

    /** Отказ на входящий вызов или отмена своего. */
    public synchronized void dropChallenge(String me, String nickname) {
        String other = PlayerStore.key(nickname);
        challenges.getOrDefault(me, new HashSet<>()).remove(other);
        challenges.getOrDefault(other, new HashSet<>()).remove(me);
        events.notifyPlayers(List.of(me, other));
    }

    // ---------- Подбор соперника ----------

    public synchronized void joinQueue(String me) {
        requireFree(me, "Сначала закончите текущую партию");
        String opponent = queue.stream().filter(other -> !other.equals(me)).findFirst().orElse(null);
        if (opponent == null) {
            queue.add(me);
            events.notifyPlayers(List.of(me));
        } else {
            startGame(opponent, me);
        }
    }

    public synchronized void leaveQueue(String me) {
        queue.remove(me);
        events.notifyPlayers(List.of(me));
    }

    // ---------- Партия ----------

    public synchronized OnlineGame game(String me, String id) {
        OnlineGame game = games.get(id);
        if (game == null || !game.hasPlayer(me)) throw new GameNotFoundException();
        return game;
    }

    public synchronized OnlineGame move(String me, String id, String uci) {
        OnlineGame online = game(me, id);
        if (!online.isActive()) throw new GameStateException("Партия уже закончена");
        Board board = online.game().board();
        if (board.isWhiteToMove() != online.white().equals(me)) throw new GameStateException("Сейчас не ваш ход");
        Move move = Move.parseUci(uci);
        if (!board.legalMoves().contains(move)) throw new IllegalMoveException(uci);
        online.play(move);
        afterChange(online);
        return online;
    }

    public synchronized OnlineGame resign(String me, String id) {
        OnlineGame online = game(me, id);
        if (!online.isActive()) throw new GameStateException("Партия уже закончена");
        online.resign(me);
        afterChange(online);
        return online;
    }

    private void afterChange(OnlineGame online) {
        store.save(online);
        if (!online.isActive()) {
            archive.saveFinishedOnlineGame(online);
            activeByPlayer.remove(online.white());
            activeByPlayer.remove(online.black());
            notifyWithFriends(online);
        } else {
            events.notifyPlayers(List.of(online.white(), online.black()));
        }
    }

    private void startGame(String first, String second) {
        boolean firstWhite = ThreadLocalRandom.current().nextBoolean();
        OnlineGame online = firstWhite ? new OnlineGame(first, second) : new OnlineGame(second, first);
        store.save(online);
        games.put(online.id(), online);
        activeByPlayer.put(first, online);
        activeByPlayer.put(second, online);
        queue.remove(first);
        queue.remove(second);
        clearChallenges(first);
        clearChallenges(second);
        notifyWithFriends(online);
    }

    /** Отменяет вызовы игрока и сообщает всем, кого это касалось. */
    private void clearChallenges(String player) {
        List<String> affected = new ArrayList<>(challenges.getOrDefault(player, Set.of()));
        challenges.remove(player);
        challenges.forEach((from, targets) -> {
            if (targets.remove(player)) affected.add(from);
        });
        events.notifyPlayers(affected);
    }

    /** Друзья видят, занят ли игрок. */
    private void notifyWithFriends(OnlineGame online) {
        events.notifyPlayers(List.of(online.white(), online.black()));
        notifyFriends(online.white());
        notifyFriends(online.black());
    }

    private void requireFree(String player, String message) {
        if (activeByPlayer.containsKey(player)) throw new GameStateException(message);
    }

    private PlayerAccount account(String key) {
        return players.find(key).orElseThrow(NotAuthenticatedException::new);
    }
}
