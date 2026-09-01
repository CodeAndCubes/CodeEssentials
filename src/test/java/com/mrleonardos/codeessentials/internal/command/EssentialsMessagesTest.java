package com.mrleonardos.codeessentials.internal.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.CancelReason;

class EssentialsMessagesTest {

    @Test
    void everyKeyIsTranslatedInBothLanguages() throws Exception {
        Set<String> declared = declaredKeys();

        assertEquals(new TreeSet<>(declared), new TreeSet<>(language("ru_RU").keySet()));
        assertEquals(new TreeSet<>(declared), new TreeSet<>(language("en_US").keySet()));
    }

    @Test
    void translationsHoldTheSameNumberOfPlaceholders() throws Exception {
        Map<String, String> russian = language("ru_RU");
        Map<String, String> english = language("en_US");

        for (Map.Entry<String, String> line : russian.entrySet()) {
            String other = english.get(line.getKey());
            assertNotNull(other, line.getKey());
            assertEquals(
                placeholders(line.getValue()),
                placeholders(other),
                "число подстановок разошлось у " + line.getKey());
        }
    }

    @Test
    void everyDeclaredKeyBelongsToTheMod() throws Exception {
        Set<String> declared = declaredKeys();

        assertFalse(declared.isEmpty());
        for (String key : declared) {
            assertTrue(key.startsWith("codeessentials."), key);
        }
    }

    @Test
    void everyCancelReasonHasItsOwnLine() throws Exception {
        Set<String> declared = declaredKeys();

        for (CancelReason reason : CancelReason.values()) {
            String key = EssentialsMessages.outcomeKey(reason);
            assertTrue(declared.contains(key), key);
            assertTrue(key.startsWith(reason.failure() ? "codeessentials.failed." : "codeessentials.cancel."), key);
        }
    }

    @Test
    void everyStoreFailureHasItsOwnLine() throws Exception {
        Set<String> declared = declaredKeys();

        for (StoreResult.Failure failure : StoreResult.Failure.values()) {
            String key = EssentialsMessages.failureKey(StoreResult.failure(failure, "x"));
            assertTrue(declared.contains(key), failure.name());
        }
    }

    @Test
    void everyCommandRootHasItsUsageLine() throws Exception {
        Map<String, String> russian = language("ru_RU");

        for (String root : CommandRoots.factoryAliases()
            .keySet()) {
            assertTrue(russian.containsKey("codeessentials.command.usage." + root), root);
        }
    }

    private static Set<String> declaredKeys() throws Exception {
        Set<String> keys = new LinkedHashSet<>();
        for (Field field : EssentialsMessages.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class
                || !Modifier.isPublic(field.getModifiers())) {
                continue;
            }
            keys.add((String) field.get(null));
        }
        return keys;
    }

    private static Map<String, String> language(String locale) throws Exception {
        Map<String, String> lines = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        try (
            InputStream stream = EssentialsMessages.class
                .getResourceAsStream("/assets/codeessentials/lang/" + locale + ".lang");
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String text = line.trim();
                if (text.isEmpty() || text.startsWith("#")) {
                    continue;
                }
                int split = text.indexOf('=');
                assertTrue(split > 0, "строка без ключа в " + locale + ": " + text);
                String key = text.substring(0, split);
                if (lines.put(key, text.substring(split + 1)) != null) {
                    duplicates.add(key);
                }
            }
        }
        assertTrue(duplicates.isEmpty(), "повторы ключей в " + locale + ": " + duplicates);
        return lines;
    }

    private static int placeholders(String text) {
        int count = 0;
        for (int index = 0; index + 1 < text.length(); index++) {
            if (text.charAt(index) == '%' && text.charAt(index + 1) == 's') {
                count++;
            }
        }
        return count;
    }
}
