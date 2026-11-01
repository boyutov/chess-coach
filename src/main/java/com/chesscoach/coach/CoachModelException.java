package com.chesscoach.coach;

/** Ошибка модели. Сообщение понятно ученику и показывается в чате. */
public class CoachModelException extends RuntimeException {

    public static final String REFUSED = "Тренер не смог ответить на этот вопрос. Попробуйте переформулировать его.";
    public static final String NO_CONNECTION = "Нет связи с сервисом ИИ. Проверьте интернет и попробуйте ещё раз.";

    public CoachModelException(String message) {
        super(message);
    }

    public static CoachModelException serviceError(int statusCode) {
        return new CoachModelException("Сервис ИИ вернул ошибку " + statusCode + ". Попробуйте ещё раз.");
    }

    public static CoachModelException badKey(String variable) {
        return new CoachModelException("API-ключ не подошёл. Проверьте " + variable + " и перезапустите приложение.");
    }
}
