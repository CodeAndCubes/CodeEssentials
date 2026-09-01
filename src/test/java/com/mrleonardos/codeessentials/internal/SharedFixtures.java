package com.mrleonardos.codeessentials.internal;

import com.mrleonardos.codecore.api.config.AuditSettings;
import com.mrleonardos.codecore.api.config.StorageSettings;
import com.mrleonardos.codeessentials.internal.store.JsonPlayerDataStore;

/** Секция главного файла и соседние с ней настройки линейки в том виде, в каком их видит мод. */
public final class SharedFixtures {

    public static final String PROVIDER = JsonPlayerDataStore.ID;
    public static final int AUTOSAVE_SECONDS = SharedSettings.FALLBACK_AUTOSAVE_SECONDS;

    private SharedFixtures() {}

    public static SharedSettings of(EssentialsSection section) {
        return new SharedSettings(section, storage(), new AuditSettings(true, false));
    }

    public static SharedSettings audit(boolean logChanges, boolean logChecks) {
        return new SharedSettings(new EssentialsSection(), storage(), new AuditSettings(logChanges, logChecks));
    }

    public static SharedSettings storage(String provider, int autosaveSeconds) {
        return new SharedSettings(
            new EssentialsSection(),
            new StorageSettings(provider, autosaveSeconds),
            new AuditSettings(true, false));
    }

    public static SharedSettings homes(int homes) {
        EssentialsSection section = new EssentialsSection();
        section.homes = homes;
        return of(section);
    }

    private static StorageSettings storage() {
        return new StorageSettings(PROVIDER, AUTOSAVE_SECONDS);
    }
}
