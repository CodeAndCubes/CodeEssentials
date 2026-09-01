package com.mrleonardos.codeessentials.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.command.CommandException;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.manage.HomeService;
import com.mrleonardos.codeessentials.api.manage.WarpService;
import com.mrleonardos.codeessentials.api.model.HomeRecord;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.model.WarpRecord;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.internal.store.EssentialsState;

class PlatformArgumentsTest {

    private final PlatformArguments arguments = new PlatformArguments(
        new NameResolver(EssentialsState::empty),
        NoHomes::new,
        NoWarps::new,
        new CorePermissions(LogManager.getLogger("CodeEssentialsTest")));

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
            CommandException.class,
            () -> arguments.coordinate()
                .parse("here"));
    }

    @Test
    void endlessAndUnreadableNumbersAreRefused() {
        assertThrows(
            CommandException.class,
            () -> arguments.coordinate()
                .parse("NaN"));
        assertThrows(
            CommandException.class,
            () -> arguments.coordinate()
                .parse("Infinity"));
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

    private static final class NoHomes implements HomeService {

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
