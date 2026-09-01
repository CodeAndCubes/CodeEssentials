package com.mrleonardos.codeessentials.internal.store;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mrleonardos.codecore.api.config.AuditSettings;
import com.mrleonardos.codecore.api.config.ConfigData;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codecore.api.config.Migration;
import com.mrleonardos.codecore.api.config.SectionSpec;
import com.mrleonardos.codecore.api.config.StorageSettings;

public final class StubConfigService implements ConfigService {

    public static final String WORLD_DIRECTORY = "world";
    public static final String LINEUP_DIRECTORY = "code";
    public static final String OWNER_PREFIX = "code";
    public static final String CLIENT_DIRECTORY = "client";

    public static final String STORAGE_SECTION = "storage";
    public static final String AUDIT_SECTION = "audit";
    public static final String SERVER_ID = "serverId";
    public static final String DEFAULT_SERVER_ID = "main";
    public static final String DEFAULT_PROVIDER = "json";
    public static final int DEFAULT_AUTOSAVE_SECONDS = 30;

    private final Path root;
    private final JsonObject main = new JsonObject();
    private final Map<String, StubFile<?>> files = new LinkedHashMap<>();
    private final Map<String, StubSection<?>> sections = new LinkedHashMap<>();
    private final List<StubFile<?>> world = new ArrayList<>();

    private boolean worldReady;

    public StubConfigService(Path root) {
        this.root = root;
    }

    /** Куда ядро положило бы этот файл: те же правила пути, что в боевой сборке. */
    public static Path pathOf(Path root, ConfigSpec<?> spec) {
        String owner = spec.modid()
            .startsWith(OWNER_PREFIX)
            && spec.modid()
                .length() > OWNER_PREFIX.length() ? spec.modid()
                    .substring(OWNER_PREFIX.length()) : spec.modid();
        String fileName = owner + (spec.name()
            .isEmpty() ? "" : "-" + spec.name())
            + spec.format()
                .extension();
        if (spec.scope() == ConfigScope.WORLD_STATE) {
            return root.resolve(WORLD_DIRECTORY)
                .resolve(LINEUP_DIRECTORY)
                .resolve(spec.role())
                .resolve(fileName);
        }
        Path directory = root.resolve(LINEUP_DIRECTORY)
            .resolve(spec.role());
        return spec.scope() == ConfigScope.CLIENT ? directory.resolve(CLIENT_DIRECTORY)
            .resolve(fileName) : directory.resolve(fileName);
    }

    @Override
    public <T> ConfigFile<T> open(ConfigSpec<T> spec) {
        String key = spec.scope() + ":" + spec.role() + "/" + spec.modid() + "/" + spec.name();
        StubFile<?> existing = files.get(key);
        if (existing != null) {
            return cast(existing);
        }
        StubFile<T> file = new StubFile<>(spec, pathOf(root, spec));
        files.put(key, file);
        if (spec.scope() == ConfigScope.WORLD_STATE) {
            world.add(file);
            if (worldReady) {
                file.load();
            }
        } else {
            file.load();
        }
        return cast(file);
    }

    @Override
    public <T> ConfigFile<T> section(SectionSpec<T> spec) {
        if (sections.containsKey(spec.name())) {
            throw new IllegalStateException("Section " + spec.name() + " is already declared");
        }
        StubSection<T> file = new StubSection<>(spec, main);
        sections.put(spec.name(), file);
        file.load();
        return file;
    }

    @Override
    public ConfigData main() {
        return new StubData(main);
    }

    @Override
    public String serverId() {
        return main().string(SERVER_ID, DEFAULT_SERVER_ID);
    }

    @Override
    public StorageSettings storage(String role) {
        ConfigData table = main().table(STORAGE_SECTION);
        String provider = table == null ? DEFAULT_PROVIDER : table.string("provider", DEFAULT_PROVIDER);
        int autosave = table == null ? DEFAULT_AUTOSAVE_SECONDS
            : table.integer("autosaveSeconds", DEFAULT_AUTOSAVE_SECONDS);
        ConfigData override = table == null ? null : table.table(role);
        if (override == null) {
            return new StorageSettings(provider, autosave);
        }
        return new StorageSettings(
            override.string("provider", provider),
            override.integer("autosaveSeconds", autosave));
    }

