package com.mrleonardos.codeessentials.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeessentials.TestConfigs;
import com.mrleonardos.codeessentials.api.event.EssentialsEvents;
import com.mrleonardos.codeessentials.api.event.KitEvents;
import com.mrleonardos.codeessentials.api.manage.KitService;
import com.mrleonardos.codeessentials.api.model.KitDefinition;
import com.mrleonardos.codeessentials.api.model.KitItem;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.internal.SharedSettings;
import com.mrleonardos.codeessentials.internal.engine.PlayerRights;
import com.mrleonardos.codeessentials.internal.store.EssentialsState;
import com.mrleonardos.codeessentials.internal.store.SingleWriter;

class KitServiceImplTest {

    private static final Logger TEST_LOG = LogManager.getLogger("codeessentials-test");
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final KitItem BREAD = KitItem.of("minecraft:bread", 16);

    private final AtomicLong clock = new AtomicLong(1_000_000L);

    @Test
    void aKitIsWrittenAndReadBack(@TempDir Path root) {
        KitService kits = kitService(
            root,
            new Writer(),
            new ServiceTestStubs.Hands(Optional.empty()),
            new Ticks(),
            null);

        kits.define(
            KitDefinition.named("starter")
                .once()
                .cooldown(30)
                .slot(0, BREAD)
                .slot(39, KitItem.of("minecraft:iron_helmet", 1))
                .build(),
            "Steve");

        KitDefinition read = kits.kit("starter")
            .orElseThrow(() -> new AssertionError("kit is gone"));
        assertTrue(read.once());
        assertEquals(30, read.cooldownSeconds());
        assertEquals(Optional.of(BREAD), read.slot(0));
        assertEquals(Optional.of(KitItem.of("minecraft:iron_helmet", 1)), read.slot(39));
        assertEquals(
            Arrays.asList("starter"),
            new ArrayList<>(
                kits.kits()
                    .keySet()));
    }

    @Test
    void anEmptyKitIsRefusedAtTheOnlyDoor(@TempDir Path root) {
        KitService kits = kitService(
            root,
            new Writer(),
            new ServiceTestStubs.Hands(Optional.empty()),
            new Ticks(),
            null);

        StoreResult written = kits.define(
            KitDefinition.named("starter")
                .build(),
            "Steve");

        assertEquals(
            StoreResult.Failure.INVALID_VALUE,
            written.failure()
                .orElse(null));
        assertFalse(
            kits.kit("starter")
                .isPresent());
    }

    @Test
    void aBrokenSaveKeepsThePreviousKit(@TempDir Path root) {
        ConfigFile<KitsFile> file = file(root);
        KitService kits = kitService(
            root,
            new Writer(),
            new ServiceTestStubs.Hands(Optional.empty()),
            new Ticks(),
            KitDefinition.named("starter")
                .slot(0, BREAD)
                .build());
        ServiceTestStubs.Files<KitsFile> breaking = new ServiceTestStubs.Files<>(file.get());
        breaking.failOnSave = new IllegalStateException("disk is gone");
        KitServiceImpl broken = new KitServiceImpl(
            SharedSettings::defaults,
            breaking,
            new ServiceTestStubs.Hands(Optional.empty()),
            events(),
            noRights(),
            new Writer(),
            new Ticks(),
            clock::get,
            TEST_LOG);

        StoreResult written = broken.define(
            KitDefinition.named("starter")
                .slot(0, KitItem.of("minecraft:apple", 3))
                .build(),
            "Steve");

        assertEquals(
            StoreResult.Failure.PROVIDER_FAILED,
            written.failure()
                .orElse(null));
        assertEquals(
            Optional.of(BREAD),
            kits.kit("starter")
                .orElseThrow(() -> new AssertionError("kit is gone"))
                .slot(0));
    }

    @Test
    void deletionAnswersAboutMissingAndExistingKits(@TempDir Path root) {
        KitService kits = kitService(
            root,
            new Writer(),
            new ServiceTestStubs.Hands(Optional.empty()),
            new Ticks(),
            KitDefinition.named("starter")
                .slot(0, BREAD)
                .build());

        assertEquals(
            StoreResult.Failure.NOT_FOUND,
            kits.delete("nope", "Steve")
                .failure()
                .orElse(null));
        assertTrue(
            kits.delete("starter", "Steve")
                .successful());
        assertFalse(
            kits.kit("starter")
                .isPresent());
    }

