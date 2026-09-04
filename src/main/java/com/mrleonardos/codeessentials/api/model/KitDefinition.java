package com.mrleonardos.codeessentials.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import com.mrleonardos.codeessentials.api.EssentialsLimits;

/**
 * Кит: раскладка предметов по слотам и два ключа ограничения.
 *
 * <p>
 * Слоты нумеруются как в инвентаре игрока: 0..8 хотбар, 9..35 рюкзак, 36..39 броня. Карта разрежена:
 * пустой слот в ките отсутствует, а не лежит пустышкой. Порядок обхода карты это порядок доставки
 * предметов нового получения.
 *
 * <p>
 * {@code once} и {@code cooldownSeconds} независимы: одноразовый кит с паузой, одноразовый без,
 * повторяемый с паузой и без ограничений вовсе это четыре разных поведения. Ноль у паузы значит «без
 * кулдауна» по общей конвенции линейки.
 */
public final class KitDefinition {

    /** Слотов инвентаря в ките, хотбар и рюкзак вместе. */
    public static final int INVENTORY_SLOTS = 36;

    /** Слотов брони в ките. */
    public static final int ARMOR_SLOTS = 4;

    /** Слотов всего. */
    public static final int SLOTS = INVENTORY_SLOTS + ARMOR_SLOTS;

    /** Первый слот брони, сапоги. */
    public static final int ARMOR_BOOTS = INVENTORY_SLOTS;

    /** Второй слот брони, штаны. */
    public static final int ARMOR_LEGGINGS = INVENTORY_SLOTS + 1;

    /** Третий слот брони, нагрудник. */
    public static final int ARMOR_CHESTPLATE = INVENTORY_SLOTS + 2;

    /** Четвёртый слот брони, шлем. */
    public static final int ARMOR_HELMET = INVENTORY_SLOTS + 3;

    private final String name;
    private final boolean once;
    private final int cooldownSeconds;
    private final Map<Integer, KitItem> slots;

    private KitDefinition(String name, boolean once, int cooldownSeconds, Map<Integer, KitItem> slots) {
        this.name = name;
        this.once = once;
        this.cooldownSeconds = cooldownSeconds;
        this.slots = slots;
    }

    /**
     * Собрать кит.
     *
     * @param name            имя в нижнем регистре по шаблону {@link EssentialsLimits#NAME_PATTERN}
     * @param once            одноразовый навсегда
     * @param cooldownSeconds пауза между получениями в секундах, ноль значит «без кулдауна»
     * @param slots           предметы по номерам слотов 0..39, пустая карта означает пустой кит
     * @throws IllegalArgumentException если имя, номер слота или предмет не проходят проверку
     */
    public static KitDefinition of(String name, boolean once, int cooldownSeconds, Map<Integer, KitItem> slots) {
        Objects.requireNonNull(slots, "slots");
        if (cooldownSeconds < 0) {
            throw new IllegalArgumentException("Kit cooldown must not be negative: " + cooldownSeconds);
        }
        Map<Integer, KitItem> sorted = new TreeMap<>();
        for (Map.Entry<Integer, KitItem> entry : slots.entrySet()) {
            int slot = entry.getKey() == null ? -1
                : entry.getKey()
                    .intValue();
            if (slot < 0 || slot >= SLOTS) {
                throw new IllegalArgumentException("Kit slot must be within 0.." + (SLOTS - 1) + ": " + slot);
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("Kit slot " + slot + " carries no item");
            }
            sorted.put(Integer.valueOf(slot), entry.getValue());
        }
        return new KitDefinition(
            EssentialsLimits.checkedName(name, "kit name"),
            once,
            cooldownSeconds,
            Collections.unmodifiableMap(sorted));
    }

    /** Начать собирать кит по имени. */
    public static Builder named(String name) {
        return new Builder(name);
    }

    /** Имя в нижнем регистре, оно же ключ в файле и хвост ноды права. */
    public String name() {
        return name;
    }

    /** Одноразовый навсегда. */
    public boolean once() {
        return once;
    }

    /** Пауза между получениями в секундах, ноль значит «без кулдауна». */
    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    /** Предметы по номерам слотов, порядок возрастания номеров. */
    public Map<Integer, KitItem> slots() {
        return slots;
    }

    /** Предмет в слоте или пустой ответ. */
    public Optional<KitItem> slot(int index) {
        return Optional.ofNullable(slots.get(Integer.valueOf(index)));
    }

    /** Сколько слотов занято. */
    public int size() {
        return slots.size();
    }

    /** Пустой кит: не был записан и не будет, {@code define} такой отклоняет. */
    public boolean isEmpty() {
        return slots.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof KitDefinition)) {
            return false;
        }
        KitDefinition that = (KitDefinition) other;
        return once == that.once && cooldownSeconds == that.cooldownSeconds
            && name.equals(that.name)
            && slots.equals(that.slots);
    }

    @Override
    public int hashCode() {
        return ((name.hashCode() * 31 + Boolean.hashCode(once)) * 31 + cooldownSeconds) * 31 + slots.hashCode();
    }

    @Override
    public String toString() {
        return name + ": " + slots.size() + " slot(s), once " + once + ", cooldown " + cooldownSeconds + "s";
    }

    /** Сборщик кита: имя обязано, остальное заводское. */
    public static final class Builder {

        private final String name;
        private final Map<Integer, KitItem> slots = new LinkedHashMap<>();
        private boolean once;
        private int cooldownSeconds;

        private Builder(String name) {
            this.name = name;
        }

        /** Одноразовый навсегда. */
        public Builder once() {
            this.once = true;
            return this;
        }

        /** Одноразовый навсегда, явным значением. */
        public Builder once(boolean value) {
            this.once = value;
            return this;
        }

        /** Пауза между получениями в секундах. */
        public Builder cooldown(int seconds) {
            this.cooldownSeconds = seconds;
            return this;
        }

        /** Положить предмет в слот. Повтор по номеру оставляет последний. */
        public Builder slot(int index, KitItem item) {
            Objects.requireNonNull(item, "item");
            slots.put(Integer.valueOf(index), item);
            return this;
        }

        /** Положить предмет, разобрав строку записи. Пустой ответ бросает исключение. */
        public Builder slot(int index, String item) {
            return slot(
                index,
                KitItem.parse(item)
                    .orElseThrow(() -> new IllegalArgumentException("Item record is unreadable: " + item)));
        }

        /** Готовый кит. */
        public KitDefinition build() {
            return of(name, once, cooldownSeconds, slots);
        }
    }
}
