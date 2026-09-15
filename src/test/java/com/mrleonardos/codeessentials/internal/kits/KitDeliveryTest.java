package com.mrleonardos.codeessentials.internal.kits;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.model.KitDefinition;
import com.mrleonardos.codeessentials.api.model.KitItem;

class KitDeliveryTest {

    private static final KitItem BREAD = KitItem.of("minecraft:bread", 16);
    private static final KitItem PICK = KitItem.of("minecraft:iron_pickaxe", 1, 120, "");
    private static final KitItem HELMET = KitItem.of("minecraft:iron_helmet", 1);
    private static final KitItem BOOTS = KitItem.of("minecraft:iron_boots", 1);

    @Test
    void everyItemLandsInItsOwnSlotOnAnEmptyInventory() {
        KitDefinition kit = kit(slot(0, BREAD), slot(1, PICK), slot(39, HELMET));

        KitDelivery report = KitDelivery.deliver(kit, debt(), WornSlots.of(slots()), stacking());

        assertEquals(BREAD, report.slots()[0]);
        assertEquals(PICK, report.slots()[1]);
        assertEquals(HELMET, report.slots()[39]);
        assertEquals(18, report.delivered());
        assertEquals(0, report.buffered());
        assertTrue(
            report.pending()
                .isEmpty());
    }

    @Test
    void aTakenOwnSlotSendsTheItemToTheFirstFreeSlot() {
        KitDefinition kit = kit(slot(5, BREAD));
        KitItem[] worn = slots();
        worn[0] = KitItem.of("minecraft:cobblestone", 1);
        worn[5] = KitItem.of("minecraft:dirt", 1);

        KitDelivery report = KitDelivery.deliver(kit, debt(), WornSlots.of(worn), stacking());

        assertEquals(KitItem.of("minecraft:cobblestone", 1), report.slots()[0], "своя клетка игрока не трогается");
        assertEquals(BREAD, report.slots()[1], "первый свободный слот, а не сосед занятого");
        assertNull(report.slots()[6]);
        assertEquals(16, report.delivered());
    }

    @Test
    void aPartialStackIsToppedUpAndTheRestTakesTheFirstFreeSlot() {
        KitDefinition kit = kit(slot(7, KitItem.of("minecraft:bread", 40)));
        KitItem[] worn = slots();
        worn[7] = KitItem.of("minecraft:bread", 60);

        KitDelivery report = KitDelivery.deliver(kit, debt(), WornSlots.of(worn), stacking());

        assertEquals(KitItem.of("minecraft:bread", 64), report.slots()[7]);
        assertEquals(KitItem.of("minecraft:bread", 36), report.slots()[0]);
        assertNull(report.slots()[8]);
        assertEquals(40, report.delivered());
    }

    @Test
    void theMergeTakesTheRoomItCanEvenWhenTheOwnSlotIsFull() {
        KitDefinition kit = kit(slot(1, KitItem.of("minecraft:bread", 20)));
        KitItem[] worn = slots();
        worn[1] = KitItem.of("minecraft:bread", 60);

        KitDelivery report = KitDelivery.deliver(kit, debt(), WornSlots.of(worn), stacking());

        assertEquals(KitItem.of("minecraft:bread", 64), report.slots()[1]);
        assertEquals(KitItem.of("minecraft:bread", 16), report.slots()[0]);
        assertNull(report.slots()[2]);
        assertEquals(20, report.delivered());
    }

    @Test
    void aRecordBiggerThanAStackIsSpreadOverSeveralStacks() {
        KitDefinition kit = kit(slot(2, KitItem.of("minecraft:bread", 128)));

        KitDelivery report = KitDelivery.deliver(kit, debt(), WornSlots.of(slots()), stacking());

        assertEquals(KitItem.of("minecraft:bread", 64), report.slots()[2]);
        assertEquals(KitItem.of("minecraft:bread", 64), report.slots()[0], "остаток идёт в первый свободный слот");
        assertNull(report.slots()[3]);
        assertEquals(128, report.delivered());
    }

    @Test
    void aRecordThatIsNotAWholeNumberOfStacksKeepsEverySlotWithinTheLimit() {
        KitDefinition kit = kit(slot(2, KitItem.of("minecraft:bread", 200)));

        KitDelivery report = KitDelivery.deliver(kit, debt(), WornSlots.of(slots()), stacking());

        assertEquals(KitItem.of("minecraft:bread", 64), report.slots()[2]);
        assertEquals(KitItem.of("minecraft:bread", 64), report.slots()[0]);
        assertEquals(KitItem.of("minecraft:bread", 64), report.slots()[1]);
        assertEquals(KitItem.of("minecraft:bread", 8), report.slots()[3], "хвост ложится отдельной стопкой");
        assertEquals(200, report.delivered());
    }

