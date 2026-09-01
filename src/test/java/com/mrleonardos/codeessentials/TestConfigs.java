package com.mrleonardos.codeessentials;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mrleonardos.codecore.api.config.ConfigService;

/**
 * Настоящий ConfigService ядра поверх временной папки.
 *
 * <p>
 * Своей заглушки у CodeEssentials больше нет: раскладка путей, toml, комментарии и перекрытие по роли
 * это поведение ядра, и проверять их подделкой значит проверять подделку. Реализация лежит в dev-джаре
 * ядра, который и так стоит на тестовом classpath, поэтому она достаётся по имени класса.
 */
public final class TestConfigs {

    public static final String LINEUP = "code";
    public static final String WORLD = "world";
    public static final String MAIN_FILE = "config.toml";
    public static final String ESSENTIALS = "essentials";

    private static final Logger LOG = LogManager.getLogger(TestConfigs.class);
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    private static final String SERVICE = "com.mrleonardos.codecore.internal.config.ConfigServiceImpl";
    private static final String PATHS = "com.mrleonardos.codecore.internal.config.ConfigPaths";

    private TestConfigs() {}

    public static ConfigService of(Path configDirectory) {
        try {
            Class<?> pathsType = Class.forName(PATHS);
            Constructor<?> pathsConstructor = pathsType.getConstructor(Path.class);
            Object paths = pathsConstructor.newInstance(configDirectory);
            Class<?> serviceType = Class.forName(SERVICE);
            Constructor<?> serviceConstructor = serviceType.getConstructor(pathsType, Logger.class);
            return (ConfigService) serviceConstructor.newInstance(paths, LOG);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                "ConfigService ядра не собрался, проверьте dev-джар CodeCore на тестовом classpath",
                failure);
        }
    }

    /** Мир загружен: файлы состояния открываются и читаются. */
    public static void attachWorld(ConfigService configs, Path worldDirectory) {
        call(configs, "attachWorld", new Class<?>[] { Path.class }, new Object[] { worldDirectory });
    }

    /** Мир выгружается: несохранённое уходит на диск, файлы состояния закрываются. */
    public static void detachWorld(ConfigService configs) {
        call(configs, "detachWorld", new Class<?>[0], new Object[0]);
    }

    /** Конец постинициализации: главный файл пишется со всеми объявленными секциями. */
    public static void seal(ConfigService configs, String... roles) {
        call(configs, "seal", new Class<?>[] { List.class }, new Object[] { Arrays.asList(roles) });
    }

    /** Папка линейки внутри папки конфигов игры. */
    public static Path lineup(Path configDirectory) {
        return configDirectory.resolve(LINEUP);
    }

    /** Папка перемещений, в которой лежат все четыре файла настроек мода. */
    public static Path essentials(Path configDirectory) {
        return lineup(configDirectory).resolve(ESSENTIALS);
    }

    /** Папка состояния мира: сюда ложатся дома и кулдауны. */
    public static Path worldState(Path root) {
        return root.resolve(WORLD)
            .resolve(LINEUP)
            .resolve(ESSENTIALS);
    }

    public static Path mainFile(Path configDirectory) {
        return lineup(configDirectory).resolve(MAIN_FILE);
    }

    /** Написать главный файл до того, как ядро его прочитает. */
    public static void writeMain(Path configDirectory, String... lines) {
        write(mainFile(configDirectory), lines);
    }

    public static void write(Path file, String... lines) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, Arrays.asList(lines), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Файл " + file + " не записан", failure);
        }
    }

    /** Машинный файл состояния: он остаётся json и после переезда на toml. */
    public static JsonObject readJson(Path file) {
        JsonElement parsed = new JsonParser().parse(read(file));
        return parsed == null || !parsed.isJsonObject() ? null : parsed.getAsJsonObject();
    }

    public static void writeJson(Path file, JsonObject data) {
        write(file, JSON.toJson(data));
    }

    public static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Файл " + file + " не прочитан", failure);
        }
    }

    private static void call(ConfigService configs, String name, Class<?>[] types, Object[] values) {
        try {
            Method method = configs.getClass()
                .getMethod(name, types);
            method.invoke(configs, values);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Метод " + name + " ядра не вызвался", failure);
        }
    }
}
