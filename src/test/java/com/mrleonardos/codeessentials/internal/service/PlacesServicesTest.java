package com.mrleonardos.codeessentials.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.model.BackPoint;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.model.WarpRecord;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.SharedSettings;

class PlacesServicesTest {

    private static final Logger LOG = LogManager.getLogger("codeessentials-test");
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final Point SHOP = Point.of(0, 1.0D, 64.0D, 2.0D);
    private static final Point NETHER = Point.of(-1, 3.0D, 70.0D, 4.0D);

    private EssentialsSettings settings;
    private SharedSettings shared;

    @BeforeEach
    void setUp() {
        settings = new EssentialsSettings();
        shared = SharedSettings.defaults();
    }

    @Test
    void aWarpWithAnUnreadableLocationIsSkippedWithoutLosingTheRest() {
        WarpsFile file = new WarpsFile();
        file.warps.put("shop", new WarpsFile.Warp(SHOP.print(), "лавка"));
        file.warps.put("broken", new WarpsFile.Warp("не точка", ""));
        WarpServiceImpl warps = warps(file, SafeSpotResult.found(SHOP, SHOP, 1));

        assertEquals(
            1,
            warps.warps()
                .size());
        assertTrue(
            warps.warp("shop")
                .isPresent());
    }

    @Test
    void aBrokenWarpIsNamedOncePerLoadNotPerRequest() {
        WarpsFile file = new WarpsFile();
        file.warps.put("broken", new WarpsFile.Warp("не точка", ""));
        WarpServiceImpl warps = warps(file, SafeSpotResult.found(SHOP, SHOP, 1));

        int told = record(() -> {
            warps.warps();
            warps.warps();
            warps.warp("broken");
        }, "broken");

        assertEquals(1, told, "битая запись называется один раз на загрузку файла, а не на каждый запрос");
    }

    @Test
    void theWarpNameIsReadWithoutRegardToCase() {
        WarpsFile file = new WarpsFile();
        file.warps.put("SHOP", new WarpsFile.Warp(SHOP.print(), ""));
        WarpServiceImpl warps = warps(file, SafeSpotResult.found(SHOP, SHOP, 1));

        assertTrue(
            warps.warp("shop")
                .isPresent());
        assertTrue(
            warps.warps()
                .containsKey("shop"));
    }

    @Test
    void aKeyWrittenInUpperCaseIsHealedIntoTheNameEveryPathUses() {
        WarpsFile file = new WarpsFile();
        file.warps.put("Shop", new WarpsFile.Warp(SHOP.print(), "рынок у ратуши"));
        WarpsFile.heal(file);
        ServiceTestStubs.Files<WarpsFile> held = new ServiceTestStubs.Files<>(file);
        WarpServiceImpl warps = new WarpServiceImpl(
            () -> settings,
            () -> shared,
            held,
            new ServiceTestStubs.Spots(SafeSpotResult.found(SHOP, SHOP, 1)),
            LOG);

        assertTrue(
            warps.setWarp(WarpRecord.of("shop", NETHER, "рынок у ратуши"), false, "Steve")
                .successful());
        assertEquals(1, file.warps.size(), "второго ключа под тем же именем быть не должно");
        assertTrue(
            warps.deleteWarp("shop", "Steve")
                .successful());
        assertTrue(file.warps.isEmpty(), "чтение, запись и удаление обязаны сходиться на одном ключе");
    }

    @Test
    void anUnsafeSpotRefusesTheWarpAndLeavesTheFileAlone() {
        WarpsFile file = new WarpsFile();
        ServiceTestStubs.Files<WarpsFile> held = new ServiceTestStubs.Files<>(file);
        WarpServiceImpl warps = new WarpServiceImpl(
            () -> settings,
            () -> shared,
            held,
            new ServiceTestStubs.Spots(SafeSpotResult.unsafe(1225)),
            LOG);

        StoreResult written = warps.setWarp(WarpRecord.of("shop", SHOP, ""), true, "Steve");

        assertEquals(
            StoreResult.Failure.UNSAFE_SPOT,
            written.failure()
                .get());
        assertTrue(file.warps.isEmpty());
        assertEquals(0, held.saves);
    }

    @Test
    void forceSkipsTheCheckAndWritesTheWarp() {
        WarpsFile file = new WarpsFile();
        ServiceTestStubs.Files<WarpsFile> held = new ServiceTestStubs.Files<>(file);
        WarpServiceImpl warps = new WarpServiceImpl(
            () -> settings,
            () -> shared,
            held,
            new ServiceTestStubs.Spots(SafeSpotResult.unsafe(1225)),
            LOG);

        assertTrue(
            warps.setWarp(WarpRecord.of("shop", SHOP, ""), false, "Steve")
                .successful());
        assertEquals(1, held.saves);
        assertEquals(SHOP.print(), file.warps.get("shop").location);
    }

