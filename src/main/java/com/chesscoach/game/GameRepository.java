package com.chesscoach.game;

import java.util.Optional;

/** Хранилище партий. */
public interface GameRepository {

    void save(Game game);

    Optional<Game> findById(String id);
}
