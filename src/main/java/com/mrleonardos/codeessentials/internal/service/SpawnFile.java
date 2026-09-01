package com.mrleonardos.codeessentials.internal.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mrleonardos.codecore.api.config.Comment;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;

@Comment("Точки спавна. Файл правят и руками, и командой /setspawn.")
public final class SpawnFile {

    public static final int VERSION = 1;

    public static final String FILE = "spawn";
    public static final String FILE_NAME = "essentials-spawn.toml";

    @Comment({ "Точка для всех измерений сразу в виде \"измерение,x,y,z\".",
        "Пустое значение оставляет игрока на ванильном спавне мира." })
    public String global;

    @Comment("Точка измерения по его номеру. Она сильнее общей.")
    public Map<String, String> dimensions = new LinkedHashMap<>();

    public static ConfigSpec<SpawnFile> spec() {
        return ConfigSpec.of(EssentialsSettings.MODID, FILE, SpawnFile.class)
            .role(ConfigRoles.ESSENTIALS)
            .scope(ConfigScope.SETTINGS)
            .schemaVersion(VERSION)
            .defaults(SpawnFile::new)
            .validator(SpawnFile::heal)
            .build();
    }

    private static void heal(SpawnFile file) {
        if (file.dimensions == null) {
            file.dimensions = new LinkedHashMap<>();
        }
    }
}
