package com.chesscoach.web;

public class LocalOnlyException extends RuntimeException {

    public LocalOnlyException() {
        super("Тренер доступен только на компьютере, где запущен сервер");
    }
}
