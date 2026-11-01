package com.chesscoach.game;

/** Действие сейчас невозможно: например, партия закончена или очередь соперника. */
public class GameStateException extends RuntimeException {

    public GameStateException(String message) {
        super(message);
    }
}
