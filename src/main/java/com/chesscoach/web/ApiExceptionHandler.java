package com.chesscoach.web;

import com.chesscoach.game.GameNotFoundException;
import com.chesscoach.game.GameStateException;
import com.chesscoach.game.IllegalMoveException;
import com.chesscoach.online.AccountService;
import com.chesscoach.online.NotAuthenticatedException;
import com.chesscoach.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Переводит ошибки партии в HTTP-ответы с понятным сообщением. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(GameNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(GameNotFoundException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(GameStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse conflict(GameStateException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler({NotAuthenticatedException.class, AccountService.WrongCredentials.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse unauthorized(RuntimeException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(ChatLimiter.LimitExceeded.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ErrorResponse tooMany(ChatLimiter.LimitExceeded e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(LocalOnlyException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse forbidden(LocalOnlyException e) {
        return new ErrorResponse(e.getMessage());
    }

    /** IllegalArgumentException бросает разбор хода в формате UCI. */
    @ExceptionHandler({IllegalMoveException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse badRequest(RuntimeException e) {
        return new ErrorResponse(e.getMessage());
    }
}
