package com.mrleonardos.codeessentials.internal.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.annotations.SerializedName;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;

public final class SpawnFile {

    public static final int VERSION = 1;

    @SerializedName("default")
    public String globalSpawn;

    public Map<String, String> dimensions = new LinkedHashMap<>();

    public static ConfigSpec<SpawnFile> spec() {
        return ConfigSpec.of(EssentialsSettings.MODID, EssentialsSettings.SPAWN_FILE, SpawnFile.class)
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
