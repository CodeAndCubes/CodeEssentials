package com.mrleonardos.codeessentials.internal.store;

import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codeessentials.TestConfigs;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.store.PlayerDataStore;

class JsonPlayerDataStoreContractTest extends PlayerDataStoreContract {

    private static final Logger LOG = LogManager.getLogger(JsonPlayerDataStoreContractTest.class);

    @TempDir
    Path root;

    @Override
    protected PlayerDataStore open() {
        ConfigService configs = TestConfigs.of(root);
        JsonPlayerDataStore store = JsonPlayerDataStore.create(configs, EssentialsLimits.defaults(), clock::get, LOG);
        TestConfigs.attachWorld(configs, root.resolve(TestConfigs.WORLD));
        return store;
    }
}
