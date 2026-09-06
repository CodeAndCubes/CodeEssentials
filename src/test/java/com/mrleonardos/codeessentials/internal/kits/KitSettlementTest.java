package com.mrleonardos.codeessentials.internal.kits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.model.KitItem;

class KitSettlementTest {

    private static final KitItem BREAD = KitItem.of("minecraft:bread", 16);
    private static final KitItem PICK = KitItem.of("minecraft:iron_pickaxe", 1, 120, "");

    @Test
    void anUntouchedEditorOwesNothingInEitherDirection() {
        KitSettlement settlement = KitSettlement.between(Arrays.asList(BREAD, PICK), Arrays.asList(BREAD, PICK));

        assertTrue(
            settlement.back()
                .isEmpty());
        assertTrue(
            settlement.take()
                .isEmpty());
    }

    @Test
    void movingAnItemBetweenSlotsIsNeitherDebtNorReturn() {
        KitSettlement settlement = KitSettlement.between(Arrays.asList(BREAD, PICK), Arrays.asList(PICK, BREAD));

        assertTrue(
            settlement.back()
                .isEmpty());
        assertTrue(
            settlement.take()
                .isEmpty());
    }

    @Test
    void whatTheAdminBroughtInComesBack() {
        KitSettlement settlement = KitSettlement
            .between(Collections.singletonList(BREAD), Arrays.asList(BREAD, KitItem.of("minecraft:apple", 5)));

        assertEquals(Arrays.asList(KitItem.of("minecraft:apple", 5)), settlement.back());
        assertTrue(
            settlement.take()
                .isEmpty());
    }

    @Test
    void whatTheAdminPulledOutIsTakenBack() {
        KitSettlement settlement = KitSettlement.between(Arrays.asList(BREAD, PICK), Collections.singletonList(BREAD));

        assertTrue(
            settlement.back()
                .isEmpty());
        assertEquals(Arrays.asList(PICK), settlement.take());
    }

    @Test
    void aStackGrownInTheEditorReturnsOnlyItsGrowth() {
        KitSettlement settlement = KitSettlement.between(
            Collections.singletonList(KitItem.of("minecraft:bread", 20)),
            Collections.singletonList(KitItem.of("minecraft:bread", 64)));

        assertEquals(Arrays.asList(KitItem.of("minecraft:bread", 44)), settlement.back());
    }

    @Test
    void aStackShrunkInTheEditorTakesBackOnlyTheDifference() {
        KitSettlement settlement = KitSettlement.between(
            Collections.singletonList(KitItem.of("minecraft:bread", 64)),
            Collections.singletonList(KitItem.of("minecraft:bread", 20)));

        assertEquals(Arrays.asList(KitItem.of("minecraft:bread", 44)), settlement.take());
    }

    @Test
    void stacksOfTheSameKindInSeveralSlotsCountAsOne() {
        KitSettlement settlement = KitSettlement.between(
            Collections.singletonList(KitItem.of("minecraft:bread", 64)),
            Arrays.asList(KitItem.of("minecraft:bread", 64), KitItem.of("minecraft:bread", 32)));

        assertEquals(Arrays.asList(KitItem.of("minecraft:bread", 32)), settlement.back());
    }

    @Test
    void damageAndTagsKeepItemsApart() {
        KitSettlement settlement = KitSettlement.between(
            Collections.singletonList(KitItem.of("minecraft:iron_pickaxe", 1, 120, "")),
            Collections.singletonList(KitItem.of("minecraft:iron_pickaxe", 1, 0, "")));

        assertEquals(Arrays.asList(KitItem.of("minecraft:iron_pickaxe", 1, 0, "")), settlement.back());
        assertEquals(Arrays.asList(KitItem.of("minecraft:iron_pickaxe", 1, 120, "")), settlement.take());
    }

    @Test
    void aDifferenceLongerThanOneRecordIsCutIntoSeveral() {
        List<KitItem> opened = Arrays
            .asList(KitItem.of("minecraft:bread", KitItem.MAX_COUNT), KitItem.of("minecraft:bread", 100));

        KitSettlement settlement = KitSettlement.between(opened, Collections.<KitItem>emptyList());

        assertEquals(
            Arrays.asList(KitItem.of("minecraft:bread", KitItem.MAX_COUNT), KitItem.of("minecraft:bread", 100)),
            settlement.take());
    }
}
