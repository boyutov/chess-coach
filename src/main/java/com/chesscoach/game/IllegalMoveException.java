package com.chesscoach.game;

public class IllegalMoveException extends RuntimeException {

    public IllegalMoveException(String uci) {
        super("Нелегальный ход: " + uci);
    }
}