    @Test
    void anUnknownKitAnswersUnknown(@TempDir Path root) {
        KitService kits = kitService(
            root,
            new Writer(),
            new ServiceTestStubs.Hands(Optional.empty()),
            new Ticks(),
            null);

        assertEquals(
            KitService.Claim.Outcome.UNKNOWN,
            kits.claim(STEVE, "starter", "Steve")
                .outcome());
    }

    @Test
    void aClaimArrivesAndSetsBothMarks(@TempDir Path root) {
        Writer writer = new Writer();
        ServiceTestStubs.Hands hands = new ServiceTestStubs.Hands(Optional.of(slots()));
        KitService kits = kitService(
            root,
            writer,
            hands,
            new Ticks(),
            KitDefinition.named("starter")
                .once()
                .cooldown(30)
                .slot(0, BREAD)
                .build());

        KitService.Claim answer = kits.claim(STEVE, "starter", "Steve");

        assertEquals(KitService.Claim.Outcome.DELIVERED, answer.outcome());
        assertEquals(16, answer.delivered());
        assertTrue(hands.dressed);
        PlayerRecord record = writer.held.player(STEVE);
        assertTrue(record.hasKitClaim("starter"));
        assertTrue(
            record.kitBuffer("starter")
                .isEmpty());
        assertEquals(1, writer.batches.size());
        assertEquals(
            "kit.starter",
            writer.batches.get(0)
                .changes()
                .get(1)
                .cooldownKey()
                .orElse(""));
    }

    @Test
    void aClaimWithNowhereToArriveStillCounts(@TempDir Path root) {
        Writer writer = new Writer();
        KitService kits = kitService(
            root,
            writer,
            new ServiceTestStubs.Hands(Optional.empty()),
            new Ticks(),
            KitDefinition.named("starter")
                .once()
                .slot(0, BREAD)
                .build());

        KitService.Claim answer = kits.claim(STEVE, "starter", "Steve");

        assertEquals(KitService.Claim.Outcome.STASHED, answer.outcome());
        assertEquals(0, answer.delivered());
        assertEquals(16, answer.buffered());
        assertTrue(
            writer.held.player(STEVE)
                .hasKitClaim("starter"));
        assertEquals(
            Arrays.asList(BREAD),
            writer.held.player(STEVE)
                .kitBuffer("starter"));
    }

    @Test
    void aClaimOfAnOfflinePlayerIsStashedWithTheMarks(@TempDir Path root) {
        Writer writer = new Writer();
        KitService kits = kitService(
            root,
            writer,
            new ServiceTestStubs.Hands(Optional.empty()),
            new Ticks(),
            KitDefinition.named(
                ALEX.toString()
                    .substring(0, 0) + "starter")
                .slot(0, BREAD)
                .build());

        KitService.Claim answer = kits.claim(ALEX, "starter", "Steve");

        assertEquals(KitService.Claim.Outcome.STASHED, answer.outcome());
        assertTrue(
            writer.held.player(ALEX)
                .hasKitClaim("starter"));
        assertEquals(16, kits.pendingItems(ALEX));
    }

    @Test
    void theDebtArrivesWithoutSpendingTheClaim(@TempDir Path root) {
        Writer writer = new Writer();
        writer.held = EssentialsState.empty()
            .withPlayer(
                PlayerRecord.empty(STEVE, "Steve")
                    .withKitBuffer("starter", Arrays.asList(KitItem.of("minecraft:apple", 5))))
            .withCooldown(STEVE, "kit.starter", clock.get() + 60_000L);
        ServiceTestStubs.Hands hands = new ServiceTestStubs.Hands(Optional.of(slots()));
        KitService kits = kitService(
            root,
            writer,
            hands,
            new Ticks(),
            KitDefinition.named("starter")
                .once()
                .cooldown(60)
                .slot(0, BREAD)
                .build());

        KitService.Claim answer = kits.claim(STEVE, "starter", "Steve");

        assertEquals(KitService.Claim.Outcome.COOLDOWN, answer.outcome());
        assertEquals(5, answer.delivered(), "долг доехал");
        assertEquals(0, answer.buffered(), "новое получение не выдано");
        assertTrue(answer.waitMillis() > 0L);
        assertTrue(hands.dressed);
        assertFalse(
            writer.held.player(STEVE)
                .hasKitClaim("starter"),
            "получение не состоялось, отметки нет");
    }

