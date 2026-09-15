package com.mrleonardos.codeessentials.internal.kits;

import java.util.Objects;

import com.mrleonardos.codeessentials.api.model.KitItem;

/**
 * Слепок слотов игрока на момент выдачи: записываемые предметы и признак занятости тем, что запись
 * кита выразить не может.
 *
 * <p>
 * Предмет с NBT длиннее предела, уроном вне поля или именем вне реестра не превращается в пустое
 * место: иначе выдача стёрла бы его из инвентаря. Такой слот помечается невыразимым, доставка
 * считает его занятым и несёт предмет кита по этапам переполнения, а обратная запись оставляет
 * живой стек на месте.
 */
public final class WornSlots {

    private final KitItem[] items;
    private final boolean[] unrecorded;

    private WornSlots(KitItem[] items, boolean[] unrecorded) {
        this.items = items;
        this.unrecorded = unrecorded;
    }

    /** Собрать слепок. Массивы копируются, невыразимый слот обязан быть пустым в записи. */
    public static WornSlots of(KitItem[] items, boolean[] unrecorded) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(unrecorded, "unrecorded");
        if (items.length != unrecorded.length) {
            throw new IllegalArgumentException(
                "Item and mark arrays must be the same length: " + items.length + " and " + unrecorded.length);
        }
        KitItem[] held = new KitItem[items.length];
        boolean[] marks = new boolean[unrecorded.length];
        for (int slot = 0; slot < items.length; slot++) {
            if (unrecorded[slot]) {
                if (items[slot] != null) {
                    throw new IllegalArgumentException("Unrecordable slot " + slot + " must hold no item record");
                }
                marks[slot] = true;
                continue;
            }
            held[slot] = items[slot];
        }
        return new WornSlots(held, marks);
    }

    /** Слепок без невыразимых слотов. */
    public static WornSlots of(KitItem[] items) {
        return of(items, new boolean[items.length]);
    }

    /** Пустой слепок на заданное число слотов. */
    public static WornSlots empty(int slots) {
        return of(new KitItem[slots]);
    }

    /** Записываемый предмет слота или пустая ссылка. */
    public KitItem item(int slot) {
        return items[slot];
    }

    /** Занят ли слот предметом, который запись кита выразить не может. */
    public boolean unrecorded(int slot) {
        return unrecorded[slot];
    }

    /** Число слотов. */
    public int size() {
        return items.length;
    }
}
