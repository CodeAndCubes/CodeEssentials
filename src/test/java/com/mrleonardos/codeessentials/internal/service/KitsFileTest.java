package com.mrleonardos.codeessentials.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codeessentials.TestConfigs;

class KitsFileTest {

    @Test
    void aHandwrittenFileIsReadAndNormalised(@TempDir Path root) {
        ConfigService configs = TestConfigs.of(root);
        TestConfigs.write(
            kitsFile(root),
            "[kits.starter]",
            "once = true",
            "cooldownSeconds = 3600",
            "",
            "[kits.starter.slots]",
            "0 = \"minecraft:bread 16\"",
            "39 = \"minecraft:iron_helmet\"",
            "",
            "[kits.Starter]",
            "slots = {}");
        ConfigFile<KitsFile> file = configs.open(KitsFile.spec());

        Map<String, KitsFile.Kit> kits = file.get().kits;

        assertEquals(1, kits.size(), "второе объявление того же имени проигрывается первым");
        assertTrue(kits.containsKey("starter"));
        assertTrue(kits.get("starter").once);
        assertEquals(3600, kits.get("starter").cooldownSeconds);
        assertEquals("minecraft:bread 16", kits.get("starter").slots.get("0"));
        assertEquals("minecraft:iron_helmet", kits.get("starter").slots.get("39"));
    }

    @Test
    void aKitWrittenByTheModKeepsTheHumanNotes(@TempDir Path root) {
        ConfigService configs = TestConfigs.of(root);
        Path path = kitsFile(root);
        TestConfigs.write(
            path,
            "[kits.starter]",
            "once = false",
            "cooldownSeconds = 0",
            "myOwnNote = \"выдавать по пятницам\"",
            "",
            "[kits.starter.slots]",
            "# и над слотом",
            "0 = \"minecraft:bread 5\"");
        ConfigFile<KitsFile> file = configs.open(KitsFile.spec());
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("0", "minecraft:bread 5");
        slots.put("1", "minecraft:apple 3");

        file.get().kits.put("pvp", new KitsFile.Kit(true, 60, slots));
        file.get().kits.get("starter").slots.put("1", "minecraft:apple 3");
        file.save();

        String text = TestConfigs.read(path);
        assertTrue(text.contains("# и над слотом"), "комментарий над ключом слота: " + text);
        assertTrue(text.contains("myOwnNote"), "чужой ключ мод не читает, но и не стирает: " + text);
        assertTrue(text.contains("[kits.pvp]"), "новый кит: " + text);
        assertTrue(text.contains("once = true"), "once: " + text);
        assertTrue(text.contains("cooldownSeconds = 60"), "пауза: " + text);
        assertTrue(text.contains("minecraft:apple 3"), "яблоко: " + text);
        assertTrue(text.contains("minecraft:bread 5"), "хлеб: " + text);
    }

    @Test
    void aMissingFileStartsEmptyAndIsWrittenWhole(@TempDir Path root) {
        ConfigService configs = TestConfigs.of(root);
        ConfigFile<KitsFile> file = configs.open(KitsFile.spec());

        assertTrue(file.get().kits.isEmpty());

        file.get().kits.put("starter", new KitsFile.Kit(false, 0, singleSlot()));
        file.save();

        String text = TestConfigs.read(kitsFile(root));
        assertTrue(text.startsWith("#"), () -> text);
        assertTrue(text.contains("Киты сервера"), () -> text);
        assertTrue(text.contains("[kits.starter]"), () -> text);
        assertTrue(text.contains("[kits.starter.slots]"), () -> text);
    }

    private static Path kitsFile(Path root) {
        return TestConfigs.essentials(root)
            .resolve(KitsFile.FILE_NAME);
    }

    private static Map<String, String> singleSlot() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("0", "minecraft:bread 1");
        return slots;
    }
}
