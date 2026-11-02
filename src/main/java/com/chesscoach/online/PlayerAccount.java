package com.chesscoach.online;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Учётная запись игрока.
 *
 * @param friends  ключи друзей
 * @param incoming ключи игроков, приславших заявку в друзья
 */
public record PlayerAccount(String nickname, String salt, String hash, Set<String> friends, Set<String> incoming) {

    /** Наборы меняет сервис игры, пока хранилище может сохранять файл, поэтому они потокобезопасные. */
    public PlayerAccount {
        Set<String> safeFriends = ConcurrentHashMap.newKeySet();
        safeFriends.addAll(friends);
        friends = safeFriends;
        Set<String> safeIncoming = ConcurrentHashMap.newKeySet();
        safeIncoming.addAll(incoming);
        incoming = safeIncoming;
    }
}
