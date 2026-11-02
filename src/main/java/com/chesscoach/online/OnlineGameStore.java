package com.chesscoach.online;

import com.chesscoach.game.MoveRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Партии с людьми в базе. Ходы хранятся строкой UCI: позиции восстанавливаются их повтором. */
@Repository
public class OnlineGameStore {

    private final JdbcTemplate jdbc;

    public OnlineGameStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(OnlineGame game) {
        String moves = game.game().moves().stream().map(MoveRecord::move).map(move -> move.toUci())
                .collect(Collectors.joining(" "));
        int updated = jdbc.update("UPDATE online_games SET resigned_by = ?, moves = ? WHERE id = ?",
                game.resignedBy(), moves, game.id());
        if (updated == 0) {
            jdbc.update("INSERT INTO online_games (id, white, black, resigned_by, moves) VALUES (?, ?, ?, ?, ?)",
                    game.id(), game.white(), game.black(), game.resignedBy(), moves);
        }
    }

    public List<OnlineGame> loadAll() {
        return jdbc.query("SELECT id, white, black, resigned_by, moves FROM online_games", (row, number) -> {
            String moves = row.getString(5);
            return new OnlineGame(row.getString(1), row.getString(2), row.getString(3), row.getString(4),
                    moves.isEmpty() ? List.of() : Arrays.asList(moves.split(" ")));
        });
    }
}