    @Test
    void aFullInventoryLeavesEverythingInTheBuffer() {
        KitDefinition kit = kit(slot(0, BREAD), slot(39, HELMET));

        KitDelivery report = KitDelivery
            .deliver(kit, debt(KitItem.of("minecraft:bread", 3)), WornSlots.of(full()), stacking());

        assertArrayEquals(full(), report.slots());
        assertEquals(0, report.delivered());
        assertEquals(17, report.buffered());
        assertEquals(Arrays.asList(KitItem.of("minecraft:bread", 19), HELMET), report.pending());
    }

    @Test
    void bufferLeftoversOfTheSameKindGrowOneEntry() {
        KitDefinition kit = kit(slot(0, KitItem.of("minecraft:bread", 64)));

        KitDelivery report = KitDelivery
            .deliver(kit, debt(KitItem.of("minecraft:bread", 3)), WornSlots.of(full()), stacking());

        assertEquals(Arrays.asList(KitItem.of("minecraft:bread", 67)), report.pending());
    }

    @Test
    void armorGoesOnlyToItsOwnSlotAndOnlyWhenItIsWearable() {
        KitDefinition kit = kit(slot(38, HELMET), slot(39, BOOTS));
        KitItem[] worn = slots();
        worn[0] = KitItem.of("minecraft:cobblestone", 1);

        KitDelivery report = KitDelivery.deliver(kit, debt(), WornSlots.of(worn), stacking());

        assertNull(report.slots()[38], "шлем в клетке нагрудника не надевается");
        assertNull(report.slots()[39], "сапоги в клетке шлема не надеваются");
        assertNull(report.slots()[1], "броня минует свободные слоты инвентаря");
        assertEquals(0, report.delivered());
        assertEquals(Arrays.asList(HELMET, BOOTS), report.pending());
    }

    @Test
    void wearableArmorFillsItsSlotButDoesNotTouchTheBody() {
        KitDefinition kit = kit(slot(36, BOOTS), slot(39, HELMET));
        KitItem[] worn = slots();
        worn[39] = KitItem.of("minecraft:diamond_helmet", 1);

        KitDelivery report = KitDelivery.deliver(kit, debt(), WornSlots.of(worn), stacking());

        assertEquals(BOOTS, report.slots()[36]);
        assertEquals(KitItem.of("minecraft:diamond_helmet", 1), report.slots()[39]);
        assertNull(report.slots()[0], "не надевшаяся броня не идёт в свободный мешок");
        assertEquals(1, report.delivered());
        assertEquals(Arrays.asList(HELMET), report.pending());
    }

    @Test
    void theClaimIsDeliveredBeforeTheDebt() {
        KitDefinition kit = kit(slot(0, BREAD));

        KitDelivery report = KitDelivery
            .deliver(kit, debt(KitItem.of("minecraft:apple", 5)), WornSlots.of(slots()), stacking());

        assertEquals(BREAD, report.slots()[0], "новое получение кладёт свой слот первым");
        assertEquals(KitItem.of("minecraft:apple", 5), report.slots()[1], "долг идёт следом");
        assertEquals(21, report.delivered());
        assertTrue(
            report.pending()
                .isEmpty());
    }

    @Test
    void theDebtArrivesEvenWhenNothingNewIsGranted() {
        KitDelivery report = KitDelivery.deliver(
            KitDefinition.named("starter")
                .build(),
            debt(KitItem.of("minecraft:apple", 5), PICK),
            WornSlots.of(slots()),
            stacking());

        assertEquals(KitItem.of("minecraft:apple", 5), report.slots()[0]);
        assertEquals(PICK, report.slots()[1]);
        assertEquals(6, report.delivered());
        assertTrue(
            report.pending()
                .isEmpty());
    }

    @Test
    void anItemTheRegistryDoesNotKnowNeverReachesTheSlots() {
        KitDefinition kit = kit(slot(0, KitItem.of("mystmod:unknown_thing", 1)));

        KitDelivery report = KitDelivery.deliver(kit, debt(), WornSlots.of(slots()), stacking());

        assertNull(report.slots()[0]);
        assertEquals(0, report.delivered());
        assertEquals(Arrays.asList(KitItem.of("mystmod:unknown_thing", 1)), report.pending());
    }

