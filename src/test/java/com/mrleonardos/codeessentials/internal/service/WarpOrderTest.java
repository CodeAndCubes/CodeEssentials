package com.mrleonardos.codeessentials.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.model.WarpRecord;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.SharedSettings;
import com.mrleonardos.codeessentials.internal.store.StubConfigService;

class WarpOrderTest {

    private static final Logger LOG = LogManager.getLogger("codeessentials-test");
    private static final Point SPOT = Point.of(0, 1.0D, 64.0D, 2.0D);

    /** Порядок нарочно не алфавитный: перетасовку видно сразу. */
    private static final List<String> WRITTEN = Arrays.asList("shop", "arena", "mine", "bank");

    @Test
    void theFileComesBackInTheOrderItWasWrittenIn(@TempDir Path root) {
        StubConfigService configs = new StubConfigService(root);
        ConfigFile<WarpsFile> file = configs.open(WarpsFile.spec());
        for (String name : WRITTEN) {
            file.get().warps.put(name, warp());
        }
        file.save();

        file.reload();

        assertEquals(WRITTEN, keys(file.get()), "варпы не тасуются между записью и чтением файла");
    }

    @Test
    void aFileEditedByHandKeepsTheOrderOfTheHuman() {
        WarpsFile file = new WarpsFile();
        file.warps.put("Shop", warp());
        file.warps.put("arena", warp());
        file.warps.put("MINE", warp());

        WarpsFile.heal(file);

        assertEquals(
            Arrays.asList("shop", "arena", "mine"),
            keys(file),
            "приведение имён к нижнему регистру порядок записей не меняет");
    }

    @Test
    void aNewWarpGoesToTheEndAndMovesNobody() {
        WarpsFile file = filled();
        WarpServiceImpl warps = service(file);

        assertTrue(
            warps.setWarp(WarpRecord.of("port", SPOT, ""), false, "Steve")
                .successful());

        List<String> expected = new ArrayList<>(WRITTEN);
        expected.add("port");
        assertEquals(expected, keys(file));
    }

    @Test
    void movingAWarpLeavesItWhereItWasInTheFile() {
        WarpsFile file = filled();
        WarpServiceImpl warps = service(file);

        assertTrue(
            warps.setWarp(WarpRecord.of("arena", Point.of(-1, 5.0D, 70.0D, 5.0D), "новое место"), false, "Steve")
                .successful());

        assertEquals(WRITTEN, keys(file), "переставленный варп не всплывает в конец файла");
    }

    @Test
    void aDeletedWarpDoesNotShuffleTheRest() {
        WarpsFile file = filled();
        WarpServiceImpl warps = service(file);

        assertTrue(
            warps.deleteWarp("arena", "Steve")
                .successful());

        assertEquals(Arrays.asList("shop", "mine", "bank"), keys(file));
    }

    @Test
    void theListOfWarpsIsSortedByNameOnPurpose() {
        WarpServiceImpl warps = service(filled());

        assertEquals(
            Arrays.asList("arena", "bank", "mine", "shop"),
            new ArrayList<>(
                warps.warps()
                    .keySet()),
            "вывод команды алфавитный независимо от порядка в файле, и это не то же самое, что порядок файла");
    }

    private static WarpsFile filled() {
        WarpsFile file = new WarpsFile();
        for (String name : WRITTEN) {
            file.warps.put(name, warp());
        }
        return file;
    }

    private static WarpServiceImpl service(WarpsFile file) {
        return new WarpServiceImpl(
            EssentialsSettings::defaults,
            SharedSettings::defaults,
            new ServiceTestStubs.Files<>(file),
            new ServiceTestStubs.Spots(SafeSpotResult.found(SPOT, SPOT, 1)),
            LOG);
    }

    private static WarpsFile.Warp warp() {
        return new WarpsFile.Warp(SPOT.print(), "");
    }

    private static List<String> keys(WarpsFile file) {
        return new ArrayList<>(file.warps.keySet());
    }
}
