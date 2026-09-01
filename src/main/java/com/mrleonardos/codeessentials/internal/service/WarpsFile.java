package com.mrleonardos.codeessentials.internal.service;

import java.util.LinkedHashMap;
import java.util.Locale;
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

    static void heal(WarpsFile file) {
        if (file.warps == null) {
            file.warps = new LinkedHashMap<>();
            return;
        }
        Map<String, Warp> keyed = new LinkedHashMap<>();
        for (Map.Entry<String, Warp> entry : file.warps.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String name = entry.getKey()
                .trim()
                .toLowerCase(Locale.ROOT);
            if (!keyed.containsKey(name)) {
                keyed.put(name, entry.getValue());
            }
        }
        file.warps = keyed;
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
