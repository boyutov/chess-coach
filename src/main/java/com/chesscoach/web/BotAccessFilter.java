package com.chesscoach.web;

import com.chesscoach.online.AccountService;
import com.chesscoach.online.NotAuthenticatedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** На публичном сервере закрывает партии с ботом и чат от тех, кто не вошёл: они тратят процессор и деньги. */
@Component
public class BotAccessFilter extends OncePerRequestFilter {

    private final AccountService accounts;
    private final boolean requireLogin;

    public BotAccessFilter(AccountService accounts, @Value("${online.require-login}") boolean requireLogin) {
        this.accounts = accounts;
        this.requireLogin = requireLogin;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !requireLogin || !request.getRequestURI().startsWith("/api/games");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie cookie = WebUtils.getCookie(request, OnlineController.COOKIE);
        try {
            accounts.requirePlayer(cookie == null ? null : cookie.getValue());
        } catch (NotAuthenticatedException e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"Войдите в аккаунт\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