    @Test
    void aFileThatRefusesToSaveRollsTheChangeBack() {
        WarpsFile file = new WarpsFile();
        ServiceTestStubs.Files<WarpsFile> held = new ServiceTestStubs.Files<>(file);
        held.failOnSave = new IllegalStateException("disk is full");
        WarpServiceImpl warps = new WarpServiceImpl(
            () -> settings,
            () -> shared,
            held,
            new ServiceTestStubs.Spots(SafeSpotResult.found(SHOP, SHOP, 1)),
            LOG);

        StoreResult written = warps.setWarp(WarpRecord.of("shop", SHOP, ""), true, "Steve");

        assertEquals(
            StoreResult.Failure.PROVIDER_FAILED,
            written.failure()
                .get());
        assertTrue(file.warps.isEmpty(), "отказ значит, что в памяти тоже ничего не осело");
    }

    @Test
    void deletingAWarpThatIsNotThereAnswersNotFound() {
        WarpServiceImpl warps = warps(new WarpsFile(), SafeSpotResult.found(SHOP, SHOP, 1));

        assertEquals(
            StoreResult.Failure.NOT_FOUND,
            warps.deleteWarp("ghost", "Steve")
                .failure()
                .get());
    }

    @Test
    void theWarpCeilingCountsTheStoredNames() {
        settings.limits.warps = 1;
        WarpsFile file = new WarpsFile();
        WarpServiceImpl warps = warps(file, SafeSpotResult.found(SHOP, SHOP, 1));

        assertTrue(
            warps.setWarp(WarpRecord.of("shop", SHOP, ""), true, "Steve")
                .successful());
        assertEquals(
            StoreResult.Failure.LIMIT_REACHED,
            warps.setWarp(WarpRecord.of("mine", SHOP, ""), true, "Steve")
                .failure()
                .get());
        assertTrue(
            warps.setWarp(WarpRecord.of("shop", NETHER, ""), true, "Steve")
                .successful(),
            "переставить существующий варп потолок не мешает");
    }

    @Test
    void theSpawnOfTheDimensionWinsOverTheSharedOne() {
        SpawnFile file = new SpawnFile();
        file.global = SHOP.print();
        file.dimensions.put("-1", NETHER.print());
        SpawnServiceImpl spawns = new SpawnServiceImpl(() -> shared, new ServiceTestStubs.Files<>(file), LOG);

        assertEquals(
            NETHER,
            spawns.spawnFor(-1)
                .get());
        assertEquals(
            SHOP,
            spawns.spawnFor(0)
                .get());
    }

    @Test
    void anEmptyFileLeavesTheSpawnToVanilla() {
        SpawnServiceImpl spawns = new SpawnServiceImpl(
            () -> shared,
            new ServiceTestStubs.Files<>(new SpawnFile()),
            LOG);

        assertFalse(
            spawns.spawnFor(0)
                .isPresent());
        assertTrue(
            spawns.table()
                .isEmpty());
    }

    @Test
    void theDimensionSpawnIsKeyedByThePointItself() {
        SpawnFile file = new SpawnFile();
        SpawnServiceImpl spawns = new SpawnServiceImpl(() -> shared, new ServiceTestStubs.Files<>(file), LOG);

        assertTrue(
            spawns.setDimensionSpawn(NETHER, "Steve")
                .successful());

        assertEquals(NETHER.print(), file.dimensions.get("-1"));
        assertEquals(
            NETHER,
            spawns.spawnFor(-1)
                .get());
    }

    @Test
    void aBrokenSpawnLineIsSkipped() {
        SpawnFile file = new SpawnFile();
        file.global = "мусор";
        file.dimensions.put("нечисло", NETHER.print());
        SpawnServiceImpl spawns = new SpawnServiceImpl(() -> shared, new ServiceTestStubs.Files<>(file), LOG);

        assertTrue(
            spawns.table()
                .isEmpty());
    }

    @Test
    void theBackDepthComesFromTheMetaAndStaysUnderTheCap() {
        ServiceTestStubs.State state = new ServiceTestStubs.State();
        ServiceTestStubs.Meta meta = new ServiceTestStubs.Meta();
        BackServiceImpl backs = new BackServiceImpl(() -> settings, meta, state, LOG);

        assertEquals(1, backs.depthFor(STEVE));

        meta.values.put(EssentialsSettings.META_BACK_DEPTH, "4");
        assertEquals(4, backs.depthFor(STEVE));

        meta.values.put(EssentialsSettings.META_BACK_DEPTH, "1000");
        assertEquals(1, backs.depthFor(STEVE), "значение выше капа считается незаданным");

        meta.values.put(EssentialsSettings.META_BACK_DEPTH, "две");
        assertEquals(1, backs.depthFor(STEVE));
    }

