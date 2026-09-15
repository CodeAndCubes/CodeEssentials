package com.mrleonardos.codeessentials.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotLimits;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;

class PolicyChoiceTest {

    private static final Point SHOP = Point.of(0, 1.5D, 64.0D, 2.5D);
    private static final SafeSpotLimits LIMITS = SafeSpotLimits.defaults();

    private final Logger log = LogManager.getLogger("codeessentials-test");

    @Test
    void anUnknownNameSwitchesTheSearchOff() {
        PolicyChoice choice = new PolicyChoice(() -> "nowhere", id -> Optional.empty(), log);
        EngineFixtures.FakeWorld world = new EngineFixtures.FakeWorld(0);
        world.floor(1, 1, 1);

        SafeSpotResult found = choice.named()
            .find(world, SHOP, LIMITS);

        assertTrue(found.found(), "поиск выключен, точка проходит как есть");
        assertEquals(
            SHOP,
            found.spot()
                .get(),
            "телепорт идёт по прямым координатам");
        assertEquals(
            "nowhere",
            choice.named()
                .id());
    }

    @Test
    void anUnknownNameIsToldOnceUntilTheSettingsAreReread() {
        PolicyChoice choice = new PolicyChoice(() -> "nowhere", id -> Optional.empty(), log);

        int told = record(() -> {
            choice.named();
            choice.named();
        }, "nowhere");

        assertEquals(1, told, "строка о незнакомом имени звучит один раз на выбор");
        choice.reset();
        told = record(() -> choice.named(), "nowhere");
        assertEquals(1, told, "после перечитывания настроек имя названо снова");
    }

    @Test
    void theNameIsResolvedOnceAndResetAppliesTheNewOne() {
        AtomicInteger asked = new AtomicInteger();
        PolicyChoice choice = new PolicyChoice(() -> "builtin", id -> {
            asked.incrementAndGet();
            return Optional.of(new SafeSpotFinder());
        }, log);
        EngineFixtures.FakeWorld world = new EngineFixtures.FakeWorld(0);
        world.floor(1, 1, 1);

        choice.named();
        choice.named();
        assertEquals(1, asked.get(), "имя смотрят в реестре один раз, а не на каждом переносе");

        choice.reset();
        choice.named();
        assertEquals(2, asked.get(), "перечитывание настроек заставляет выбрать заново");

        SafeSpotResult found = choice.named()
            .find(world, SHOP, LIMITS);
        assertTrue(found.found());
    }

    private int record(Runnable work, String word) {
        org.apache.logging.log4j.core.Logger held = (org.apache.logging.log4j.core.Logger) log;
        CountingAppender appender = new CountingAppender(word);
        Level before = held.getLevel();
        held.addAppender(appender);
        held.setLevel(Level.WARN);
        try {
            work.run();
        } finally {
            held.removeAppender(appender);
            held.setLevel(before);
        }
        return appender.seen;
    }

    private static final class CountingAppender extends AbstractAppender {

        private final String word;
        private int seen;

        CountingAppender(String word) {
            super("counting-" + word, null, null, true);
            this.word = word;
            start();
        }

        @Override
        public void append(LogEvent event) {
            if (event.getMessage()
                .getFormattedMessage()
                .contains(word)) {
                seen++;
            }
        }
    }
}
