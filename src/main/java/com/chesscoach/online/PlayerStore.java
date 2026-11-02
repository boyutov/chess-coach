package com.chesscoach.online;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Игроки и друзья в базе. Читаются из памяти: игроков мало, а обращаются к ним при каждом запросе. */
@Repository
public class PlayerStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Map<String, PlayerAccount> players = new ConcurrentHashMap<>();

    public PlayerStore(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        load();
    }

    public static String key(String nickname) {
        return nickname.trim().toLowerCase(Locale.ROOT);
    }

    public Optional<PlayerAccount> find(String nickname) {
        return Optional.ofNullable(players.get(key(nickname)));
    }

    public Collection<PlayerAccount> all() {
        return List.copyOf(players.values());
    }

    public synchronized void put(PlayerAccount player) {
        String key = key(player.nickname());
        jdbc.update("INSERT INTO players (player_key, nickname, salt, hash) VALUES (?, ?, ?, ?)",
                key, player.nickname(), player.salt(), player.hash());
        players.put(key, player);
    }

    /** Переписывает связи целиком. При паре игроков это проще и надёжнее, чем считать разницу. */
    public synchronized void save() {
        List<Object[]> friends = new ArrayList<>();
        List<Object[]> requests = new ArrayList<>();
        players.forEach((key, player) -> {
            player.friends().forEach(friend -> friends.add(new Object[]{key, friend}));
            player.incoming().forEach(from -> requests.add(new Object[]{from, key}));
        });
        transaction.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM friendships");
            jdbc.update("DELETE FROM friend_requests");
            jdbc.batchUpdate("INSERT INTO friendships (player, friend) VALUES (?, ?)", friends);
            jdbc.batchUpdate("INSERT INTO friend_requests (from_player, to_player) VALUES (?, ?)", requests);
        });
    }

    private void load() {
        jdbc.query("SELECT player_key, nickname, salt, hash FROM players", row -> {
            players.put(row.getString(1), new PlayerAccount(row.getString(2), row.getString(3), row.getString(4),
                    new HashSet<>(), new HashSet<>()));
        });
        jdbc.query("SELECT player, friend FROM friendships",
                row -> {
                    players.get(row.getString(1)).friends().add(row.getString(2));
                });
        jdbc.query("SELECT from_player, to_player FROM friend_requests",
                row -> {
                    players.get(row.getString(2)).incoming().add(row.getString(1));
                });
    }
}
