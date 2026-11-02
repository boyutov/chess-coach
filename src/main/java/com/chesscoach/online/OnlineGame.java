package com.chesscoach.online;

import com.chesscoach.chess.Board;
import com.chesscoach.chess.Move;
import com.chesscoach.engine.Level;
import com.chesscoach.game.Game;
import com.chesscoach.game.GameStatus;
import com.chesscoach.game.MoveRecord;

import java.util.List;
import java.util.UUID;

/**
 * Партия двух людей. Правила и историю ведёт обычный {@link Game}; он не попадает в хранилище партий с ботом,
 * поэтому подсказки, отмена и тренер к нему недоступны. Игроки заданы ключами.
 */
public class OnlineGame {

    private final Game game;
    private final String white;
    private final String black;
    private String resignedBy;

    public OnlineGame(String white, String black) {
        this(UUID.randomUUID().toString(), white, black);
    }

    /** Партия из базы: ходы проигрываются заново, чтобы восстановить все позиции. */
    public OnlineGame(String id, String white, String black, String resignedBy, List<String> moves) {
        this(id, white, black);
        moves.forEach(uci -> play(Move.parseUci(uci)));
        this.resignedBy = resignedBy;
    }

    private OnlineGame(String id, String white, String black) {
        this.game = new Game(id, Board.start(), true, Level.BEGINNER, null);
        this.white = white;
        this.black = black;
    }

    public void play(Move move) {
        Board board = game.board();
        game.addMove(new MoveRecord(board, board.apply(move), move, board.toSan(move),
                board.isWhiteToMove(), List.of(), null));
    }

    public String id() {
        return game.id();
    }

    public Game game() {
        return game;
    }

    public String white() {
        return white;
    }

    public String black() {
        return black;
    }

    public String resignedBy() {
        return resignedBy;
    }

    public void resign(String player) {
        resignedBy = player;
    }

    public boolean isActive() {
        return resignedBy == null && game.status() == GameStatus.ACTIVE;
    }

    public boolean hasPlayer(String player) {
        return white.equals(player) || black.equals(player);
    }

    public String opponentOf(String player) {
        return white.equals(player) ? black : white;
    }
}
