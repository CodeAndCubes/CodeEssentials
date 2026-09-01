package com.mrleonardos.codeessentials.internal.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.command.CommandNode;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;

class CommandRootsTest {

    private static final Logger LOG = LogManager.getLogger("codeessentials-test");

    @Test
    void theFreshFileHoldsEveryRootWithItsFactoryAliases() {
        CommandRoots book = new CommandRoots();

        assertEquals(
            CommandRoots.factoryAliases()
                .keySet(),
            book.commands.keySet());
        for (Map.Entry<String, CommandRoots.Entry> entry : book.commands.entrySet()) {
            assertTrue(entry.getValue().enabled, entry.getKey());
            assertEquals(
                CommandRoots.factoryAliases()
                    .get(entry.getKey()),
                entry.getValue().aliases,
                entry.getKey());
        }
        assertEquals(Collections.singletonList("tpyes"), book.commands.get(CommandRoots.TPACCEPT).aliases);
    }

    @Test
    void aRootTurnedOffIsNotRegisteredAtAll() {
        CommandRoots book = new CommandRoots();
        book.commands.get(CommandRoots.TP).enabled = false;

        List<CommandNode> chosen = book.chosen(roots(CommandRoots.HOME, CommandRoots.TP), LOG);

        assertEquals(Collections.singletonList(CommandRoots.HOME), names(chosen));
    }

    @Test
    void ownAliasesReplaceTheFactoryOnes() {
        CommandRoots book = new CommandRoots();
        book.commands.get(CommandRoots.TPACCEPT).aliases = new ArrayList<>(Collections.singletonList("да"));
        book.commands.get(CommandRoots.HOME).aliases = new ArrayList<>(Collections.singletonList("Дом"));

        List<CommandNode> chosen = book.chosen(roots(CommandRoots.HOME, CommandRoots.TPACCEPT), LOG);

        assertEquals(
            Collections.singletonList("дом"),
            chosen.get(0)
                .aliases());
        assertEquals(
            Collections.singletonList("да"),
            chosen.get(1)
                .aliases());
        assertFalse(
            chosen.get(1)
                .matches("tpyes"),
            "заводской алиас после замены больше не отвечает");
        assertTrue(
            chosen.get(1)
                .matches("да"));
    }

    @Test
    void anEmptyListLeavesTheRootWithoutAliases() {
        CommandRoots book = new CommandRoots();
        book.commands.get(CommandRoots.TPACCEPT).aliases = new ArrayList<>();

        List<CommandNode> chosen = book.chosen(roots(CommandRoots.TPACCEPT), LOG);

        assertTrue(
            chosen.get(0)
                .aliases()
                .isEmpty());
        assertTrue(
            chosen.get(0)
                .matches(CommandRoots.TPACCEPT));
    }

    @Test
    void aMissingAliasFieldFallsBackToTheFactoryList() {
        CommandRoots book = new CommandRoots();
        book.commands.get(CommandRoots.TPACCEPT).aliases = null;

        List<CommandNode> chosen = book.chosen(roots(CommandRoots.TPACCEPT), LOG);

        assertEquals(
            Collections.singletonList("tpyes"),
            chosen.get(0)
                .aliases());
    }

    @Test
    void aTypoInTheRootNameSwitchesOffNothing() {
        CommandRoots book = new CommandRoots();
        book.commands.put("hoem", new CommandRoots.Entry(false));

        List<String> unknown = book.unknownRoots();
        List<CommandNode> chosen = book.chosen(roots(CommandRoots.HOME, CommandRoots.HOMES), LOG);

        assertEquals(Collections.singletonList("hoem"), unknown);
        assertEquals(Arrays.asList(CommandRoots.HOME, CommandRoots.HOMES), names(chosen));
    }

    @Test
    void aRootMissingFromTheFileIsWrittenBackWithFactoryValues() {
        CommandRoots book = new CommandRoots();
        book.commands.remove(CommandRoots.HOME);

        List<CommandNode> chosen = book.chosen(roots(CommandRoots.HOME), LOG);

        assertEquals(Collections.singletonList(CommandRoots.HOME), names(chosen));
        assertTrue(book.commands.get(CommandRoots.HOME).enabled);
    }

    @Test
    void theFileSitsBesideTheOtherSettings() {
        assertEquals(
            EssentialsSettings.COMMANDS_FILE,
            CommandRoots.spec()
                .name());
        assertEquals(
            EssentialsSettings.MODID,
            CommandRoots.spec()
                .modid());
    }

    private static List<CommandNode> roots(String... names) {
        List<CommandNode> roots = new ArrayList<>();
        for (String name : names) {
            roots.add(CommandNode.literal(name));
        }
        return roots;
    }

    private static List<String> names(List<CommandNode> nodes) {
        List<String> names = new ArrayList<>();
        for (CommandNode node : nodes) {
            names.add(node.name());
        }
        return names;
    }
}
