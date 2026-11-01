package com.chesscoach.game;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Хранит партии в памяти: при перезапуске приложения они пропадают. */
@Repository
public class InMemoryGameRepository implements GameRepository {

    private final Map<String, Game> games = new ConcurrentHashMap<>();

    @Override
    public void save(Game game) {
        games.put(game.id(), game);
    }

    @Override
    public Optional<Game> findById(String id) {
        return Optional.ofNullable(games.get(id));
    }
}
