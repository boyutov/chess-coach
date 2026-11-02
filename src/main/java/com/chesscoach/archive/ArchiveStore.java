package com.chesscoach.archive;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ArchiveStore {

    private static final String COLUMNS = "id, owner_white, owner_black, white_name, black_name, level, status, "
            + "resigned_by, moves, plies, analysis, saved_at";
    private static final RowMapper<ArchivedGame> MAPPER = (row, number) -> new ArchivedGame(
            row.getString(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5), row.getString(6),
            row.getString(7), row.getString(8), row.getString(9), row.getInt(10), row.getString(11), row.getLong(12));

    private final JdbcTemplate jdbc;

    public ArchiveStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(ArchivedGame game) {
        int updated = jdbc.update("UPDATE archived_games SET status = ?, resigned_by = ?, moves = ?, plies = ?, "
                        + "analysis = ?, saved_at = ? WHERE id = ?",
                game.status(), game.resignedBy(), game.moves(), game.plies(), game.analysis(), game.savedAt(),
                game.id());
        if (updated == 0) {
            jdbc.update("INSERT INTO archived_games (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    game.id(), game.ownerWhite(), game.ownerBlack(), game.whiteName(), game.blackName(), game.level(),
                    game.status(), game.resignedBy(), game.moves(), game.plies(), game.analysis(), game.savedAt());
        }
    }

    public void saveAnalysis(String id, String analysis) {
        jdbc.update("UPDATE archived_games SET analysis = ? WHERE id = ?", analysis, id);
    }

    public void delete(String id) {
        jdbc.update("DELETE FROM archived_games WHERE id = ?", id);
    }

    public boolean exists(String id) {
        return !jdbc.queryForList("SELECT id FROM archived_games WHERE id = ?", String.class, id).isEmpty();
    }

    public Optional<ArchivedGame> find(String id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM archived_games WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<ArchivedGame> listFor(String player) {
        return jdbc.query("SELECT " + COLUMNS + " FROM archived_games WHERE owner_white = ? OR owner_black = ? "
                + "ORDER BY saved_at DESC", MAPPER, player, player);
    }
}
