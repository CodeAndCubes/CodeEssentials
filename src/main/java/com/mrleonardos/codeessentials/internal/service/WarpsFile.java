package com.mrleonardos.codeessentials.internal.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;

public final class WarpsFile {

    public static final int VERSION = 1;

    public Map<String, Warp> warps = new LinkedHashMap<>();

    public static ConfigSpec<WarpsFile> spec() {
        return ConfigSpec.of(EssentialsSettings.MODID, EssentialsSettings.WARPS_FILE, WarpsFile.class)
            .scope(ConfigScope.SETTINGS)
            .schemaVersion(VERSION)
            .defaults(WarpsFile::new)
            .validator(WarpsFile::heal)
            .build();
    }

    private static void heal(WarpsFile file) {
        if (file.warps == null) {
            file.warps = new LinkedHashMap<>();
        }
    }

    public static final class Warp {

        public String location;
        public String description = "";

        public Warp() {}

        public Warp(String location, String description) {
            this.location = location;
            this.description = description == null ? "" : description;
        }
    }
}
