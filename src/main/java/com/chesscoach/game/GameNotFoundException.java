package com.chesscoach.game;

public class GameNotFoundException extends RuntimeException {

    public GameNotFoundException() {
        super("Партия не найдена");
    }
}