    @Test
    void aTakenOneTimeKitAnswersTakenButTheDebtArrives(@TempDir Path root) {
        Writer writer = new Writer();
        writer.held = EssentialsState.empty()
            .withPlayer(
                PlayerRecord.empty(STEVE, "Steve")
                    .withKitClaim("starter")
                    .withKitBuffer("starter", Arrays.asList(KitItem.of("minecraft:apple", 5))));
        KitService kits = kitService(
            root,
            writer,
            new ServiceTestStubs.Hands(Optional.of(slots())),
            new Ticks(),
            KitDefinition.named("starter")
                .once()
                .slot(0, BREAD)
                .build());

        KitService.Claim answer = kits.claim(STEVE, "starter", "Steve");

        assertEquals(KitService.Claim.Outcome.TAKEN, answer.outcome());
        assertEquals(5, answer.delivered());
        assertEquals(0, answer.buffered());
    }

    @Test
    void aVetoKeepsTheMarksAwayButHandsOutTheDebt(@TempDir Path root) {
        Writer writer = new Writer();
        writer.held = EssentialsState.empty()
            .withPlayer(
                PlayerRecord.empty(STEVE, "Steve")
                    .withKitBuffer("starter", Arrays.asList(KitItem.of("minecraft:apple", 5))));
        KitRegistry registry = new KitRegistry();
        registry.register(1, new KitEvents.Listener() {

            @Override
            public EssentialsEvents.Kind kind() {
                return EssentialsEvents.Kind.ENFORCE;
            }

            @Override
            public EssentialsEvents.Decision beforeClaim(UUID player, String kit) {
                return EssentialsEvents.Decision.deny("the region is closed");
            }
        });
        KitService kits = new KitServiceImpl(
            SharedSettings::defaults,
            file(root),
            new ServiceTestStubs.Hands(Optional.of(slots())),
            () -> registry,
            noRights(),
            writer,
            new Ticks(),
            clock::get,
            TEST_LOG);
        kits.define(
            KitDefinition.named("starter")
                .once()
                .slot(0, BREAD)
                .build(),
            "Steve");

        KitService.Claim answer = kits.claim(STEVE, "starter", "Steve");

        assertEquals(KitService.Claim.Outcome.VETOED, answer.outcome());
        assertEquals(Optional.of("the region is closed"), answer.reason());
        assertEquals(5, answer.delivered());
        assertFalse(
            writer.held.player(STEVE)
                .hasKitClaim("starter"));
    }

    @Test
    void aBrokenDressLosesNothing(@TempDir Path root) {
        Writer writer = new Writer();
        ServiceTestStubs.Hands hands = new ServiceTestStubs.Hands(Optional.of(slots()));
        hands.dressable = false;
        KitService kits = kitService(
            root,
            writer,
            hands,
            new Ticks(),
            KitDefinition.named("starter")
                .slot(0, BREAD)
                .build());

        KitService.Claim answer = kits.claim(STEVE, "starter", "Steve");

        assertEquals(KitService.Claim.Outcome.STASHED, answer.outcome());
        assertEquals(
            Arrays.asList(BREAD),
            writer.held.player(STEVE)
                .kitBuffer("starter"));
    }

    @Test
    void zeroCooldownIgnoresTheStaleStamp(@TempDir Path root) {
        Writer writer = new Writer();
        writer.held = EssentialsState.empty()
            .withCooldown(STEVE, "kit.starter", clock.get() + 60_000L);
        KitService kits = kitService(
            root,
            writer,
            new ServiceTestStubs.Hands(Optional.of(slots())),
            new Ticks(),
            KitDefinition.named("starter")
                .slot(0, BREAD)
                .build());

        assertEquals(
            KitService.Claim.Outcome.DELIVERED,
            kits.claim(STEVE, "starter", "Steve")
                .outcome());
    }

    @Test
    void theBypassNodeSkipsThePauseButNotTheOneTimeMark(@TempDir Path root) {
        Writer writer = new Writer();
        writer.held = EssentialsState.empty()
            .withCooldown(STEVE, "kit.starter", clock.get() + 60_000L)
            .withPlayer(
                PlayerRecord.empty(STEVE, "Steve")
                    .withKitClaim("starter"));
        KitService kits = new KitServiceImpl(
            SharedSettings::defaults,
            file(root),
            new ServiceTestStubs.Hands(Optional.of(slots())),
            events(),
            rights("codeessentials.bypass.cooldown"),
            writer,
            new Ticks(),
            clock::get,
            TEST_LOG);
        kits.define(
            KitDefinition.named("starter")
                .once()
                .cooldown(60)
                .slot(0, BREAD)
                .build(),
            "Steve");

        assertEquals(
            KitService.Claim.Outcome.TAKEN,
            kits.claim(STEVE, "starter", "Steve")
                .outcome());

        Writer fresh = new Writer();
        fresh.held = EssentialsState.empty()
            .withCooldown(STEVE, "kit.starter", clock.get() + 60_000L);
        KitService noMark = new KitServiceImpl(
            SharedSettings::defaults,
            file(root),
            new ServiceTestStubs.Hands(Optional.of(slots())),
            events(),
            rights("codeessentials.bypass.cooldown"),
            fresh,
            new Ticks(),
            clock::get,
            TEST_LOG);

        assertEquals(
            KitService.Claim.Outcome.DELIVERED,
            noMark.claim(STEVE, "starter", "Steve")
                .outcome());
    }