    @Override
    public AuditSettings audit(String role) {
        ConfigData table = main().table(AUDIT_SECTION);
        boolean changes = table == null || table.flag("logChanges", true);
        boolean checks = table != null && table.flag("logChecks", false);
        ConfigData override = table == null ? null : table.table(role);
        if (override == null) {
            return new AuditSettings(changes, checks);
        }
        return new AuditSettings(override.flag("logChanges", changes), override.flag("logChecks", checks));
    }

    @Override
    public Path directory(String role) {
        return root.resolve(LINEUP_DIRECTORY)
            .resolve(role);
    }

    @Override
    public void reloadAll() {
        for (StubFile<?> file : files.values()) {
            if (file.loaded()) {
                file.reload();
            }
        }
        for (StubSection<?> file : sections.values()) {
            file.reload();
        }
    }

    public StubConfigService bindWorld() {
        worldReady = true;
        for (StubFile<?> file : world) {
            file.load();
        }
        return this;
    }

    /** Главный файл как есть: тест правит его так же, как это сделал бы человек. */
    public JsonObject mainJson() {
        return main;
    }

    @SuppressWarnings("unchecked")
    private static <T> ConfigFile<T> cast(StubFile<?> file) {
        return (ConfigFile<T>) file;
    }

    static final class StubFile<T> implements ConfigFile<T> {

        private final ConfigSpec<T> spec;
        private final Path path;
        private T value;

        StubFile(ConfigSpec<T> spec, Path path) {
            this.spec = spec;
            this.path = path;
        }

        @Override
        public T get() {
            if (value == null) {
                throw new IllegalStateException("Config " + spec.name() + " is not loaded");
            }
            return value;
        }

        @Override
        public boolean loaded() {
            return value != null;
        }

        @Override
        public void save() {
            JsonObject data = Json.GSON.toJsonTree(value)
                .getAsJsonObject();
            data.addProperty(SchemaMigrations.VERSION_FIELD, Integer.valueOf(spec.schemaVersion()));
            Json.write(path, data);
        }

        @Override
        public void reload() {
            load();
        }

        @Override
        public Path path() {
            return path;
        }

        void load() {
            if (!Files.isRegularFile(path)) {
                value = spec.defaults()
                    .get();
                save();
                return;
            }
            JsonObject data = Json.read(path);
            if (data == null) {
                quarantine();
                value = spec.defaults()
                    .get();
                save();
                return;
            }
            value = Json.GSON.fromJson(migrated(data), spec.type());
            spec.validator()
                .accept(value);
        }

        private JsonObject migrated(JsonObject data) {
            JsonElement version = data.get(SchemaMigrations.VERSION_FIELD);
            int found = version == null || !version.isJsonPrimitive() ? 1 : version.getAsInt();
            while (found < spec.schemaVersion()) {
                Migration step = stepFrom(found);
                if (step == null) {
                    return data;
                }
                step.apply(new StubData(data));
                found = step.to();
            }
            return data;
        }

        private Migration stepFrom(int version) {
            for (Migration migration : spec.migrations()) {
                if (migration.from() == version) {
                    return migration;
                }
            }
            return null;
        }

