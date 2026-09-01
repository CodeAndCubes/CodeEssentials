package com.mrleonardos.codeessentials.api.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.model.PlayerRecord;

/**
 * Пачка правок состояния игроков для хранилища.
 *
 * <p>
 * Носит автора и список правок целых записей. Хранилище применяет пачку один раз, поэтому
 * частичного применения не бывает: либо вся пачка, либо отказ. По пачке же строится аудит.
 *
 * <p>
 * Кулдауны правятся здесь же, отдельными правками: файл у них свой, но писатель один, и мешать два
 * пути записи ради этого незачем.
 */
public final class ChangeBatch {

    /** Что делает правка. */
    public enum Kind {

        /** Записать игрока целиком. */
        UPSERT_PLAYER,

        /** Убрать игрока со всеми домами и стеком возврата. */
        REMOVE_PLAYER,

        /** Записать метку окончания кулдауна. */
        SET_COOLDOWN,

        /** Снять все кулдауны игрока. */
        CLEAR_COOLDOWNS
    }

    /** Одна правка. */
    public static final class Change {

        private final Kind kind;
        private final UUID player;
        private final PlayerRecord record;
        private final String cooldownKey;
        private final long expiresAt;

        private Change(Kind kind, UUID player, PlayerRecord record, String cooldownKey, long expiresAt) {
            this.kind = kind;
            this.player = player;
            this.record = record;
            this.cooldownKey = cooldownKey;
            this.expiresAt = expiresAt;
        }

        /** Записать игрока целиком. */
        public static Change upsertPlayer(PlayerRecord record) {
            Objects.requireNonNull(record, "record");
            return new Change(Kind.UPSERT_PLAYER, record.uuid(), record, null, 0L);
        }

        /** Убрать игрока. */
        public static Change removePlayer(UUID player) {
            Objects.requireNonNull(player, "player");
            return new Change(Kind.REMOVE_PLAYER, player, null, null, 0L);
        }

        /**
         * Записать метку окончания кулдауна.
         *
         * @param cooldownKey ключ причины или {@code request} для срока между tpa-запросами
         * @param expiresAt   абсолютная метка в epoch millis
         * @throws IllegalArgumentException если метка отрицательная
         */
        public static Change setCooldown(UUID player, String cooldownKey, long expiresAt) {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(cooldownKey, "cooldownKey");
            if (expiresAt < 0L) {
                throw new IllegalArgumentException("Cooldown deadline must not be negative: " + expiresAt);
            }
            return new Change(Kind.SET_COOLDOWN, player, null, cooldownKey, expiresAt);
        }

        /** Снять все кулдауны игрока. */
        public static Change clearCooldowns(UUID player) {
            Objects.requireNonNull(player, "player");
            return new Change(Kind.CLEAR_COOLDOWNS, player, null, null, 0L);
        }

        /** Что делает правка. */
        public Kind kind() {
            return kind;
        }

        /** Кого правка касается. */
        public UUID player() {
            return player;
        }

        /** Новая запись игрока или пустой ответ. */
        public Optional<PlayerRecord> record() {
            return Optional.ofNullable(record);
        }

        /** Ключ кулдауна или пустой ответ. */
        public Optional<String> cooldownKey() {
            return Optional.ofNullable(cooldownKey);
        }

        /** Метка окончания кулдауна в epoch millis. */
        public long expiresAt() {
            return expiresAt;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Change)) {
                return false;
            }
            Change that = (Change) other;
            return kind == that.kind && expiresAt == that.expiresAt
                && player.equals(that.player)
                && Objects.equals(record, that.record)
                && Objects.equals(cooldownKey, that.cooldownKey);
        }

        @Override
        public int hashCode() {
            return (((kind.hashCode() * 31 + player.hashCode()) * 31 + Objects.hashCode(record)) * 31
                + Objects.hashCode(cooldownKey)) * 31 + Long.hashCode(expiresAt);
        }

        @Override
        public String toString() {
            return kind + " " + player + (cooldownKey == null ? "" : " " + cooldownKey);
        }
    }

    private final String author;
    private final List<Change> changes;

    private ChangeBatch(String author, List<Change> changes) {
        this.author = author;
        this.changes = changes;
    }

    /**
     * Начать собирать пачку.
     *
     * @param author кто правит: имя игрока, {@code console} или идентификатор чужого мода
     */
    public static Builder builder(String author) {
        return new Builder(author);
    }

    /** Кто правил. */
    public String author() {
        return author;
    }

    /** Правки в порядке применения. */
    public List<Change> changes() {
        return changes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChangeBatch)) {
            return false;
        }
        ChangeBatch that = (ChangeBatch) other;
        return author.equals(that.author) && changes.equals(that.changes);
    }

    @Override
    public int hashCode() {
        return author.hashCode() * 31 + changes.hashCode();
    }

    @Override
    public String toString() {
        return author + ": " + changes.size() + " change(s)";
    }

    /** Сборщик пачки правок. */
    public static final class Builder {

        private final String author;
        private final List<Change> changes = new ArrayList<>();

        private Builder(String author) {
            this.author = Objects.requireNonNull(author, "author");
        }

        /** Записать игрока целиком. */
        public Builder upsert(PlayerRecord record) {
            changes.add(Change.upsertPlayer(record));
            return this;
        }

        /** Убрать игрока. */
        public Builder removePlayer(UUID player) {
            changes.add(Change.removePlayer(player));
            return this;
        }

        /** Записать метку окончания кулдауна. */
        public Builder setCooldown(UUID player, String cooldownKey, long expiresAt) {
            changes.add(Change.setCooldown(player, cooldownKey, expiresAt));
            return this;
        }

        /** Снять все кулдауны игрока. */
        public Builder clearCooldowns(UUID player) {
            changes.add(Change.clearCooldowns(player));
            return this;
        }

        /**
         * Готовая пачка.
         *
         * @throws IllegalArgumentException если в пачке ни одной правки
         */
        public ChangeBatch build() {
            if (changes.isEmpty()) {
                throw new IllegalArgumentException("Change batch needs at least one change");
            }
            return new ChangeBatch(author, Collections.unmodifiableList(new ArrayList<>(changes)));
        }
    }
}
