package com.mrleonardos.codeessentials.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.internal.EssentialsSettings;

class CorePermissionsTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final Logger LOG = LogManager.getLogger("codeessentials-test");
    private static final String NODE = "codeessentials.warp.go.shop";

    private final EssentialsSettings settings = new EssentialsSettings();
    private final CorePermissions permissions = new CorePermissions(() -> settings, () -> null, LOG);

    @Test
    void withoutAPermissionServiceBothWaysOfAskingAnswerNo() {
        assertFalse(permissions.has(STEVE, NODE), "проверка по uuid закрыта");
        assertFalse(permissions.allowed(new Console(), NODE), "проверка по отправителю обязана отвечать так же");
    }

    @Test
    void aRefusalIsExplainedAtDebugOnlyWithTheFlagOn() {
        List<String> quiet = record(() -> permissions.has(STEVE, NODE));

        assertTrue(
            quiet.stream()
                .noneMatch(line -> line.contains(NODE)),
            () -> "выключенный audit.logChecks про ноды молчит: " + quiet);

        settings.audit.logChecks = true;
        List<String> lines = record(() -> permissions.has(STEVE, NODE));

        assertTrue(
            lines.stream()
                .anyMatch(line -> line.contains(NODE) && line.contains(STEVE.toString())),
            () -> "включённый audit.logChecks обязан объяснить отказ: " + lines);
    }

    private static List<String> record(Runnable work) {
        org.apache.logging.log4j.core.Logger held = (org.apache.logging.log4j.core.Logger) LOG;
        CapturingAppender appender = new CapturingAppender();
        Level before = held.getLevel();
        held.addAppender(appender);
        held.setLevel(Level.DEBUG);
        try {
            work.run();
        } finally {
            held.removeAppender(appender);
            held.setLevel(before);
        }
        return appender.lines;
    }

    /** Подставной приёмник debug-записей: ловит отформатированные сообщения. */
    private static final class CapturingAppender extends AbstractAppender {

        private final List<String> lines = new ArrayList<>();

        CapturingAppender() {
            super("capturing-rights", null, null, true);
            start();
        }

        @Override
        public void append(LogEvent event) {
            lines.add(
                event.getMessage()
                    .getFormattedMessage());
        }
    }

    private static final class Console implements ICommandSender {

        @Override
        public String getCommandSenderName() {
            return "console";
        }

        @Override
        public IChatComponent func_145748_c_() {
            return null;
        }

        @Override
        public void addChatMessage(IChatComponent message) {}

        @Override
        public boolean canCommandSenderUseCommand(int level, String command) {
            return false;
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return null;
        }

        @Override
        public World getEntityWorld() {
            return null;
        }
    }
}
