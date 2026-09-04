package com.mrleonardos.codeessentials.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class KitModelTest {

    @Test
    void aRecordReadsCountAndDamageWhenTheyAreWritten() {
        KitItem item = KitItem.parse("minecraft:bread 16 0")
            .orElseThrow(() -> new AssertionError("unreadable"));

        assertEquals("minecraft:bread", item.id());
        assertEquals(16, item.count());
        assertEquals(0, item.damage());
        assertEquals("", item.nbt());
    }

    @Test
    void aBareNameBecomesTheVanillaNamespace() {
        assertEquals(
            "minecraft:bread",
            KitItem.parse("Bread")
                .orElseThrow(() -> new AssertionError("unreadable"))
                .id());
        assertEquals(
            "tconstruct:material",
            KitItem.parse("TConStruct:Material")
                .orElseThrow(() -> new AssertionError("unreadable"))
                .id());
    }

    @Test
    void nbtSurvivesItsOwnSpaces() {
        KitItem item = KitItem.parse("minecraft:written_book 1 0 {display:{Name:\"Кит\"},pages:[\"a b\"]}")
            .orElseThrow(() -> new AssertionError("unreadable"));

        assertEquals(1, item.count());
        assertEquals("{display:{Name:\"Кит\"},pages:[\"a b\"]}", item.nbt());
    }

    @Test
    void unreadableRecordsAnswerEmpty() {
        assertFalse(
            KitItem.parse(null)
                .isPresent());
        assertFalse(
            KitItem.parse("   ")
                .isPresent());
        assertFalse(
            KitItem.parse("bread 1 0 tail")
                .isPresent());
        assertFalse(
            KitItem.parse("bread zero")
                .isPresent());
        assertFalse(
            KitItem.parse("bread 0")
                .isPresent());
        assertFalse(
            KitItem.parse("bread 1 -1")
                .isPresent());
        assertFalse(
            KitItem.parse("bread 1 0 {open")
                .isPresent());
        assertFalse(
            KitItem.parse("bread {display:{Name:\"x\"}} extra")
                .isPresent());
    }

    @Test
    void printKeepsTheShortFormWhenThereIsNothingElse() {
        assertEquals(
            "minecraft:bread 16",
            KitItem.parse("minecraft:bread 16 0")
                .orElseThrow(() -> new AssertionError("unreadable"))
                .print());
        assertEquals(
            "minecraft:iron_pickaxe 1 120",
            KitItem.parse("minecraft:iron_pickaxe 1 120")
                .orElseThrow(() -> new AssertionError("unreadable"))
                .print());
    }

    @Test
    void printedRecordParsesBackToTheSameItem() {
        KitItem item = KitItem.of("bread", 4, 7, "{display:{Name:\"x y\"}}");

        assertEquals(
            item,
            KitItem.parse(item.print())
                .orElse(null));
    }

    @Test
    void boundariesHoldOnTheFactory() {
        assertThrows(NullPointerException.class, () -> KitItem.of(null, 1, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> KitItem.of("has space", 1, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> KitItem.of("minecraft:bread", 0, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> KitItem.of("minecraft:bread", 4097, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> KitItem.of("minecraft:bread", 1, -1, ""));
        assertThrows(IllegalArgumentException.class, () -> KitItem.of("minecraft:bread", 1, 32768, ""));
        assertThrows(IllegalArgumentException.class, () -> KitItem.of("minecraft:bread", 1, 0, "nbt"));
        assertEquals(
            4096,
            KitItem.of("minecraft:bread", 4096, 0, "")
                .count());
    }

    @Test
    void sameKindIgnoresTheCount() {
        KitItem bread = KitItem.of("minecraft:bread", 8, 0, "");

        assertTrue(bread.sameKind(KitItem.of("minecraft:bread", 64, 0, "")));
        assertFalse(bread.sameKind(KitItem.of("minecraft:bread", 8, 3, "")));
        assertFalse(bread.sameKind(KitItem.of("minecraft:apple", 8, 0, "")));
        assertFalse(bread.sameKind(KitItem.of("minecraft:bread", 8, 0, "{display:{Name:\"x\"}}")));
        assertFalse(bread.sameKind(null));
    }

    @Test
    void aNewCountReusesTheSameInstance() {
        KitItem bread = KitItem.of("minecraft:bread", 8, 0, "");

        assertNotSame(bread, bread.withCount(16));
        assertEquals(
            16,
            bread.withCount(16)
                .count());
        assertEquals(
            8,
            bread.withCount(8)
                .count());
    }

    @Test
    void slotsOfTheKitAreNumberedLikeTheInventory() {
        assertEquals(36, KitDefinition.INVENTORY_SLOTS);
        assertEquals(4, KitDefinition.ARMOR_SLOTS);
        assertEquals(40, KitDefinition.SLOTS);
        assertEquals(36, KitDefinition.ARMOR_BOOTS);
        assertEquals(37, KitDefinition.ARMOR_LEGGINGS);
        assertEquals(38, KitDefinition.ARMOR_CHESTPLATE);
        assertEquals(39, KitDefinition.ARMOR_HELMET);
    }

    @Test
    void aKitKeepsItsSlotsInSlotOrder() {
        Map<Integer, KitItem> given = new LinkedHashMap<>();
        given.put(Integer.valueOf(20), KitItem.of("minecraft:apple", 1));
        given.put(Integer.valueOf(0), KitItem.of("minecraft:bread", 1));
        KitDefinition kit = KitDefinition.of("starter", true, 30, given);

        assertEquals("starter", kit.name());
        assertTrue(kit.once());
        assertEquals(30, kit.cooldownSeconds());
        assertEquals(2, kit.size());
        assertFalse(kit.isEmpty());
        assertEquals(
            "[0=minecraft:bread 1, 20=minecraft:apple 1]",
            String.valueOf(
                kit.slots()
                    .entrySet()));
        assertEquals(
            "minecraft:bread 1",
            kit.slot(0)
                .map(KitItem::print)
                .orElse(""));
        assertFalse(
            kit.slot(1)
                .isPresent());
    }

    @Test
    void anEmptySlotListMightComeFromAnEmptyEditor() {
        assertTrue(
            KitDefinition.named("starter")
                .build()
                .isEmpty());
    }

    @Test
    void aKitRefusesBadNamesAndSlots() {
        assertThrows(
            IllegalArgumentException.class,
            () -> KitDefinition.named("Мой.Кит")
                .build());
        assertThrows(
            IllegalArgumentException.class,
            () -> KitDefinition.named("starter")
                .cooldown(-1)
                .build());
        assertThrows(
            IllegalArgumentException.class,
            () -> KitDefinition.of("starter", false, 0, singleSlot(40, KitItem.of("minecraft:bread", 1))));
        assertThrows(
            IllegalArgumentException.class,
            () -> KitDefinition.of("starter", false, 0, singleSlot(-1, KitItem.of("minecraft:bread", 1))));
        assertThrows(IllegalArgumentException.class, () -> KitDefinition.of("starter", false, 0, singleSlot(5, null)));
    }

    @Test
    void aBuilderReadsTheRecordForm() {
        KitDefinition kit = KitDefinition.named("starter")
            .cooldown(60)
            .slot(0, "minecraft:bread 16")
            .build();

        assertEquals(
            "minecraft:bread 16",
            kit.slot(0)
                .map(KitItem::print)
                .orElse(""));
    }

    private static Map<Integer, KitItem> singleSlot(int slot, KitItem item) {
        Map<Integer, KitItem> slots = new LinkedHashMap<>();
        slots.put(Integer.valueOf(slot), item);
        return slots;
    }
}