    @Test
    void theStackKeepsOnlyTheAllowedDepth() {
        ServiceTestStubs.State state = new ServiceTestStubs.State();
        ServiceTestStubs.Meta meta = new ServiceTestStubs.Meta();
        meta.values.put(EssentialsSettings.META_BACK_DEPTH, "2");
        BackServiceImpl backs = new BackServiceImpl(() -> settings, meta, state, LOG);

        backs.record(STEVE, BackPoint.of(SHOP, BackPoint.Origin.TELEPORT, 1L));
        backs.record(STEVE, BackPoint.of(NETHER, BackPoint.Origin.TELEPORT, 2L));
        backs.record(STEVE, BackPoint.of(SHOP, BackPoint.Origin.DEATH, 3L));

        assertEquals(
            2,
            backs.stack(STEVE)
                .size());
        assertTrue(
            backs.peek(STEVE)
                .get()
                .fromDeath());
    }

    @Test
    void backOffMeansNothingIsWrittenAndTheAnswerSaysSo() {
        settings.back.on = EssentialsSettings.BACK_DEATH;
        ServiceTestStubs.State state = new ServiceTestStubs.State();
        BackServiceImpl backs = new BackServiceImpl(() -> settings, new ServiceTestStubs.Meta(), state, LOG);

        StoreResult written = backs.record(STEVE, BackPoint.of(SHOP, BackPoint.Origin.TELEPORT, 1L));

        assertEquals(
            StoreResult.Failure.UNSUPPORTED,
            written.failure()
                .get());
        assertTrue(state.batches.isEmpty());
        assertTrue(
            backs.record(STEVE, BackPoint.of(SHOP, BackPoint.Origin.DEATH, 2L))
                .successful());
    }

    @Test
    void poppingAnEmptyStackChangesNothing() {
        ServiceTestStubs.State state = new ServiceTestStubs.State();
        BackServiceImpl backs = new BackServiceImpl(() -> settings, new ServiceTestStubs.Meta(), state, LOG);

        assertFalse(backs.pop(STEVE));
        assertTrue(state.batches.isEmpty());
    }

    @Test
    void popTakesTheTopAndLeavesTheRest() {
        ServiceTestStubs.Meta meta = new ServiceTestStubs.Meta();
        meta.values.put(EssentialsSettings.META_BACK_DEPTH, "2");
        BackServiceImpl backs = new BackServiceImpl(() -> settings, meta, new ServiceTestStubs.State(), LOG);
        backs.record(STEVE, BackPoint.of(SHOP, BackPoint.Origin.TELEPORT, 1L));
        backs.record(STEVE, BackPoint.of(NETHER, BackPoint.Origin.TELEPORT, 2L));

        assertTrue(backs.pop(STEVE));
        assertEquals(
            SHOP,
            backs.peek(STEVE)
                .get()
                .point());
        assertTrue(backs.pop(STEVE));
        assertFalse(backs.pop(STEVE));
    }

    @Test
    void theTeleportOnlyModeIsTheMirrorOfTheDeathOnlyOne() {
        settings.back.on = EssentialsSettings.BACK_TELEPORT;
        BackServiceImpl backs = new BackServiceImpl(
            () -> settings,
            new ServiceTestStubs.Meta(),
            new ServiceTestStubs.State(),
            LOG);

        assertTrue(
            backs.record(STEVE, BackPoint.of(SHOP, BackPoint.Origin.TELEPORT, 1L))
                .successful());
        assertEquals(
            StoreResult.Failure.UNSUPPORTED,
            backs.record(STEVE, BackPoint.of(NETHER, BackPoint.Origin.DEATH, 2L))
                .failure()
                .get());
    }

    private WarpServiceImpl warps(WarpsFile file, SafeSpotResult spot) {
        return new WarpServiceImpl(
            () -> settings,
            () -> shared,
            new ServiceTestStubs.Files<>(file),
            new ServiceTestStubs.Spots(spot),
            LOG);
    }

    private int record(Runnable work, String word) {
        org.apache.logging.log4j.core.Logger held = (org.apache.logging.log4j.core.Logger) LOG;
        CountingAppender appender = new CountingAppender(word);
        org.apache.logging.log4j.Level before = held.getLevel();
        held.addAppender(appender);
        held.setLevel(org.apache.logging.log4j.Level.WARN);
        try {
            work.run();
        } finally {
            held.removeAppender(appender);
            held.setLevel(before);
        }
        return appender.seen;
    }

    private static final class CountingAppender extends org.apache.logging.log4j.core.appender.AbstractAppender {

        private final String word;
        private int seen;

        CountingAppender(String word) {
            super("counting-" + word, null, null, true);
            this.word = word;
            start();
        }

        @Override
        public void append(org.apache.logging.log4j.core.LogEvent event) {
            if (event.getMessage()
                .getFormattedMessage()
                .contains(word)) {
                seen++;
            }
        }
    }
}
