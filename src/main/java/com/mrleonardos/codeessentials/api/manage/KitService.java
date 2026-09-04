package com.mrleonardos.codeessentials.api.manage;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.model.KitDefinition;
import com.mrleonardos.codeessentials.api.store.StoreResult;

/**
 * Киты сервера: раскладки предметов, их файл и личный буфер игроков.
 *
 * <p>
 * Право на получение здесь не спрашивается: это дело ноды {@code codeessentials.kit.<имя>} и команды.
 * Сервис отвечает за хранение, ограничения и доставку, поэтому чужой мод, зовущий {@link #claim},
 * получает кит целиком, вместе с отметкой одноразовости и кулдауном.
 *
 * <p>
 * {@link #define} и {@link #delete} правят файл настроек, который админ правит и руками. Доставка
 * идёт в главном потоке через планировщик ядра: зовущий из другого потока ждёт ответа не дольше
 * десяти секунд.
 */
public interface KitService {

    /** Все киты по именам, порядок алфавитный. */
    Map<String, KitDefinition> kits();

    /** Кит по имени. */
    Optional<KitDefinition> kit(String name);

    /**
     * Записать кит целиком: так работают снимок инвентаря и закрытие редактора.
     *
     * @return отказ {@code INVALID_VALUE}, если в ките ни одного предмета: пустой кит записью не
     *         выходит, удаление это отдельная команда
     */
    StoreResult define(KitDefinition kit, String actor);

    /**
     * Убрать кит. Буфер игроков при этом остаётся: вернувшийся под тем же именем кит доносит долг.
     *
     * @return отказ {@code NOT_FOUND}, если кита с таким именем нет
     */
    StoreResult delete(String name, String actor);

    /** Сколько предметов игрока ждёт в буферах всех китов. */
    int pendingItems(UUID player);

    /**
     * Ждущие предметы по китам: имя кита в число предметов, порядок алфавитный, пустых китов нет.
     */
    Map<String, Integer> pendingByKit(UUID player);

    /** Взят ли игроком одноразовый кит. Отметка живёт в состоянии игрока и не снимается. */
    boolean taken(UUID player, String kitName);

    /** Остаток паузы кита в миллисекундах, ноль значит «можно брать». */
    long cooldownLeft(UUID player, String kitName);

    /**
     * Выдать кит: доставить долг, спросить ограничения и вето, выдать новое получение.
     *
     * @param player кому выдаётся
     * @param kit    имя кита в нижнем регистре
     * @param actor  кто выдаёт, для аудита
     */
    Claim claim(UUID player, String kit, String actor);

    /** Ответ на выдачу: что случилось и в каких числах. */
    final class Claim {

        /** Чем кончилась выдача. */
        public enum Outcome {

            /** Новое получение выдано, часть могла уехать в буфер. */
            DELIVERED,

            /** Новое получение целиком легло в буфер: инвентаря нет или он полон. Получение состоялось. */
            STASHED,

            /** Пауза ещё не прошла, новое получение не выдано, долг при этом доезжает. */
            COOLDOWN,

            /** Одноразовый кит уже был взят, новое получение не выдано, долг доезжает. */
            TAKEN,

            /** Слушатель ветит кит, отметок и кулдауна нет, долг доезжает. */
            VETOED,

            /** Кита с таким именем нет. */
            UNKNOWN
        }

        private static final Claim UNKNOWN = new Claim(Outcome.UNKNOWN, 0, 0, 0, 0L, null);

        private final Outcome outcome;
        private final int delivered;
        private final int buffered;
        private final int pending;
        private final long waitMillis;
        private final String reason;

        private Claim(Outcome outcome, int delivered, int buffered, int pending, long waitMillis, String reason) {
            this.outcome = outcome;
            this.delivered = delivered;
            this.buffered = buffered;
            this.pending = pending;
            this.waitMillis = waitMillis;
            this.reason = reason;
        }

        /** Кита с таким именем нет. */
        public static Claim unknown() {
            return UNKNOWN;
        }

        /** Кит выдан: сколько доехало, сколько легло в буфер, сколько осталось ждать там. */
        public static Claim granted(Outcome outcome, int delivered, int buffered, int pending) {
            return new Claim(outcome, delivered, buffered, pending, 0L, null);
        }

        /** Отказ по паузе или одноразовости: долг при этом мог доехать. */
        public static Claim refused(Outcome outcome, int delivered, int pending, long waitMillis) {
            return new Claim(outcome, delivered, 0, pending, waitMillis, null);
        }

        /** Вето слушателя с его причиной. */
        public static Claim vetoed(String reason, int delivered, int pending) {
            Objects.requireNonNull(reason, "reason");
            return new Claim(Outcome.VETOED, delivered, 0, pending, 0L, reason);
        }

        /** Что случилось. */
        public Outcome outcome() {
            return outcome;
        }

        /** Сколько предметов доехало до инвентаря в этом вызове, из долга и из получения вместе. */
        public int delivered() {
            return delivered;
        }

        /** Сколько предметов нового получения легло в буфер. */
        public int buffered() {
            return buffered;
        }

        /** Сколько предметов осталось ждать в буфере после этого вызова. */
        public int pending() {
            return pending;
        }

        /** Остаток паузы в миллисекундах, для {@link Outcome#COOLDOWN}. */
        public long waitMillis() {
            return waitMillis;
        }

        /** Причина вето или пустой ответ. */
        public Optional<String> reason() {
            return Optional.ofNullable(reason);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Claim)) {
                return false;
            }
            Claim that = (Claim) other;
            return outcome == that.outcome && delivered == that.delivered
                && buffered == that.buffered
                && pending == that.pending
                && waitMillis == that.waitMillis
                && Objects.equals(reason, that.reason);
        }

        @Override
        public int hashCode() {
            return (((outcome.hashCode() * 31 + delivered) * 31 + buffered) * 31 + pending) * 31
                + (Long.hashCode(waitMillis) * 31 + Objects.hashCode(reason));
        }

        @Override
        public String toString() {
            return outcome + " delivered "
                + delivered
                + ", buffered "
                + buffered
                + ", pending "
                + pending
                + (reason == null ? "" : ", " + reason);
        }
    }
}
