package com.mrleonardos.codeessentials.internal.kits;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.mrleonardos.codeessentials.api.model.KitDefinition;
import com.mrleonardos.codeessentials.api.model.KitItem;

public final class KitDelivery {

    private final KitItem[] slots;
    private final List<KitItem> pending;
    private final int delivered;
    private final int buffered;

    private KitDelivery(KitItem[] slots, List<KitItem> pending, int delivered, int buffered) {
        this.slots = slots;
        this.pending = pending;
        this.delivered = delivered;
        this.buffered = buffered;
    }

    /**
     * Разложить кит по слотам игрока.
     *
     * <p>
     * Сначала предметы кита по номерам слотов, затем долг в порядке хранения. Каждый предмет идёт по
     * этапам: свой слот, слияние со стопками, первый свободный слот инвентаря, остаток в буфер.
     * Предмет из ячейки брони этапы после первого минует: не наделся, значит ждёт в буфере.
     *
     * @param kit      что выдаётся
     * @param debt     ждущие предметы этого кита из прошлого раза, допускается пустой список
     * @param worn     слоты игрока, пустые места пустыми ссылками
     * @param stacking правила стопок и надеваемости
     */
    public static KitDelivery deliver(KitDefinition kit, List<KitItem> debt, KitItem[] worn, KitStacking stacking) {
        Objects.requireNonNull(kit, "kit");
        Objects.requireNonNull(debt, "debt");
        Objects.requireNonNull(worn, "worn");
        Objects.requireNonNull(stacking, "stacking");
        if (worn.length != KitDefinition.SLOTS) {
            throw new IllegalArgumentException("Player slots must be " + KitDefinition.SLOTS + ": " + worn.length);
        }
        KitItem[] slots = new KitItem[KitDefinition.SLOTS];
        for (int slot = 0; slot < KitDefinition.SLOTS; slot++) {
            slots[slot] = worn[slot] == null ? null : worn[slot].withCount(worn[slot].count());
        }
        List<KitItem> pending = new ArrayList<>();
        int arrived = 0;
        int stashed = 0;
        for (int slot = 0; slot < KitDefinition.SLOTS; slot++) {
            KitItem item = kit.slot(slot)
                .orElse(null);
            if (item == null) {
                continue;
            }
            int moved = place(item, slot, slots, stacking, pending);
            arrived += moved;
            stashed += item.count() - moved;
        }
        for (KitItem item : debt) {
            arrived += place(item, -1, slots, stacking, pending);
        }
        return new KitDelivery(slots, pending, arrived, stashed);
    }

    /**
     * Отложить предмет в буфер, слив с уже ждущим того же вида: буфер без этого подрос бы вдвое на
     * втором же получении.
     */
    public static void setAside(List<KitItem> pending, KitItem item) {
        for (int index = 0; index < pending.size(); index++) {
            KitItem held = pending.get(index);
            if (held.sameKind(item)) {
                pending.set(index, held.withCount(held.count() + item.count()));
                return;
            }
        }
        pending.add(item);
    }

    /** Слоты игрока после доставки: 40 мест, пустые места пустыми ссылками. */
    public KitItem[] slots() {
        return slots;
    }

    /** Что осталось ждать в буфере, по порядку. */
    public List<KitItem> pending() {
        return pending;
    }

    /** Сколько предметов доехало до инвентаря, из кита и из долга вместе. */
    public int delivered() {
        return delivered;
    }

    /** Сколько предметов кита легло в буфер, не доехав до инвентаря. */
    public int buffered() {
        return buffered;
    }

    private static int place(KitItem item, int ownSlot, KitItem[] slots, KitStacking stacking, List<KitItem> pending) {
        int remaining = item.count();
        if (ownSlot >= KitDefinition.INVENTORY_SLOTS) {
            if (remaining > 0 && slots[ownSlot] == null && stacking.accepts(ownSlot, item)) {
                remaining -= put(item, Math.min(remaining, stacking.limit(item)), slots, ownSlot);
            }
            if (remaining > 0) {
                setAside(pending, item.withCount(remaining));
            }
            return item.count() - remaining;
        }
        if (remaining > 0 && ownSlot >= 0 && slots[ownSlot] == null && stacking.accepts(ownSlot, item)) {
            remaining -= put(item, Math.min(remaining, stacking.limit(item)), slots, ownSlot);
        }
        for (int slot = 0; remaining > 0 && slot < KitDefinition.INVENTORY_SLOTS; slot++) {
            KitItem held = slots[slot];
            if (held == null || !stacking.merges(held, item)) {
                continue;
            }
            int room = stacking.limit(held) - held.count();
            if (room <= 0) {
                continue;
            }
            int take = Math.min(room, remaining);
            slots[slot] = held.withCount(held.count() + take);
            remaining -= take;
        }
        for (int slot = 0; remaining > 0 && slot < KitDefinition.INVENTORY_SLOTS; slot++) {
            if (slots[slot] != null || !stacking.accepts(slot, item)) {
                continue;
            }
            remaining -= put(item, remaining, slots, slot);
        }
        if (remaining > 0) {
            setAside(pending, item.withCount(remaining));
        }
        return item.count() - remaining;
    }

    private static int put(KitItem item, int take, KitItem[] slots, int slot) {
        slots[slot] = item.withCount(take);
        return take;
    }
}
