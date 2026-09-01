package com.mrleonardos.codeessentials.api.store;

import java.util.Objects;
import java.util.Optional;

/**
 * Итог операции: сделано либо отказ с причиной из конечного перечня.
 *
 * <p>
 * Отказ приходит значением, а не исключением, чтобы команда показала его игроку, а чужой мод честно
 * разобрал, что именно не вышло. Отказ всегда означает одно: ничего не изменилось, повтор
 * безопасен.
 */
public final class StoreResult {

    /** Почему операция не прошла. */
    public enum Failure {

        /** Дома, варпа или игрока с таким именем нет. */
        NOT_FOUND,

        /** Запись с таким именем уже есть. */
        ALREADY_EXISTS,

        /** Потолок из {@code EssentialsLimits} или лимит домов исчерпан. */
        LIMIT_REACHED,

        /** Значение не подходит: имя не по шаблону, координата за пределами мира. */
        INVALID_VALUE,

        /** В точке нет безопасного места, а флага {@code --force} не было. */
        UNSAFE_SPOT,

        /** Слушатель с видом {@code ENFORCE} не пустил правку. */
        VETOED,

        /**
         * Правка не дошла до главного потока за отведённое время и снята с очереди: хранилище не
         * тронуто.
         */
        TIMEOUT,

        /**
         * Провайдер не умеет такую правку. Так отвечает чужое хранилище, которое ведёт дома, но не
         * ведёт кулдауны: мод пишет строку в лог и работает дальше.
         */
        UNSUPPORTED,

        /** Носитель не принял запись, модель остаётся на последнем удачном снимке. */
        PROVIDER_FAILED
    }

    private static final StoreResult SUCCESS = new StoreResult(null, null);

    private final Failure failure;
    private final String message;

    private StoreResult(Failure failure, String message) {
        this.failure = failure;
        this.message = message;
    }

    /** Сделано. */
    public static StoreResult success() {
        return SUCCESS;
    }

    /**
     * Сделано.
     *
     * @param message пояснение для человека или журнала
     */
    public static StoreResult success(String message) {
        Objects.requireNonNull(message, "message");
        return new StoreResult(null, message);
    }

    /**
     * Не сделано.
     *
     * @param message пояснение для человека или журнала
     */
    public static StoreResult failure(Failure failure, String message) {
        Objects.requireNonNull(failure, "failure");
        return new StoreResult(failure, message);
    }

    /** Правда ли операция прошла. */
    public boolean successful() {
        return failure == null;
    }

    /** Причина отказа или пустой ответ. */
    public Optional<Failure> failure() {
        return Optional.ofNullable(failure);
    }

    /** Пояснение или пустой ответ. */
    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StoreResult)) {
            return false;
        }
        StoreResult that = (StoreResult) other;
        return failure == that.failure && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(failure) * 31 + Objects.hashCode(message);
    }

    @Override
    public String toString() {
        return successful() ? "success" : failure + ": " + message;
    }
}
