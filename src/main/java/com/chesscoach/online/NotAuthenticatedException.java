package com.chesscoach.online;

public class NotAuthenticatedException extends RuntimeException {

    public NotAuthenticatedException() {
        super("Войдите в аккаунт");
    }
}
