package com.mrleonardos.codeessentials.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.model.BackPoint;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.store.StoreResult;

class BackLogTest {

    private static final Point FIRST = Point.of(0, 1.5D, 64.0D, 1.5D, 90.0F, 0.0F);
    private static final Point SECOND = Point.of(0, 2.5D, 64.0D, 2.5D);
    private static final Point THIRD = Point.of(-1, 3.5D, 64.0D, 3.5D);

    private final AtomicLong clock = new AtomicLong(1_000L);
    private final EngineFixtures.FakeRights rights = new EngineFixtures.FakeRights();

    @Test
    void freshPlayerHasNothingToGoBackTo() {
        BackLog log = log(EngineRules.defaults());

        assertFalse(
            log.peek(EngineFixtures.STEVE)
                .isPresent());
        assertTrue(
            log.stack(EngineFixtures.STEVE)
                .isEmpty());
        assertFalse(log.pop(EngineFixtures.STEVE), "снимать было нечего");
    }

    @Test
    void defaultDepthKeepsOnlyTheLatestPoint() {
        BackLog log = log(EngineRules.defaults());

        log.record(EngineFixtures.STEVE, teleport(FIRST));
        log.record(EngineFixtures.STEVE, teleport(SECOND));

        assertEquals(
            1,
            log.stack(EngineFixtures.STEVE)
                .size());
        assertEquals(
            SECOND,
            log.peek(EngineFixtures.STEVE)
                .get()
                .point());
    }

    @Test
    void depthFromMetaWinsAndStaysUnderTheCeiling() {
        BackLog log = log(EngineRules.defaults());
        rights.meta(EngineFixtures.STEVE, BackLog.DEPTH_META, 3);
        rights.meta(EngineFixtures.ALEX, BackLog.DEPTH_META, 900);

        assertEquals(3, log.depthFor(EngineFixtures.STEVE));
        assertEquals(10, log.depthFor(EngineFixtures.ALEX), "выше потолка глубина не растёт");
        assertEquals(1, log.depthFor(EngineFixtures.NOTCH), "без меты работает значение из настроек");

        log.record(EngineFixtures.STEVE, teleport(FIRST));
        log.record(EngineFixtures.STEVE, teleport(SECOND));
        log.record(EngineFixtures.STEVE, teleport(THIRD));

        assertEquals(
            3,
            log.stack(EngineFixtures.STEVE)
                .size());
        assertEquals(
            THIRD,
            log.stack(EngineFixtures.STEVE)
                .get(0)
                .point());
        assertEquals(
            FIRST,
            log.stack(EngineFixtures.STEVE)
                .get(2)
                .point());
    }

    @Test
    void deathPointCarriesItsMarkThroughTheStack() {
        BackLog log = log(EngineRules.defaults());

        log.record(EngineFixtures.STEVE, BackPoint.of(FIRST, BackPoint.Origin.DEATH, clock.get()));

        assertTrue(
            log.peek(EngineFixtures.STEVE)
                .get()
                .fromDeath());
    }

    @Test
    void popTakesTheTopAndLeavesTheRest() {
        BackLog log = log(
            EngineRules.builder()
                .backDepth(2)
                .build());
        log.record(EngineFixtures.STEVE, teleport(FIRST));
        log.record(EngineFixtures.STEVE, teleport(SECOND));

        assertTrue(log.pop(EngineFixtures.STEVE));
        assertEquals(
            FIRST,
            log.peek(EngineFixtures.STEVE)
                .get()
                .point());
        assertTrue(log.pop(EngineFixtures.STEVE));
        assertFalse(log.pop(EngineFixtures.STEVE));
    }

    @Test
    void switchedOffStackRefusesWithACodeInsteadOfPretendingItWorked() {
        BackLog log = log(
            EngineRules.builder()
                .backTrigger(BackLog.Trigger.OFF)
                .build());

        StoreResult refused = log.record(EngineFixtures.STEVE, teleport(FIRST));

        assertFalse(refused.successful());
        assertEquals(
            StoreResult.Failure.UNSUPPORTED,
            refused.failure()
                .get());
        assertTrue(
            log.stack(EngineFixtures.STEVE)
                .isEmpty());
    }

    @Test
    void deathOnlyTriggerKeepsDeathAndRefusesTeleports() {
        BackLog log = log(
            EngineRules.builder()
                .backTrigger(BackLog.Trigger.DEATH)
                .build());

        assertFalse(
            log.record(EngineFixtures.STEVE, teleport(FIRST))
                .successful());
        assertTrue(
            log.record(EngineFixtures.STEVE, BackPoint.of(SECOND, BackPoint.Origin.DEATH, clock.get()))
                .successful());
        assertEquals(
            1,
            log.stack(EngineFixtures.STEVE)
                .size());
    }

    @Test
    void teleportOnlyTriggerIsTheMirrorImage() {
        BackLog log = log(
            EngineRules.builder()
                .backTrigger(BackLog.Trigger.TELEPORT)
                .build());

        assertTrue(
            log.record(EngineFixtures.STEVE, teleport(FIRST))
                .successful());
        assertFalse(
            log.record(EngineFixtures.STEVE, BackPoint.of(SECOND, BackPoint.Origin.DEATH, clock.get()))
                .successful());
    }

    private BackPoint teleport(Point point) {
        return BackPoint.of(point, BackPoint.Origin.TELEPORT, clock.get());
    }

    private BackLog log(EngineRules rules) {
        return new BackLog(EngineFixtures.writer(clock::get), rights, () -> rules);
    }
}
