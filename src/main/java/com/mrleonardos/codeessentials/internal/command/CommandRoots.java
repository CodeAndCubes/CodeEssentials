package com.mrleonardos.codeessentials.internal.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.command.CommandNode;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;

public final class CommandRoots {

    public static final int VERSION = 1;

    public static final String HOME = "home";
    public static final String SETHOME = "sethome";
    public static final String DELHOME = "delhome";
    public static final String HOMES = "homes";
    public static final String WARP = "warp";
    public static final String WARPS = "warps";
    public static final String SETWARP = "setwarp";
    public static final String DELWARP = "delwarp";
    public static final String SPAWN = "spawn";
    public static final String SETSPAWN = "setspawn";
    public static final String BACK = "back";
    public static final String TPA = "tpa";
    public static final String TPAHERE = "tpahere";
    public static final String TPACCEPT = "tpaccept";
    public static final String TPDENY = "tpdeny";
    public static final String TPACANCEL = "tpacancel";
    public static final String TPATOGGLE = "tpatoggle";
    public static final String TP = "tp";
    public static final String TPPOS = "tppos";
    public static final String ECANCEL = "ecancel";
    public static final String ESSENTIALS = "essentials";

    private static final Map<String, List<String>> FACTORY = factoryTable();

    public Map<String, Entry> commands = factoryEntries();

    public static ConfigSpec<CommandRoots> spec() {
        return ConfigSpec.of(EssentialsSettings.MODID, EssentialsSettings.COMMANDS_FILE, CommandRoots.class)
            .scope(ConfigScope.SETTINGS)
            .schemaVersion(VERSION)
            .defaults(CommandRoots::new)
            .validator(CommandRoots::heal)
            .build();
    }

    public static Map<String, List<String>> factoryAliases() {
        return FACTORY;
    }

    public List<String> unknownRoots() {
        heal(this);
        List<String> unknown = new ArrayList<>();
        for (String name : commands.keySet()) {
            if (!FACTORY.containsKey(name)) {
                unknown.add(name);
            }
        }
        return unknown;
    }

    public List<CommandNode> chosen(List<CommandNode> roots, Logger log) {
        for (String name : unknownRoots()) {
            log.warn("commands.json holds an unknown command root {}, the record is skipped", name);
        }
        List<CommandNode> chosen = new ArrayList<>();
        for (CommandNode root : roots) {
            Entry entry = commands.get(root.name());
            if (entry == null) {
                entry = factoryEntry(root.name());
                commands.put(root.name(), entry);
                log.info("commands.json had no record for {}, the factory one is added", root.name());
            }
            if (!entry.enabled) {
                log.info("Command root {} is off in commands.json, the name stays free", root.name());
                continue;
            }
            for (String alias : aliasesOf(entry, root.name())) {
                root.alias(alias);
            }
            chosen.add(root);
        }
        return chosen;
    }

    private static List<String> aliasesOf(Entry entry, String name) {
        if (entry.aliases == null) {
            return factoryAliasesOf(name);
        }
        List<String> cleaned = new ArrayList<>();
        for (String alias : entry.aliases) {
            if (alias == null) {
                continue;
            }
            String text = alias.trim()
                .toLowerCase(Locale.ROOT);
            if (!text.isEmpty() && !text.equals(name) && !cleaned.contains(text)) {
                cleaned.add(text);
            }
        }
        return cleaned;
    }

    private static void heal(CommandRoots file) {
        if (file.commands == null) {
            file.commands = factoryEntries();
        }
    }

    private static Map<String, Entry> factoryEntries() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (String name : FACTORY.keySet()) {
            entries.put(name, factoryEntry(name));
        }
        return entries;
    }

    private static Entry factoryEntry(String name) {
        return new Entry(true, factoryAliasesOf(name));
    }

    private static List<String> factoryAliasesOf(String name) {
        List<String> aliases = FACTORY.get(name);
        return aliases == null ? new ArrayList<>() : new ArrayList<>(aliases);
    }

    private static Map<String, List<String>> factoryTable() {
        Map<String, List<String>> table = new LinkedHashMap<>();
        table.put(HOME, none());
        table.put(SETHOME, none());
        table.put(DELHOME, none());
        table.put(HOMES, none());
        table.put(WARP, none());
        table.put(WARPS, none());
        table.put(SETWARP, none());
        table.put(DELWARP, none());
        table.put(SPAWN, none());
        table.put(SETSPAWN, none());
        table.put(BACK, none());
        table.put(TPA, none());
        table.put(TPAHERE, none());
        table.put(TPACCEPT, Collections.singletonList("tpyes"));
        table.put(TPDENY, Collections.singletonList("tpno"));
        table.put(TPACANCEL, none());
        table.put(TPATOGGLE, none());
        table.put(TP, none());
        table.put(TPPOS, none());
        table.put(ECANCEL, none());
        table.put(ESSENTIALS, Collections.singletonList("ess"));
        return Collections.unmodifiableMap(table);
    }

    private static List<String> none() {
        return Collections.emptyList();
    }

    public static final class Entry {

        public boolean enabled = true;
        public List<String> aliases;

        public Entry() {}

        public Entry(boolean enabled, List<String> aliases) {
            this.enabled = enabled;
            this.aliases = aliases == null ? new ArrayList<>() : new ArrayList<>(aliases);
        }

        public Entry(boolean enabled, String... aliases) {
            this(enabled, Arrays.asList(aliases));
        }
    }
}
