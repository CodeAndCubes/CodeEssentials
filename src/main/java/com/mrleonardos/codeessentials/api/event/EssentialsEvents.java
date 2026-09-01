package com.mrleonardos.codeessentials.api.event;

import java.util.Objects;
import java.util.Optional;

/**
 * Реестры слушателей мода.
 *
 * <p>
 * Слушатели вызываются в главном потоке, порядок задаёт число: меньшее значит более ранний вызов.
 * При равном приоритете слушатели идут в порядке регистрации, поэтому поведение не зависит от
 * порядка загрузки модов.
 *
 * <p>
 * Каждый слушатель объявляет свой вид. {@code ENFORCE} решает, случится ли действие, и упавший
 * {@code ENFORCE} отменяет его: проверка безопасности, которая сломалась, не должна молча
 * пропускать. {@code INFORM} ни на что не влияет, его падение уходит в лог, остальные слушатели
 * получают событие дальше.
 */
public interface EssentialsEvents {

    /** Чем слушатель занят. */
    enum Kind {

        /** Решает, случится ли действие. Падение считается отказом. */
        ENFORCE,

        /** Смотрит и записывает. Падение уходит в лог и ни на что не влияет. */
        INFORM
    }

    /**
     * Ответ слушателя: пускает или ветит.
     *
     * <p>
     * Первый отказ останавливает цепочку, действие отменяется, причина попадает в лог и игроку.
     * Отказ это значение, а не исключение: правило «сюда нельзя» штатный ход конвейера.
     */
    final class Decision {

        private static final Decision ALLOW = new Decision(true, null);

        private final boolean allowed;
        private final String reason;

        private Decision(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        /** Действие пускаем. */
        public static Decision allow() {
            return ALLOW;
        }

        /**
         * Действие ветим.
         *
         * @param reason почему, попадает в лог и игроку
         */
        public static Decision deny(String reason) {
            Objects.requireNonNull(reason, "reason");
            return new Decision(false, reason);
        }

        /** Правда ли действие пускаем. */
        public boolean allowed() {
            return allowed;
        }

        /** Причина отказа или пустой ответ. */
        public Optional<String> reason() {
            return Optional.ofNullable(reason);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Decision)) {
                return false;
            }
            Decision that = (Decision) other;
            return allowed == that.allowed && Objects.equals(reason, that.reason);
        }

        @Override
        public int hashCode() {
            return Boolean.hashCode(allowed) * 31 + Objects.hashCode(reason);
        }

        @Override
        public String toString() {
            return allowed ? "allow" : "deny: " + reason;
        }
    }

    /** Слушатели переносов. */
    TeleportEvents teleports();

    /** Слушатели домов. */
    HomeEvents homes();
}
