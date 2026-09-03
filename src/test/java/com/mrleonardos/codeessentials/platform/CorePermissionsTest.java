package com.mrleonardos.codeessentials.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.adapter.PermissionCapabilities;
import com.mrleonardos.codecore.api.adapter.RoleCapability;
import com.mrleonardos.codecore.api.command.SenderKind;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.SharedFixtures;
import com.mrleonardos.codeessentials.internal.SharedSettings;

class CorePermissionsTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final Logger LOG = LogManager.getLogger("codeessentials-test");
    private static final String NODE = "codeessentials.warp.go.shop";

    private SharedSettings shared = SharedFixtures.audit(true, false);
    private final CorePermissions permissions = new CorePermissions(() -> shared, () -> null, LOG);

    @Test
    void withoutAPermissionServiceBothWaysOfAskingAnswerNo() {
        assertFalse(permissions.has(STEVE, NODE), "проверка по uuid закрыта");
        assertFalse(
            permissions.allowed(PlatformStubs.Sender.of(SenderKind.CONSOLE, "Server"), NODE),
            "проверка по отправителю обязана отвечать так же");
    }

    @Test
    void aRefusalIsExplainedAtDebugOnlyWithTheFlagOn() {
        List<String> quiet = record(() -> permissions.has(STEVE, NODE));

        assertTrue(
            quiet.stream()
                .noneMatch(line -> line.contains(NODE)),
            () -> "выключенный audit.logChecks про ноды молчит: " + quiet);

        shared = SharedFixtures.audit(true, true);
        List<String> lines = record(() -> permissions.has(STEVE, NODE));

        assertTrue(
            lines.stream()
                .anyMatch(line -> line.contains(NODE) && line.contains(STEVE.toString())),
            () -> "включённый audit.logChecks обязан объяснить отказ: " + lines);
    }

    @Test
    void withoutTheMetaAbilityPersonalNumbersAreNotAskedAboutAtAll() {
        CountingMeta service = new CountingMeta("5");
        CorePermissions rights = new CorePermissions(() -> shared, () -> service, LOG);

        assertEquals(OptionalInt.of(5), rights.number(STEVE, EssentialsSettings.META_MAX_HOMES));
        assertEquals(1, service.asked, "умение на месте, значение спрашивают у владельца роли");

        service.answer = null;
        assertEquals(
            OptionalInt.empty(),
            rights.number(STEVE, EssentialsSettings.META_MAX_HOMES),
            "значение не задано: спросили и получили пустоту");
        assertEquals(2, service.asked);

        List<String> lines = record(() -> rights.reviewMeta(Collections.singleton(PermissionCapabilities.META)));
        service.answer = "5";

        assertEquals(
            OptionalInt.empty(),
            rights.number(STEVE, EssentialsSettings.META_MAX_HOMES),
            "умения нет: личный лимит выключен, действует заводской");
        assertEquals(2, service.asked, "умения нет, значит владельца роли не спрашивают вовсе");
        assertEquals(
            1,
            lines.stream()
                .filter(line -> line.contains(EssentialsSettings.META_MAX_HOMES))
                .count(),
            () -> "об отключении говорится один раз при старте: " + lines);
    }

    @Test
    void aRoleThatKeepsTheMetaAbilityIsLeftAlone() {
        CountingMeta service = new CountingMeta("3");
        CorePermissions rights = new CorePermissions(() -> shared, () -> service, LOG);

        List<String> lines = record(() -> rights.reviewMeta(Collections.<RoleCapability>emptySet()));

        assertEquals(OptionalInt.of(3), rights.number(STEVE, EssentialsSettings.META_MAX_HOMES));
        assertTrue(
            lines.stream()
                .noneMatch(line -> line.contains(EssentialsSettings.META_MAX_HOMES)),
            () -> "умение на месте, отключать нечего и говорить не о чем: " + lines);
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

    /** Владелец роли прав, который считает, сколько раз у него спросили мету. */
    private static final class CountingMeta implements PermissionService {

        private String answer;
        private int asked;

        CountingMeta(String answer) {
            this.answer = answer;
        }

        @Override
        public boolean has(UUID player, String node) {
            return false;
        }

        @Override
        public String group(UUID player) {
            return "";
        }

        @Override
        public String meta(UUID player, String key, String fallback) {
            asked++;
            return answer == null ? fallback : answer;
        }
    }
}
