package com.mrleonardos.codeessentials.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.command.CommandInputException;
import com.mrleonardos.codecore.api.command.SenderKind;
import com.mrleonardos.codeessentials.api.manage.HomeService;
import com.mrleonardos.codeessentials.api.manage.KitService;
import com.mrleonardos.codeessentials.api.manage.WarpService;
import com.mrleonardos.codeessentials.api.model.HomeRecord;
import com.mrleonardos.codeessentials.api.model.KitDefinition;
import com.mrleonardos.codeessentials.api.model.KitItem;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.model.WarpRecord;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.internal.SharedSettings;
import com.mrleonardos.codeessentials.internal.store.EssentialsState;

class PlatformArgumentsTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final Point SOMEWHERE = Point.of(0, 1.0D, 64.0D, 2.0D);

    private final PlatformArguments arguments = new PlatformArguments(
        new NameResolver(EssentialsState::empty),
        NoHomes::new,
        NoWarps::new,
        NoKits::new,
        new CorePermissions(SharedSettings::defaults, LogManager.getLogger("CodeEssentialsTest")));

    @Test
    void aCoordinateReadsFractionsAndSigns() {
        assertEquals(
            Double.valueOf(12.5D),
            arguments.coordinate()
                .parse("12.5"));
        assertEquals(
            Double.valueOf(-3.0D),
            arguments.coordinate()
                .parse(" -3 "));
    }

    @Test
    void aWordIsNotACoordinate() {
        assertThrows(
            CommandInputException.class,
            () -> arguments.coordinate()
                .parse("here"));
    }

    @Test
    void endlessAndUnreadableNumbersAreRefused() {
        assertThrows(
            CommandInputException.class,
            () -> arguments.coordinate()
                .parse("NaN"));
        assertThrows(
            CommandInputException.class,
            () -> arguments.coordinate()
                .parse("Infinity"));
    }

    @Test
    void theRefusalCarriesTheKeyAndWhatWasTyped() {
        CommandInputException refusal = assertThrows(
            CommandInputException.class,
            () -> arguments.coordinate()
                .parse("here"));

        assertEquals("codeessentials.error.bad_arguments", refusal.translationKey());
        assertEquals(Collections.singletonList("here"), Arrays.asList(refusal.arguments()));
    }

    @Test
    void namesOfHomesAndWarpsComeInLowerCase() {
        assertEquals(
            "base",
            arguments.homeName()
                .parse(" BASE "));
        assertEquals(
            "shop",
            arguments.warpName()
                .parse("Shop"));
    }

    @Test
    void aPlayerNameKeepsItsCase() {
        assertEquals(
            "Steve",
            arguments.playerName()
                .parse(" Steve "));
    }

    @Test
    void tabOnAPlayerNameNamesTheKnownPlayers() {
        PlatformArguments known = new PlatformArguments(
            new NameResolver(
                () -> EssentialsState.empty()
                    .withPlayer(PlayerRecord.empty(STEVE, "Steve"))),
            NoHomes::new,
            NoWarps::new,
            NoKits::new,
            new CorePermissions(SharedSettings::defaults, LogManager.getLogger("CodeEssentialsTest")));

        assertEquals(
            Collections.singletonList("Steve"),
            known.playerName()
                .suggestions(PlatformStubs.Sender.of(SenderKind.CONSOLE, "Server"), ""),
            "подсказка молчит, когда suggestions стала перегрузкой вместо переопределения");
    }

    @Test
    void tabOnAHomeNameNamesTheHomesOfWhoeverIsTyping() {
        PlatformArguments own = new PlatformArguments(
            new NameResolver(EssentialsState::empty),
            () -> new OwnHomes(STEVE, "base", "mine"),
            NoWarps::new,
            NoKits::new,
            new CorePermissions(SharedSettings::defaults, LogManager.getLogger("CodeEssentialsTest")));

        assertEquals(
            Arrays.asList("base", "mine"),
            own.homeName()
                .suggestions(PlatformStubs.Sender.player(STEVE, "Steve"), ""));
        assertTrue(
            own.homeName()
                .suggestions(PlatformStubs.Sender.of(SenderKind.CONSOLE, "Server"), "")
                .isEmpty(),
            "своих домов у консоли нет, подсказывать нечего");
    }

    private static class NoHomes implements HomeService {

        @Override
        public Optional<HomeRecord> home(UUID player, String name) {
            return Optional.empty();
        }

        @Override
        public Map<String, HomeRecord> homes(UUID player) {
            return Collections.emptyMap();
        }

        @Override
        public int homeLimit(UUID player) {
            return 0;
        }

        @Override
        public StoreResult setHome(UUID player, String name, Point point, String actor) {
            return StoreResult.success();
        }

        @Override
        public StoreResult deleteHome(UUID player, String name, String actor) {
            return StoreResult.success();
        }
    }

    /** Дома одного игрока: всем остальным отвечает пустой картой. */
    private static final class OwnHomes extends NoHomes {

        private final UUID owner;
        private final Map<String, HomeRecord> homes = new LinkedHashMap<>();

        OwnHomes(UUID owner, String... names) {
            this.owner = owner;
            for (String name : names) {
                homes.put(name, HomeRecord.of(name, SOMEWHERE, 0L));
            }
        }

        @Override
        public Map<String, HomeRecord> homes(UUID player) {
            return owner.equals(player) ? homes : Collections.<String, HomeRecord>emptyMap();
        }
    }

    /** Киты сервера: перечень имён задаёт тест. */
    private static final class NoKits implements KitService {

        private final Map<String, KitDefinition> kits = new LinkedHashMap<>();

        NoKits(String... names) {
            for (String name : names) {
                kits.put(
                    name,
                    KitDefinition.named(name)
                        .slot(0, KitItem.of("minecraft:bread", 1))
                        .build());
            }
        }

        @Override
        public Map<String, KitDefinition> kits() {
            return kits;
        }

        @Override
        public Optional<KitDefinition> kit(String name) {
            return Optional.ofNullable(kits.get(name));
        }

        @Override
        public StoreResult define(KitDefinition kit, String actor) {
            return StoreResult.success();
        }

        @Override
        public StoreResult delete(String name, String actor) {
            return StoreResult.success();
        }

        @Override
        public int pendingItems(UUID player) {
            return 0;
        }

        @Override
        public Map<String, Integer> pendingByKit(UUID player) {
            return Collections.emptyMap();
        }

        @Override
        public boolean taken(UUID player, String kitName) {
            return false;
        }

        @Override
        public long cooldownLeft(UUID player, String kitName) {
            return 0L;
        }

        @Override
        public Claim claim(UUID player, String kit, String actor) {
            return Claim.unknown();
        }
    }

    private static final class NoWarps implements WarpService {

        @Override
        public Optional<WarpRecord> warp(String name) {
            return Optional.empty();
        }

        @Override
        public Map<String, WarpRecord> warps() {
            return Collections.emptyMap();
        }

        @Override
        public StoreResult setWarp(WarpRecord warp, boolean safeSpot, String actor) {
            return StoreResult.success();
        }

        @Override
        public StoreResult deleteWarp(String name, String actor) {
            return StoreResult.success();
        }
    }
}
