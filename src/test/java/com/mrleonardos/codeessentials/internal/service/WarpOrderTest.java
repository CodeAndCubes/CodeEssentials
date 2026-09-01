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
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codeessentials.TestConfigs;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.model.WarpRecord;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.SharedSettings;

class WarpOrderTest {

    private static final Logger LOG = LogManager.getLogger("codeessentials-test");
    private static final Point SPOT = Point.of(0, 1.0D, 64.0D, 2.0D);

    /** Порядок нарочно не алфавитный: перетасовку видно сразу. */
    private static final List<String> WRITTEN = Arrays.asList("shop", "arena", "mine", "bank");

    @Test
    void theFileComesBackInTheOrderItWasWrittenIn(@TempDir Path root) {
        ConfigService configs = TestConfigs.of(root);
        ConfigFile<WarpsFile> file = configs.open(WarpsFile.spec());
        fill(file.get());
        file.save();

        assertEquals(WRITTEN, order(TestConfigs.read(file.path())), "порядок в самом toml тот, в каком варпы завели");

        file.reload();

        assertEquals(WRITTEN, keys(file.get()), "разбор toml порядок секций не тасует");
    }

    @Test
    void theOrderSurvivesARestartOfTheServer(@TempDir Path root) {
        ConfigService first = TestConfigs.of(root);
        ConfigFile<WarpsFile> written = first.open(WarpsFile.spec());
        fill(written.get());
        written.save();

        ConfigService second = TestConfigs.of(root);
        ConfigFile<WarpsFile> reopened = second.open(WarpsFile.spec());

        assertEquals(WRITTEN, keys(reopened.get()), "второй запуск сервера читает тот же порядок");

        reopened.save();

        assertEquals(WRITTEN, order(TestConfigs.read(reopened.path())), "и записывает его обратно тем же");
    }

    @Test
    void aCommentOfTheHumanSurvivesTheRewrite(@TempDir Path root) {
        ConfigService configs = TestConfigs.of(root);
        Path path = TestConfigs.essentials(root)
            .resolve(WarpsFile.FILE_NAME);
        TestConfigs.write(
            path,
            "schemaVersion = 1",
            "",
            "# рынок у ратуши, трогать только со старостой",
            "[warps.shop]",
            "location = \"" + SPOT.print() + "\"",
            "description = \"лавка\"",
            "",
            "[warps.arena]",
            "location = \"" + SPOT.print() + "\"",
            "description = \"\"",
            "myOwnNote = \"сюда ходят по пятницам\"");

        ConfigFile<WarpsFile> file = configs.open(WarpsFile.spec());
        WarpServiceImpl warps = service(file);
        assertTrue(
            warps.setWarp(WarpRecord.of("mine", SPOT, ""), false, "Steve")
                .successful());

        String text = TestConfigs.read(path);
        assertTrue(text.contains("# рынок у ратуши, трогать только со старостой"), () -> text);
        assertTrue(text.contains("myOwnNote"), () -> "чужой ключ мод не читает, но и не стирает: " + text);
        assertEquals(Arrays.asList("shop", "arena", "mine"), order(text), "дописанный варп встал в конец");
    }

    @Test
    void aNewWarpGoesToTheEndAndMovesNobody(@TempDir Path root) {
        ConfigFile<WarpsFile> file = filled(root);
        WarpServiceImpl warps = service(file);

        assertTrue(
            warps.setWarp(WarpRecord.of("port", SPOT, ""), false, "Steve")
                .successful());

        List<String> expected = new ArrayList<>(WRITTEN);
        expected.add("port");
        assertEquals(expected, keys(file.get()));
        assertEquals(expected, order(TestConfigs.read(file.path())));
    }

    @Test
    void movingAWarpLeavesItWhereItWasInTheFile(@TempDir Path root) {
        ConfigFile<WarpsFile> file = filled(root);
        WarpServiceImpl warps = service(file);

        assertTrue(
            warps.setWarp(WarpRecord.of("arena", Point.of(-1, 5.0D, 70.0D, 5.0D), "новое место"), false, "Steve")
                .successful());

        assertEquals(WRITTEN, order(TestConfigs.read(file.path())), "переставленный варп не всплывает в конец файла");
    }

    @Test
    void aDeletedWarpDoesNotShuffleTheRest(@TempDir Path root) {
        ConfigFile<WarpsFile> file = filled(root);
        WarpServiceImpl warps = service(file);

        assertTrue(
            warps.deleteWarp("arena", "Steve")
                .successful());

        assertEquals(Arrays.asList("shop", "mine", "bank"), order(TestConfigs.read(file.path())));
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
    void theListOfWarpsIsSortedByNameOnPurpose(@TempDir Path root) {
        WarpServiceImpl warps = service(filled(root));

        assertEquals(
            Arrays.asList("arena", "bank", "mine", "shop"),
            new ArrayList<>(
                warps.warps()
                    .keySet()),
            "вывод команды алфавитный независимо от порядка в файле, и это не то же самое, что порядок файла");
    }

    private static ConfigFile<WarpsFile> filled(Path root) {
        ConfigFile<WarpsFile> file = TestConfigs.of(root)
            .open(WarpsFile.spec());
        fill(file.get());
        file.save();
        return file;
    }

    private static void fill(WarpsFile file) {
        for (String name : WRITTEN) {
            file.warps.put(name, warp());
        }
    }

    private static WarpServiceImpl service(ConfigFile<WarpsFile> file) {
        return new WarpServiceImpl(
            EssentialsSettings::defaults,
            SharedSettings::defaults,
            file,
            new ServiceTestStubs.Spots(SafeSpotResult.found(SPOT, SPOT, 1)),
            LOG);
    }

    private static WarpsFile.Warp warp() {
        return new WarpsFile.Warp(SPOT.print(), "");
    }

    private static List<String> keys(WarpsFile file) {
        return new ArrayList<>(file.warps.keySet());
    }

    /** Имена варпов в том порядке, в каком заголовки секций стоят в файле. */
    private static List<String> order(String toml) {
        List<String> names = new ArrayList<>();
        for (String line : toml.split("\\R")) {
            String text = line.trim();
            if (text.startsWith("[warps.") && text.endsWith("]")) {
                names.add(text.substring("[warps.".length(), text.length() - 1));
            }
        }
        return names;
    }
}