    @Test
    void aClaimTheMainThreadNeverTookAnswersUnknown(@TempDir Path root) {
        KitService kits = new KitServiceImpl(
            SharedSettings::defaults,
            file(root),
            new ServiceTestStubs.Hands(Optional.empty()),
            events(),
            noRights(),
            new Writer(),
            dropped(),
            clock::get,
            TEST_LOG);

        assertEquals(
            KitService.Claim.Outcome.UNKNOWN,
            kits.claim(STEVE, "starter", "Steve")
                .outcome());
    }

    @Test
    void pendingCountsAndMarksAreReadFromTheRecord(@TempDir Path root) {
        Writer writer = new Writer();
        writer.held = EssentialsState.empty()
            .withPlayer(
                PlayerRecord.empty(STEVE, "Steve")
                    .withKitBuffer("starter", Arrays.asList(KitItem.of("minecraft:bread", 8)))
                    .withKitBuffer("vip", Arrays.asList(KitItem.of("minecraft:apple", 2)))
                    .withKitClaim("vip"))
            .withCooldown(STEVE, "kit.starter", clock.get() + 5_000L);
        KitService kits = kitService(root, writer, new ServiceTestStubs.Hands(Optional.empty()), new Ticks(), null);

        assertEquals(10, kits.pendingItems(STEVE));
        assertEquals(
            Integer.valueOf(8),
            kits.pendingByKit(STEVE)
                .get("starter"));
        assertTrue(kits.taken(STEVE, "vip"));
        assertFalse(kits.taken(STEVE, "starter"));
        assertTrue(kits.cooldownLeft(STEVE, "starter") > 0L);
        assertEquals(0L, kits.cooldownLeft(STEVE, "vip"));
    }

    private KitService kitService(Path root, Writer writer, ServiceTestStubs.Hands hands, Scheduler ticks,
        KitDefinition kit) {
        KitService kits = new KitServiceImpl(
            SharedSettings::defaults,
            file(root),
            hands,
            events(),
            noRights(),
            writer,
            ticks,
            clock::get,
            TEST_LOG);
        if (kit != null) {
            kits.define(kit, "Steve");
        }
        return kits;
    }

    private static Scheduler dropped() {
        return new Scheduler() {

            @Override
            public void onMainThread(Runnable task) {}

            @Override
            public void afterTicks(int ticks, Runnable task) {}
        };
    }

    private static java.util.function.Supplier<KitEvents> events() {
        KitRegistry registry = new KitRegistry();
        return () -> registry;
    }

    private static ConfigFile<KitsFile> file(Path root) {
        return TestConfigs.of(root)
            .open(KitsFile.spec());
    }

    private static PlayerRights noRights() {
        return rights();
    }

    private static PlayerRights rights(String... nodes) {
        Set<String> held = new LinkedHashSet<>(Arrays.asList(nodes));
        return new PlayerRights() {

            @Override
            public boolean has(UUID player, String node) {
                return held.contains(node);
            }

            @Override
            public OptionalInt number(UUID player, String key) {
                return OptionalInt.empty();
            }
        };
    }

    private static KitItem[] slots() {
        return new KitItem[KitDefinition.SLOTS];
    }

    private static final class Ticks implements Scheduler {

        @Override
        public void onMainThread(Runnable task) {
            task.run();
        }

        @Override
        public void afterTicks(int ticks, Runnable task) {}
    }

    private static final class Writer implements SingleWriter {

        EssentialsState held = EssentialsState.empty();
        final List<ChangeBatch> batches = new ArrayList<>();

        @Override
        public EssentialsState state() {
            return held;
        }

        @Override
        public StoreResult commit(EssentialsState next, ChangeBatch batch) {
            held = next;
            batches.add(batch);
            return StoreResult.success();
        }

        @Override
        public void flush() {}
    }

    private static final class KitRegistry implements KitEvents {

        final List<Listener> listeners = new ArrayList<>();

        @Override
        public void register(int priority, Listener listener) {
            listeners.add(listener);
        }

        @Override
        public void unregister(Listener listener) {
            listeners.remove(listener);
        }

        @Override
        public List<Listener> listeners() {
            return listeners;
        }
    }
}
