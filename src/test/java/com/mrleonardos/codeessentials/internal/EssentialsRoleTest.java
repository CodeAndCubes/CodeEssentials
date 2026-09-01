package com.mrleonardos.codeessentials.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.adapter.RoleCapability;
import com.mrleonardos.codecore.api.adapter.RoleOwnerKind;
import com.mrleonardos.codecore.api.adapter.RoleServices;
import com.mrleonardos.codecore.api.adapter.RoleSpec;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codeessentials.TestConfigs;
import com.mrleonardos.codeessentials.api.manage.BackService;
import com.mrleonardos.codeessentials.api.manage.HomeService;
import com.mrleonardos.codeessentials.api.manage.SpawnService;
import com.mrleonardos.codeessentials.api.manage.WarpService;
import com.mrleonardos.codeessentials.api.teleport.TeleportService;
import com.mrleonardos.codeessentials.internal.service.WarpsFile;
import com.mrleonardos.codeessentials.internal.store.JsonPlayerDataStore;

class EssentialsRoleTest {

    private static final Set<String> ABILITIES = new LinkedHashSet<>(
        Arrays.asList("homes", "warps", "spawn", "back", "tpa"));

    @Test
    void theRoleNamesEveryAbilityAndEveryServiceItCloses() {
        RoleSpec spec = EssentialsRole.spec();

        assertEquals(ConfigRoles.ESSENTIALS, spec.role());
        assertEquals(ABILITIES, names(spec.capabilities()));
        assertEquals(
            new LinkedHashSet<>(
                Arrays.asList(
                    TeleportService.class,
                    HomeService.class,
                    WarpService.class,
                    SpawnService.class,
                    BackService.class)),
            new LinkedHashSet<>(spec.services()));
    }

    @Test
    void theClaimOfThisModCarriesTheWholeRole() {
        EssentialsClaim claim = new EssentialsClaim(
            () -> RoleServices.builder()
                .build());

        assertEquals("codeessentials", claim.name());
        assertEquals(ConfigRoles.ESSENTIALS, claim.role());
        assertEquals(RoleOwnerKind.MOD, claim.kind());
        assertTrue(claim.available(), "наш мод стоит на сервере всегда, когда он спрашивает");
        assertEquals(
            names(
                EssentialsRole.spec()
                    .capabilities()),
            names(claim.capabilities()),
            "мод закрывает роль целиком, недоступного у него нет");
    }

    @Test
    void aClaimThatHasNotWonTouchesNoFileOfItsOwn(@TempDir Path root) throws IOException {
        ConfigService configs = TestConfigs.of(root);
        Path warps = warpsFile(root);
        TestConfigs.write(warps, "schemaVersion = 1", "", "[warps.shop]", "location = \"0,1.0,64.0,2.0\"");
        long written = Files.getLastModifiedTime(warps)
            .toMillis();
        AtomicInteger built = new AtomicInteger();
        EssentialsClaim claim = new EssentialsClaim(() -> open(configs, built));

        assertTrue(claim.available());

        assertEquals(0, built.get(), "проигравшая заявка ничего не строит");
        assertEquals(
            written,
            Files.getLastModifiedTime(warps)
                .toMillis(),
            "существующий файл роли не тронут");
        assertFalse(Files.exists(settingsFile(root)), "своих настроек мод не заводит");
        assertFalse(Files.exists(playersFile(root)), "файла игроков нет");
    }

    @Test
    void theWinnerOfTheRoleOpensItsFiles(@TempDir Path root) {
        ConfigService configs = TestConfigs.of(root);
        TestConfigs.attachWorld(configs, root.resolve(TestConfigs.WORLD));
        AtomicInteger built = new AtomicInteger();
        EssentialsClaim claim = new EssentialsClaim(() -> open(configs, built));

        RoleServices created = claim.create();

        assertTrue(
            created.types()
                .isEmpty(),
            "заявка отдаёт ядру ровно то, что собрала");
        assertEquals(1, built.get());
        assertTrue(Files.exists(settingsFile(root)));
        assertTrue(Files.exists(warpsFile(root)));
        assertTrue(Files.exists(playersFile(root)));
    }

    private static Path settingsFile(Path root) {
        return TestConfigs.essentials(root)
            .resolve("essentials.toml");
    }

    private static Path warpsFile(Path root) {
        return TestConfigs.essentials(root)
            .resolve(WarpsFile.FILE_NAME);
    }

    private static Path playersFile(Path root) {
        return TestConfigs.worldState(root)
            .resolve(JsonPlayerDataStore.PLAYERS_FILE_NAME);
    }

    private static RoleServices open(ConfigService configs, AtomicInteger built) {
        built.incrementAndGet();
        configs.open(EssentialsSettings.spec());
        configs.open(WarpsFile.spec());
        configs.open(JsonPlayerDataStore.playersSpec());
        return RoleServices.builder()
            .build();
    }

    private static Set<String> names(Set<RoleCapability> capabilities) {
        Set<String> found = new LinkedHashSet<>();
        for (RoleCapability capability : capabilities) {
            found.add(capability.name());
        }
        return found;
    }
}
