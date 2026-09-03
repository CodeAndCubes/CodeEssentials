package com.mrleonardos.codeessentials.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.command.CommandSender;
import com.mrleonardos.codecore.api.command.SenderKind;
import com.mrleonardos.codeessentials.internal.SharedSettings;
import com.mrleonardos.codeessentials.internal.store.EssentialsState;

class SenderSubjectsTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private final SenderSubjects subjects = new SenderSubjects(
        new NameResolver(EssentialsState::empty),
        new CorePermissions(SharedSettings::defaults, () -> null, LogManager.getLogger("CodeEssentialsTest")));

    @Test
    void aCommandBlockSignsItselfWithItsCoordinates() {
        assertEquals("commandblock@100,64,-30", SenderSubjects.actorOf(PlatformStubs.Sender.block(0, 100, 64, -30)));
    }

    @Test
    void aCommandBlockWithoutCoordinatesStaysJustACommandBlock() {
        assertEquals("commandblock", SenderSubjects.actorOf(PlatformStubs.Sender.of(SenderKind.COMMAND_BLOCK, "@")));
    }

    @Test
    void theOtherThreeKindsSignThemselvesAsBefore() {
        assertEquals("Steve", SenderSubjects.actorOf(PlatformStubs.Sender.player(STEVE, "Steve")));
        assertEquals("console", SenderSubjects.actorOf(PlatformStubs.Sender.of(SenderKind.CONSOLE, "Server")));
        assertEquals("rcon", SenderSubjects.actorOf(PlatformStubs.Sender.of(SenderKind.RCON, "Rcon")));
    }

    @Test
    void theSameSignatureComesOutOfAWholeCommand() {
        assertEquals(
            "commandblock@8,70,-4",
            subjects.actorOf(new StubContext(PlatformStubs.Sender.block(0, 8, 70, -4))));
    }

    @Test
    void aSenderWithoutAPlayerHasNoPositionAndThatIsTheAnswer() {
        CommandContext console = new StubContext(PlatformStubs.Sender.of(SenderKind.CONSOLE, "Server"));

        assertEquals(Optional.empty(), subjects.playerOf(console));
        assertEquals(Optional.empty(), subjects.positionOf(console), "у консоли координат не бывает");
    }

    /** Контекст команды на одном отправителе; sender() и player() уходят со сносом старых подписей ядра. */
    private static final class StubContext implements CommandContext {

        private final CommandSender caller;
        private final List<String> replies = new ArrayList<>();

        StubContext(CommandSender caller) {
            this.caller = caller;
        }

        @Override
        public CommandSender caller() {
            return caller;
        }

        @Override
        public ICommandSender sender() {
            return null;
        }

        @Override
        public EntityPlayerMP player() {
            return null;
        }

        @Override
        public <T> T get(String name) {
            throw new IllegalArgumentException(name);
        }

        @Override
        public <T> T getOrDefault(String name, T fallback) {
            return fallback;
        }

        @Override
        public boolean has(String name) {
            return false;
        }

        @Override
        public void reply(String translationKey, Object... arguments) {
            replies.add(translationKey);
        }

        @Override
        public void replyError(String translationKey, Object... arguments) {
            replies.add(translationKey);
        }
    }
}