        private void quarantine() {
            Path broken = path.resolveSibling(
                path.getFileName()
                    .toString() + ".broken");
            try {
                Files.move(path, broken, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    static final class StubSection<T> implements ConfigFile<T> {

        private final SectionSpec<T> spec;
        private final JsonObject main;
        private T value;

        StubSection(SectionSpec<T> spec, JsonObject main) {
            this.spec = spec;
            this.main = main;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public boolean loaded() {
            return value != null;
        }

        @Override
        public void save() {
            main.add(
                spec.name(),
                Json.GSON.toJsonTree(value)
                    .getAsJsonObject());
        }

        @Override
        public void reload() {
            load();
        }

        @Override
        public Path path() {
            return null;
        }

        void load() {
            JsonElement stored = main.get(spec.name());
            if (stored == null || !stored.isJsonObject()) {
                value = spec.defaults()
                    .get();
                return;
            }
            value = Json.GSON.fromJson(stored.getAsJsonObject(), spec.type());
            spec.validator()
                .accept(value);
        }
    }

    /** Дерево главного файла и файлов миграций в том же виде, в каком его отдаёт ядро. */
    public static final class StubData implements ConfigData {

        private final JsonObject root;

        public StubData(JsonObject root) {
            this.root = root;
        }

        @Override
        public boolean has(String path) {
            return element(path) != null;
        }

        @Override
        public Object get(String path) {
            JsonElement found = element(path);
            if (found == null || found.isJsonNull()) {
                return null;
            }
            if (found.isJsonObject()) {
                return new StubData(found.getAsJsonObject());
            }
            if (found.isJsonArray()) {
                List<Object> values = new ArrayList<>();
                for (JsonElement item : found.getAsJsonArray()) {
                    values.add(plain(item));
                }
                return values;
            }
            return plain(found);
        }

        @Override
        public String string(String path, String fallback) {
            JsonElement found = element(path);
            return found != null && found.isJsonPrimitive() ? found.getAsString() : fallback;
        }

        @Override
        public int integer(String path, int fallback) {
            JsonElement found = element(path);
            return isNumber(found) ? found.getAsInt() : fallback;
        }

        @Override
        public long number(String path, long fallback) {
            JsonElement found = element(path);
            return isNumber(found) ? found.getAsLong() : fallback;
        }

        @Override
        public boolean flag(String path, boolean fallback) {
            JsonElement found = element(path);
            return found != null && found.isJsonPrimitive()
                && found.getAsJsonPrimitive()
                    .isBoolean() ? found.getAsBoolean() : fallback;
        }

        @Override
        public ConfigData table(String path) {
            JsonElement found = element(path);
            return found != null && found.isJsonObject() ? new StubData(found.getAsJsonObject()) : null;
        }

        @Override
        public List<ConfigData> tables(String path) {
            JsonElement found = element(path);
            List<ConfigData> values = new ArrayList<>();
            if (found == null || !found.isJsonArray()) {
                return values;
            }
            for (JsonElement item : found.getAsJsonArray()) {
                if (item.isJsonObject()) {
                    values.add(new StubData(item.getAsJsonObject()));
                }
            }
            return values;
        }

        @Override
        public Set<String> keys() {
            return new LinkedHashSet<>(names());
        }

        @Override
        public void set(String path, Object value) {
            String[] steps = path.split("\\.");
            JsonObject table = root;
            for (int index = 0; index < steps.length - 1; index++) {
                JsonElement next = table.get(steps[index]);
                if (next == null || !next.isJsonObject()) {
                    JsonObject created = new JsonObject();
                    table.add(steps[index], created);
                    table = created;
                    continue;
                }
                table = next.getAsJsonObject();
            }
            table.add(steps[steps.length - 1], element(value));
        }

        @Override
        public void remove(String path) {
            String[] steps = path.split("\\.");
            JsonObject table = root;
            for (int index = 0; index < steps.length - 1; index++) {
                JsonElement next = table.get(steps[index]);
                if (next == null || !next.isJsonObject()) {
                    return;
                }
                table = next.getAsJsonObject();
            }
            table.remove(steps[steps.length - 1]);
        }

        @Override
        public ConfigData newTable() {
            return new StubData(new JsonObject());
        }

        private List<String> names() {
            List<String> found = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                found.add(entry.getKey());
            }
            return found;
        }

        private JsonElement element(String path) {
            JsonElement current = root;
            for (String step : path.split("\\.")) {
                if (current == null || !current.isJsonObject()) {
                    return null;
                }
                current = current.getAsJsonObject()
                    .get(step);
            }
            return current;
        }

        private static JsonElement element(Object value) {
            if (value instanceof StubData) {
                return ((StubData) value).root;
            }
            return Json.GSON.toJsonTree(value);
        }

        private static Object plain(JsonElement item) {
            if (!item.isJsonPrimitive()) {
                return item.isJsonObject() ? new StubData(item.getAsJsonObject()) : null;
            }
            JsonPrimitive primitive = item.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return Boolean.valueOf(primitive.getAsBoolean());
            }
            return primitive.isNumber() ? primitive.getAsNumber() : primitive.getAsString();
        }

        private static boolean isNumber(JsonElement found) {
            return found != null && found.isJsonPrimitive()
                && found.getAsJsonPrimitive()
                    .isNumber();
        }
    }

    public static final class Json {

        public static final Gson GSON = new GsonBuilder().disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

        private Json() {}

        public static JsonObject read(Path path) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonElement parsed = new JsonParser().parse(reader);
                return parsed == null || !parsed.isJsonObject() ? null : parsed.getAsJsonObject();
            } catch (IOException | JsonParseException failure) {
                return null;
            }
        }

        public static void write(Path path, JsonObject data) {
            Path temporary = path.resolveSibling(
                path.getFileName()
                    .toString() + ".tmp");
            try {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                    GSON.toJson(data, writer);
                }
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }
}
