package com.mrleonardos.codeessentials.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.event.EssentialsEvents;
import com.mrleonardos.codeessentials.api.model.HomeRecord;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;

class HomeServiceImplTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final Point SPOT = Point.of(0, 1.0D, 64.0D, 2.0D);
    private static final Point OTHER = Point.of(-1, 3.0D, 70.0D, 4.0D);

    private EssentialsSettings settings;
    private ServiceTestStubs.State state;
    private ServiceTestStubs.Meta meta;
    private ServiceTestStubs.Ticks ticks;
    private ServiceTestStubs.Homes events;
    private HomeServiceImpl homes;

    @BeforeEach
    void setUp() {
        settings = new EssentialsSettings();
        state = new ServiceTestStubs.State();
        meta = new ServiceTestStubs.Meta();
        ticks = new ServiceTestStubs.Ticks();
        events = new ServiceTestStubs.Homes();
        homes = new HomeServiceImpl(
            () -> settings,
            meta,
            state,
            () -> events,
            ticks,
            LogManager.getLogger("codeessentials-test"));
    }

    @Test
    void withoutMetaTheLimitComesFromTheConfig() {
        settings.homes.defaultMax = 5;

        assertEquals(5, homes.homeLimit(STEVE));
    }

    @Test
    void metaOfTheGroupWinsOverTheConfig() {
        settings.homes.defaultMax = 3;
        meta.values.put(EssentialsSettings.META_MAX_HOMES, "7");

        assertEquals(7, homes.homeLimit(STEVE));
    }

    @Test
    void brokenMetaIsTreatedAsAbsent() {
        settings.homes.defaultMax = 3;

        meta.values.put(EssentialsSettings.META_MAX_HOMES, "три");
        assertEquals(3, homes.homeLimit(STEVE));

        meta.values.put(EssentialsSettings.META_MAX_HOMES, "-1");
        assertEquals(3, homes.homeLimit(STEVE));

        meta.values.put(EssentialsSettings.META_MAX_HOMES, "1000");
        assertEquals(3, homes.homeLimit(STEVE));
    }

    @Test
    void theFourthHomeUnderALimitOfThreeIsRefused() {
        meta.values.put(EssentialsSettings.META_MAX_HOMES, "3");
        homes.setHome(STEVE, "one", SPOT, "Steve");
        homes.setHome(STEVE, "two", SPOT, "Steve");
        homes.setHome(STEVE, "three", SPOT, "Steve");

        StoreResult fourth = homes.setHome(STEVE, "four", SPOT, "Steve");

        assertFalse(fourth.successful());
        assertEquals(
            StoreResult.Failure.LIMIT_REACHED,
            fourth.failure()
                .get());
        assertEquals(
            3,
            homes.homes(STEVE)
                .size(),
            "отказ значит, что хранилище не тронуто");
    }

    @Test
    void overwritingAtAFullLimitCostsNothing() {
        meta.values.put(EssentialsSettings.META_MAX_HOMES, "1");
        homes.setHome(STEVE, "base", SPOT, "Steve");

        StoreResult moved = homes.setHome(STEVE, "base", OTHER, "Steve");

        assertTrue(moved.successful());
        assertEquals(
            1,
            homes.homes(STEVE)
                .size());
        assertEquals(
            OTHER,
            homes.home(STEVE, "base")
                .get()
                .point());
    }

    @Test
    void aFallenLimitLeavesTheOldHomesInPlace() {
        meta.values.put(EssentialsSettings.META_MAX_HOMES, "5");
        for (String name : new String[] { "a", "b", "c", "d", "e" }) {
            homes.setHome(STEVE, name, SPOT, "Steve");
        }

        meta.values.put(EssentialsSettings.META_MAX_HOMES, "2");

        assertEquals(
            5,
            homes.homes(STEVE)
                .size());
        assertTrue(
            homes.home(STEVE, "e")
                .isPresent());
        assertFalse(
            homes.setHome(STEVE, "f", SPOT, "Steve")
                .successful());
        assertTrue(
            homes.setHome(STEVE, "a", OTHER, "Steve")
                .successful(),
            "переставить старый дом лимит не мешает");
    }

    @Test
    void aNameOutsideThePatternNeverReachesTheStore() {
        StoreResult written = homes.setHome(STEVE, "мой.дом", SPOT, "Steve");

        assertEquals(
            StoreResult.Failure.INVALID_VALUE,
            written.failure()
                .get());
        assertTrue(state.batches.isEmpty());
    }

    @Test
    void theNameIsStoredInLowerCase() {
        homes.setHome(STEVE, "Base", SPOT, "Steve");

        assertTrue(
            homes.home(STEVE, "base")
                .isPresent());
        assertTrue(
            homes.home(STEVE, "BASE")
                .isPresent());
    }

    @Test
    void anEnforcingVetoLeavesTheStoreAlone() {
        ServiceTestStubs.Watcher watcher = new ServiceTestStubs.Watcher();
        watcher.kind = EssentialsEvents.Kind.ENFORCE;
        watcher.verdict = EssentialsEvents.Decision.deny("чужая земля");
        events.register(0, watcher);

        StoreResult written = homes.setHome(STEVE, "base", SPOT, "Steve");

        assertEquals(
            StoreResult.Failure.VETOED,
            written.failure()
                .get());
        assertEquals(
            "чужая земля",
            written.message()
                .get());
        assertTrue(state.batches.isEmpty());
        assertEquals(Collections.singletonList("before base"), watcher.seen);
    }

    @Test
    void aBrokenEnforcingListenerRefusesTheWrite() {
        ServiceTestStubs.Watcher watcher = new ServiceTestStubs.Watcher();
        watcher.kind = EssentialsEvents.Kind.ENFORCE;
        watcher.throwOnBeforeSet = true;
        events.register(0, watcher);

        StoreResult written = homes.setHome(STEVE, "base", SPOT, "Steve");

        assertEquals(
            StoreResult.Failure.VETOED,
            written.failure()
                .get());
        assertTrue(state.batches.isEmpty());
    }

    @Test
    void aBrokenInformListenerIsSkipped() {
        ServiceTestStubs.Watcher watcher = new ServiceTestStubs.Watcher();
        watcher.throwOnBeforeSet = true;
        events.register(0, watcher);

        assertTrue(
            homes.setHome(STEVE, "base", SPOT, "Steve")
                .successful());
    }

    @Test
    void aStoreThatRefusesTheWriteIsReportedAsIs() {
        state.answer = StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, "disk is full");
        ServiceTestStubs.Watcher watcher = new ServiceTestStubs.Watcher();
        events.register(0, watcher);

        StoreResult written = homes.setHome(STEVE, "base", SPOT, "Steve");

        assertEquals(
            StoreResult.Failure.PROVIDER_FAILED,
            written.failure()
                .get());
        assertFalse(watcher.seen.contains("after base"), "неудачная запись не объявляется состоявшейся");
    }

    @Test
    void deletingAHomeThatIsNotThereAnswersNotFound() {
        StoreResult written = homes.deleteHome(STEVE, "ghost", "Steve");

        assertEquals(
            StoreResult.Failure.NOT_FOUND,
            written.failure()
                .get());
        assertTrue(state.batches.isEmpty());
    }

    @Test
    void changeNoticesAreGatheredIntoOneTick() {
        ServiceTestStubs.Watcher watcher = new ServiceTestStubs.Watcher();
        events.register(0, watcher);

        homes.setHome(STEVE, "one", SPOT, "Steve");
        homes.setHome(STEVE, "two", SPOT, "Steve");
        homes.deleteHome(STEVE, "one", "Steve");

        assertTrue(watcher.changes.isEmpty(), "до тика никто не оповещён");
        ticks.tick();
        assertEquals(1, watcher.changes.size());
        assertEquals(Collections.singleton(STEVE), watcher.changes.get(0));
    }

    @Test
    void aWriteFromAForeignThreadCallsItsListenersOnTheMainOne() {
        ServiceTestStubs.Handoff handoff = new ServiceTestStubs.Handoff();
        ServiceTestStubs.Watcher watcher = new ServiceTestStubs.Watcher();
        events.register(0, watcher);
        HomeServiceImpl offThread = new HomeServiceImpl(
            () -> settings,
            meta,
            state,
            () -> events,
            handoff,
            LogManager.getLogger("codeessentials-test"));

        assertTrue(
            offThread.setHome(STEVE, "base", SPOT, "Steve")
                .successful());

        assertEquals(2, watcher.threads.size(), () -> watcher.seen.toString());
        for (Thread seen : watcher.threads) {
            assertSame(handoff.main, seen, "слушателей дома зовут в главном потоке, а не в потоке вызывающего");
        }
        assertNotSame(Thread.currentThread(), handoff.main);
    }

    @Test
    void homesOfAnUnknownPlayerAreEmptyWithoutCreatingARecord() {
        assertTrue(
            homes.homes(STEVE)
                .isEmpty());
        assertTrue(state.records.isEmpty());
    }

    @Test
    void theBatchCarriesTheActorAndTheWholeRecord() {
        homes.setHome(STEVE, "base", SPOT, "Steve");

        assertEquals(
            "Steve",
            state.batches.get(0)
                .author());
        PlayerRecord written = state.records.get(STEVE);
        HomeRecord home = written.homes()
            .get("base");
        assertEquals(SPOT, home.point());
    }
}