    @Test
    void anUnrecordableItemKeepsItsSlotAndTheKitItemOverflows() {
        KitItem[] worn = slots();
        boolean[] unrecorded = new boolean[KitDefinition.SLOTS];
        unrecorded[0] = true;
        unrecorded[KitDefinition.ARMOR_HELMET] = true;

        KitDelivery report = KitDelivery.deliver(
            kit(slot(0, BREAD), slot(KitDefinition.ARMOR_HELMET, KitItem.of("minecraft:diamond_helmet", 1))),
            debt(),
            WornSlots.of(worn, unrecorded),
            stacking());

        assertNull(report.slots()[0], "в слот невыразимого предмета ничего не пишется");
        assertNull(report.slots()[KitDefinition.ARMOR_HELMET], "слот брони с невыразимым предметом тоже не трогается");
        assertTrue(
            report.untouched()
                .contains(Integer.valueOf(0)));
        assertTrue(
            report.untouched()
                .contains(Integer.valueOf(KitDefinition.ARMOR_HELMET)));
        assertEquals(BREAD, report.slots()[1], "хлеб идёт по переполнению в первый свободный слот");
        assertEquals(
            Arrays.asList(KitItem.of("minecraft:diamond_helmet", 1)),
            report.pending(),
            "шлем, чей слот занят невыразимым предметом, ждёт в буфере");
        assertEquals(16, report.delivered());
    }

    @Test
    void wornSlotsAreCopiedNotReused() {
        KitDefinition kit = KitDefinition.named("starter")
            .build();
        KitItem[] worn = slots();
        worn[0] = BREAD;

        KitDelivery report = KitDelivery.deliver(kit, debt(), WornSlots.of(worn), stacking());

        assertEquals(BREAD, report.slots()[0]);
        worn[0] = null;
        assertEquals(BREAD, report.slots()[0], "выдача не делит массив со слотами игрока");
    }

    private static KitDefinition kit(BuilderSlot... slots) {
        KitDefinition.Builder builder = KitDefinition.named("starter");
        for (BuilderSlot slot : slots) {
            builder.slot(slot.slot, slot.item);
        }
        return builder.build();
    }

    private static BuilderSlot slot(int index, KitItem item) {
        return new BuilderSlot(index, item);
    }

    private static final class BuilderSlot {

        private final int slot;
        private final KitItem item;

        private BuilderSlot(int slot, KitItem item) {
            this.slot = slot;
            this.item = item;
        }
    }

    private static List<KitItem> debt(KitItem... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static KitItem[] slots() {
        return new KitItem[KitDefinition.SLOTS];
    }

    private static KitItem[] full() {
        KitItem[] worn = new KitItem[KitDefinition.SLOTS];
        for (int slot = 0; slot < KitDefinition.INVENTORY_SLOTS; slot++) {
            worn[slot] = KitItem.of("minecraft:bread", 64);
        }
        for (int slot = 0; slot < KitDefinition.ARMOR_SLOTS; slot++) {
            worn[KitDefinition.INVENTORY_SLOTS + slot] = KitItem.of("minecraft:diamond_helmet", 1);
        }
        return worn;
    }

    private static KitStacking stacking() {
        return new KitStacking() {

            @Override
            public int limit(KitItem item) {
                boolean onePerStack = item.id()
                    .endsWith("pickaxe")
                    || item.id()
                        .endsWith("helmet")
                    || item.id()
                        .endsWith("boots");
                return onePerStack ? 1 : 64;
            }

            @Override
            public boolean merges(KitItem held, KitItem added) {
                return held.sameKind(added) && limit(held) > 1;
            }

            @Override
            public boolean accepts(int slot, KitItem item) {
                if (!item.id()
                    .startsWith("minecraft:")) {
                    return false;
                }
                if (slot < KitDefinition.INVENTORY_SLOTS) {
                    return true;
                }
                int armorType = KitDefinition.ARMOR_SLOTS - 1 - (slot - KitDefinition.INVENTORY_SLOTS);
                boolean helmet = item.id()
                    .endsWith("helmet");
                boolean boots = item.id()
                    .endsWith("boots");
                return armorType == 0 && helmet || armorType == 3 && boots;
            }
        };
    }
}
