package com.mrleonardos.codeessentials.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.config.Comment;
import com.mrleonardos.codeessentials.internal.command.CommandRoots;
import com.mrleonardos.codeessentials.internal.service.SpawnFile;
import com.mrleonardos.codeessentials.internal.service.WarpsFile;

class SettingsLayoutTest {

    private static final List<Class<?>> FILES = Arrays
        .asList(EssentialsSettings.class, CommandRoots.class, WarpsFile.class, SpawnFile.class);

    private static final List<String> MOVED_OUT = Arrays.asList(
        "servicePriority",
        "storage",
        "storage.provider",
        "storage.playerProvider",
        "storage.autosaveSeconds",
        "audit",
        "audit.logChanges",
        "audit.logChecks",
        "homes",
        "homes.defaultMax",
        "teleport.warmupSeconds",
        "teleport.cooldowns");

    @Test
    void theModFileHoldsOnlyTheRareSettings() {
        assertEquals(
            new TreeSet<>(
                Arrays.asList(
                    "back.on",
                    "limits.backDepth",
                    "limits.homesPerPlayer",
                    "limits.nameLength",
                    "limits.pendingRequests",
                    "limits.requestTimeoutSeconds",
                    "limits.safeSpotRadius",
                    "limits.warmupSeconds",
                    "limits.warps",
                    "requests.maxPending",
                    "requests.rateSeconds",
                    "requests.timeoutSeconds",
                    "safeSpot.liquidOk",
                    "safeSpot.maxDown",
                    "safeSpot.maxUp",
                    "safeSpot.policy",
                    "safeSpot.radius",
                    "teleport.generateChunks",
                    "teleport.warmupCancelOnDamage",
                    "teleport.warmupMoveRadius")),
            paths(EssentialsSettings.class));
    }

    @Test
    void theMainFileSectionHoldsOnlyWhatMovedThere() {
        assertEquals(
            new TreeSet<>(
                Arrays.asList(
                    "cooldowns.back",
                    "cooldowns.home",
                    "cooldowns.spawn",
                    "cooldowns.tpa",
                    "cooldowns.warp",
                    "homes",
                    "warmupSeconds")),
            paths(EssentialsSection.class));
    }

    @Test
    void whatMovedToTheMainFileIsGoneFromTheModFile() {
        Set<String> own = paths(EssentialsSettings.class);

        for (String key : MOVED_OUT) {
            assertFalse(own.contains(key), () -> "ключ " + key + " уехал в главный файл и в essentials.toml не живёт");
        }
    }

    @Test
    void theOnlyNameRepeatedInBothFilesIsTheWarmupAndThatIsOnPurpose() {
        Set<String> repeated = leaves(EssentialsSettings.class);
        repeated.retainAll(leaves(EssentialsSection.class));

        assertEquals(
            new TreeSet<>(Arrays.asList("warmupSeconds")),
            repeated,
            "limits.warmupSeconds это потолок, [essentials] warmupSeconds это сама длина прогрева");
    }

    @Test
    void everyFieldOfTheFourFilesExplainsItself() {
        List<String> silent = new ArrayList<>();
        for (Class<?> type : FILES) {
            collectSilent(type, "", silent);
        }

        assertTrue(silent.isEmpty(), () -> "поле настроек без описания читают по исходникам: " + silent);
    }

    @Test
    void everyOneOfTheFourFilesSaysWhatItIsInItsFirstLines() {
        for (Class<?> type : FILES) {
            Comment header = type.getAnnotation(Comment.class);
            assertTrue(
                header != null && header.value().length > 0,
                () -> "шапка объясняет, что это за файл: " + type.getSimpleName());
        }
    }

    private static Set<String> paths(Class<?> type) {
        Set<String> found = new TreeSet<>();
        collect(type, "", found, true);
        return found;
    }

    private static Set<String> leaves(Class<?> type) {
        Set<String> found = new TreeSet<>();
        collect(type, "", found, false);
        return found;
    }

    private static void collect(Class<?> type, String prefix, Set<String> found, boolean dotted) {
        for (Field field : fields(type)) {
            Class<?> raw = raw(field.getGenericType());
            String path = prefix + field.getName();
            if (nested(raw)) {
                collect(raw, dotted ? path + "." : "", found, dotted);
                continue;
            }
            found.add(path);
        }
    }

    private static void collectSilent(Class<?> type, String prefix, List<String> silent) {
        for (Field field : fields(type)) {
            Class<?> raw = raw(field.getGenericType());
            Class<?> valueType = raw != null && Map.class.isAssignableFrom(raw) ? mapValue(field.getGenericType())
                : raw;
            String path = prefix + field.getName();
            if (field.getAnnotation(Comment.class) == null
                && (raw == null || raw.getAnnotation(Comment.class) == null)) {
                silent.add(path);
            }
            if (valueType != null && nested(valueType)) {
                collectSilent(valueType, path + ".", silent);
            }
        }
    }

    private static List<Field> fields(Class<?> type) {
        List<Field> found = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                continue;
            }
            found.add(field);
        }
        return found;
    }

    private static boolean nested(Class<?> raw) {
        return raw != null && raw.getName()
            .startsWith("com.mrleonardos.codeessentials");
    }

    private static Class<?> mapValue(Type type) {
        if (!(type instanceof ParameterizedType)) {
            return null;
        }
        Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
        return arguments.length == 2 ? raw(arguments[1]) : null;
    }

    private static Class<?> raw(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            return raw(((ParameterizedType) type).getRawType());
        }
        return null;
    }
}
