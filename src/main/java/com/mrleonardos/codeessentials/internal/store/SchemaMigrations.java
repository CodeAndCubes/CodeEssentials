package com.mrleonardos.codeessentials.internal.store;

import java.util.Collections;
import java.util.List;

import com.mrleonardos.codecore.api.config.Migration;

public final class SchemaMigrations {

    public static final String VERSION_FIELD = "schemaVersion";

    public static final int PLAYERS_VERSION = 1;
    public static final int RATES_VERSION = 1;

    private static final List<Migration> PLAYERS_CHAIN = Collections.emptyList();
    private static final List<Migration> RATES_CHAIN = Collections.emptyList();

    private SchemaMigrations() {}

    public static List<Migration> playersChain() {
        return PLAYERS_CHAIN;
    }

    public static List<Migration> ratesChain() {
        return RATES_CHAIN;
    }

    public static void checkChain(List<Migration> chain, int version, String file) {
        int step = 1;
        for (Migration migration : chain) {
            if (migration.from() != step || migration.to() != step + 1) {
                throw new IllegalStateException(
                    "Migration chain of " + file + " breaks at " + migration.from() + " -> " + migration.to());
            }
            step++;
        }
        if (step != version) {
            throw new IllegalStateException(
                "Migration chain of " + file + " reaches version " + step + " while the file is at " + version);
        }
    }
}
