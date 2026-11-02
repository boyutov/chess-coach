package com.chesscoach.online;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Регистрация и вход. В базе лежат только хеши паролей и токенов сессий. */
@Service
public class AccountService {

    private static final String NICKNAME_PATTERN = "[\\p{L}\\p{N}_-]{3,20}";
    private static final int MIN_PASSWORD = 6;
    private static final int MAX_PASSWORD = 200;
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final long SESSION_AGE_MS = Duration.ofDays(30).toMillis();
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MS = Duration.ofMinutes(15).toMillis();

    private record Failures(int count, long lastAt) {
    }

    private final PlayerStore players;
    private final JdbcTemplate jdbc;
    private final byte[] inviteCode;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    public AccountService(PlayerStore players, JdbcTemplate jdbc, @Value("${online.invite-code}") String inviteCode) {
        this.players = players;
        this.jdbc = jdbc;
        this.inviteCode = inviteCode.getBytes(StandardCharsets.UTF_8);
        jdbc.update("DELETE FROM sessions WHERE created_at < ?", System.currentTimeMillis() - SESSION_AGE_MS);
    }

    /** @return токен новой сессии */
    public String register(String nickname, String password, String invite) {
        // Код проверяется первым: хеширование пароля дорогое, а посторонние не должны его запускать.
        byte[] given = (invite == null ? "" : invite.trim()).getBytes(StandardCharsets.UTF_8);
        if (inviteCode.length > 0 && !MessageDigest.isEqual(inviteCode, given)) {
            throw new IllegalArgumentException("Неверный код приглашения");
        }
        String name = nickname == null ? "" : nickname.trim();
        if (!name.matches(NICKNAME_PATTERN)) {
            throw new IllegalArgumentException("Ник: от 3 до 20 букв, цифр, «_» или «-»");
        }
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw new IllegalArgumentException("Пароль: от 6 до 200 символов");
        }
        byte[] salt = randomBytes(16);
        String hash = encode(hash(password, salt));
        synchronized (players) {
            if (players.find(name).isPresent()) {
                throw new IllegalArgumentException("Этот ник уже занят");
            }
            players.put(new PlayerAccount(name, encode(salt), hash, new HashSet<>(), new HashSet<>()));
        }
        return openSession(name);
    }

    /** @return токен новой сессии */
    public String login(String nickname, String password) {
        if (nickname == null || password == null || password.length() > MAX_PASSWORD) {
            throw new NotAuthenticatedException();
        }
        String key = PlayerStore.key(nickname);
        requireNotLocked(key);
        PlayerAccount player = players.find(nickname).orElseThrow(WrongCredentials::new);
        byte[] expected = Base64.getDecoder().decode(player.hash());
        byte[] actual = hash(password, Base64.getDecoder().decode(player.salt()));
        if (!MessageDigest.isEqual(expected, actual)) {
            failures.merge(key, new Failures(1, System.currentTimeMillis()),
                    (old, next) -> new Failures(old.count() + 1, next.lastAt()));
            throw new WrongCredentials();
        }
        failures.remove(key);
        return openSession(player.nickname());
    }

    private void requireNotLocked(String key) {
        Failures seen = failures.get(key);
        if (seen == null) return;
        if (System.currentTimeMillis() - seen.lastAt() > LOCK_MS) {
            failures.remove(key);
        } else if (seen.count() >= MAX_FAILURES) {
            throw new WrongCredentials("Слишком много попыток. Попробуйте через 15 минут");
        }
    }

    public void logout(String token) {
        if (token != null) jdbc.update("DELETE FROM sessions WHERE token_hash = ?", tokenHash(token));
    }

    /** @return ключ игрока этой сессии */
    public String requirePlayer(String token) {
        if (token == null) throw new NotAuthenticatedException();
        List<String> found = jdbc.queryForList("SELECT player FROM sessions WHERE token_hash = ? AND created_at >= ?",
                String.class, tokenHash(token), System.currentTimeMillis() - SESSION_AGE_MS);
        if (found.isEmpty()) throw new NotAuthenticatedException();
        return found.get(0);
    }

    private String openSession(String nickname) {
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
        jdbc.update("INSERT INTO sessions (token_hash, player, created_at) VALUES (?, ?, ?)",
                tokenHash(token), PlayerStore.key(nickname), System.currentTimeMillis());
        return token;
    }

    private static String tokenHash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        random.nextBytes(bytes);
        return bytes;
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] hash(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Одно сообщение на неверный ник и неверный пароль. */
    public static final class WrongCredentials extends RuntimeException {
        WrongCredentials() {
            this("Неверный ник или пароль");
        }

        WrongCredentials(String message) {
            super(message);
        }
    }
}
