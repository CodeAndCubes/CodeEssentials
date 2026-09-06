package com.mrleonardos.codeessentials.internal.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeessentials.api.event.EssentialsEvents;
import com.mrleonardos.codeessentials.api.event.KitEvents;
import com.mrleonardos.codeessentials.api.manage.KitService;
import com.mrleonardos.codeessentials.api.model.KitDefinition;
import com.mrleonardos.codeessentials.api.model.KitItem;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.internal.SharedSettings;
import com.mrleonardos.codeessentials.internal.command.Nodes;
import com.mrleonardos.codeessentials.internal.engine.PlayerRights;
import com.mrleonardos.codeessentials.internal.kits.KitDelivery;
import com.mrleonardos.codeessentials.internal.kits.KitHands;
import com.mrleonardos.codeessentials.internal.store.SingleWriter;

public final class KitServiceImpl implements KitService {

    public static final String COOLDOWN_KEY_PREFIX = "kit.";
    public static final long MILLIS_PER_SECOND = 1000L;

    private final Supplier<SharedSettings> shared;
    private final ConfigFile<KitsFile> file;
    private final KitHands hands;
    private final Supplier<KitEvents> events;
    private final PlayerRights rights;
    private final SingleWriter writer;
    private final Scheduler scheduler;
    private final LongSupplier clock;
    private final Logger log;

    public KitServiceImpl(Supplier<SharedSettings> shared, ConfigFile<KitsFile> file, KitHands hands,
        Supplier<KitEvents> events, PlayerRights rights, SingleWriter writer, Scheduler scheduler, LongSupplier clock,
        Logger log) {
        this.shared = shared;
        this.file = file;
        this.hands = hands;
        this.events = events;
        this.rights = rights;
        this.writer = writer;
        this.scheduler = scheduler;
        this.clock = clock;
        this.log = log;
    }

    @Override
    public Map<String, KitDefinition> kits() {
        if (!file.loaded()) {
            return Collections.emptyMap();
        }
        Map<String, KitDefinition> decoded = new TreeMap<>();
        for (Map.Entry<String, KitsFile.Kit> entry : file.get().kits.entrySet()) {
            KitDefinition kit = decode(entry.getKey(), entry.getValue());
            if (kit != null) {
                decoded.put(kit.name(), kit);
            }
        }
        return Collections.unmodifiableMap(decoded);
    }

    @Override
    public Optional<KitDefinition> kit(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(kits().get(lower(name)));
    }

    @Override
    public StoreResult define(KitDefinition kit, String actor) {
        if (kit == null || actor == null) {
            return StoreResult.failure(StoreResult.Failure.INVALID_VALUE, "kit and actor are required");
        }
        if (kit.isEmpty()) {
            return StoreResult.failure(StoreResult.Failure.INVALID_VALUE, "kit holds no items");
        }
        if (!file.loaded()) {
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, KitsFile.FILE);
        }
        KitsFile stored = file.get();
        KitsFile.Kit previous = stored.kits.get(kit.name());
        boolean overwrite = previous != null;
        stored.kits.put(kit.name(), encode(kit));
        try {
            file.save();
        } catch (RuntimeException broken) {
            restore(stored, kit.name(), previous);
            log.warn("{} was not written, nothing changed", KitsFile.FILE_NAME, broken);
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, String.valueOf(broken.getMessage()));
        }
        if (shared.get()
            .logChanges()) {
            log.info(
                "{} {} kit {} with {} slot(s), once {}, cooldown {}s",
                actor,
                overwrite ? "rewrote" : "created",
                kit.name(),
                Integer.valueOf(kit.size()),
                Boolean.valueOf(kit.once()),
                Integer.valueOf(kit.cooldownSeconds()));
        }
        return StoreResult.success();
    }

    @Override
    public StoreResult delete(String name, String actor) {
        if (name == null || actor == null) {
            return StoreResult.failure(StoreResult.Failure.INVALID_VALUE, "name and actor are required");
        }
        if (!file.loaded()) {
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, KitsFile.FILE);
        }
        String key = lower(name);
        KitsFile held = file.get();
        KitsFile.Kit previous = held.kits.get(key);
        if (previous == null) {
            return StoreResult.failure(StoreResult.Failure.NOT_FOUND, name);
        }
        held.kits.remove(key);
        try {
            file.save();
        } catch (RuntimeException broken) {
            restore(held, key, previous);
            log.warn("{} was not written, nothing changed", KitsFile.FILE_NAME, broken);
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, String.valueOf(broken.getMessage()));
        }
        if (shared.get()
            .logChanges()) {
            log.info("{} deleted kit {}", actor, key);
        }
        return StoreResult.success();
    }

    @Override
    public int pendingItems(UUID player) {
        int count = 0;
        for (List<KitItem> items : record(player).kitBuffer()
            .values()) {
            count += count(items);
        }
        return count;
    }

    @Override
    public Map<String, Integer> pendingByKit(UUID player) {
        Map<String, Integer> pending = new TreeMap<>();
        for (Map.Entry<String, List<KitItem>> entry : record(player).kitBuffer()
            .entrySet()) {
            pending.put(entry.getKey(), Integer.valueOf(count(entry.getValue())));
        }
        return pending;
    }

    @Override
    public Claim claim(UUID player, String kit, String actor) {
        if (player == null || kit == null || actor == null) {
            return Claim.unknown();
        }
        String key = lower(kit);
        return onMainThread(() -> deliver(player, key, actor));
    }

    @Override
    public boolean taken(UUID player, String kitName) {
        return record(player).hasKitClaim(lower(kitName));
    }

    @Override
    public long cooldownLeft(UUID player, String kitName) {
        String key = lower(kitName);
        long expiresAt = writer.state()
            .cooldown(player, cooldownKey(key));
        return Math.max(0L, expiresAt - clock.getAsLong());
    }

    private Claim deliver(UUID player, String key, String actor) {
        KitDefinition kit = kits().get(key);
        if (kit == null) {
            return Claim.unknown();
        }
        PlayerRecord held = record(player);
        List<KitItem> debt = new ArrayList<>(held.kitBuffer(key));
        String veto = vetoOf(player, key);
        boolean taken = veto == null && kit.once() && held.hasKitClaim(key);
        long wait = veto == null && !taken ? cooldownLeft(player, key, kit) : 0L;
        boolean granted = veto == null && !taken && wait == 0L;
        Claim refused = refusedOf(veto, taken, wait, count(debt));
        Optional<KitItem[]> worn = hands.worn(player);
        if (worn.isPresent()) {
            KitDefinition granting = granted ? kit : emptyOf(key);
            KitDelivery report = KitDelivery.deliver(granting, debt, worn.get(), hands.stacking());
            if (hands.dress(player, report.slots())) {
                return settled(player, key, kit, granted, report, held, actor, refused);
            }
            log.warn("Kit {} could not be dressed on {}, every item waits in the buffer", key, player);
        }
        if (!granted) {
            return refused;
        }
        return stashed(player, key, kit, debt, held, actor);
    }

    private Claim settled(UUID player, String key, KitDefinition kit, boolean granted, KitDelivery report,
        PlayerRecord held, String actor, Claim refused) {
        PlayerRecord next = granted ? held.withKitBuffer(key, report.pending())
            .withKitClaim(key) : held.withKitBuffer(key, report.pending());
        if (!commit(next, player, key, kit, granted).successful()) {
            return Claim.unknown();
        }
        if (shared.get()
            .logChanges()) {
            log.info(
                "{} claimed kit {} of {}: {} arrived, {} stashed, {} still pending",
                actor,
                key,
                player,
                Integer.valueOf(report.delivered()),
                Integer.valueOf(report.buffered()),
                Integer.valueOf(count(report.pending())));
        }
        announce(player, key, report.delivered(), report.buffered());
        if (!granted) {
            return withCounts(refused, report.delivered(), count(report.pending()));
        }
        return Claim.granted(
            report.delivered() > 0 ? Claim.Outcome.DELIVERED : Claim.Outcome.STASHED,
            report.delivered(),
            report.buffered(),
            count(report.pending()));
    }

    private Claim stashed(UUID player, String key, KitDefinition kit, List<KitItem> debt, PlayerRecord held,
        String actor) {
        List<KitItem> pending = new ArrayList<>(debt);
        for (KitItem item : kit.slots()
            .values()) {
            KitDelivery.setAside(pending, item);
        }
        PlayerRecord next = held.withKitBuffer(key, pending)
            .withKitClaim(key);
        if (!commit(next, player, key, kit, true).successful()) {
            return Claim.unknown();
        }
        int buffered = count(pending) - count(debt);
        if (shared.get()
            .logChanges()) {
            log.info(
                "{} claimed kit {} of {} into the buffer: {} stashed",
                actor,
                key,
                player,
                Integer.valueOf(count(pending)));
        }
        announce(player, key, 0, buffered);
        return Claim.granted(Claim.Outcome.STASHED, 0, buffered, count(pending));
    }

    private StoreResult commit(PlayerRecord next, UUID player, String key, KitDefinition kit, boolean granted) {
        ChangeBatch.Builder batch = ChangeBatch.builder(SingleWriter.AUTHOR)
            .upsert(next);
        if (granted && kit.cooldownSeconds() > 0) {
            batch.setCooldown(player, cooldownKey(key), clock.getAsLong() + kit.cooldownSeconds() * MILLIS_PER_SECOND);
        }
        return writer.commit(
            writer.state()
                .withPlayer(next),
            batch.build());
    }

    private long cooldownLeft(UUID player, String key, KitDefinition kit) {
        if (kit.cooldownSeconds() <= 0 || rights.has(player, Nodes.BYPASS_COOLDOWN)) {
            return 0L;
        }
        long expiresAt = writer.state()
            .cooldown(player, cooldownKey(key));
        return Math.max(0L, expiresAt - clock.getAsLong());
    }

    private PlayerRecord record(UUID player) {
        return writer.state()
            .player(player);
    }

    private String vetoOf(UUID player, String kit) {
        KitEvents registry = events.get();
        if (registry == null) {
            return null;
        }
        for (KitEvents.Listener listener : registry.listeners()) {
            EssentialsEvents.Decision decision;
            try {
                decision = listener.beforeClaim(player, kit);
            } catch (RuntimeException broken) {
                if (listener.kind() == EssentialsEvents.Kind.ENFORCE) {
                    log.warn("Enforcing kit listener {} failed, the kit is refused", listener, broken);
                    return listener.getClass()
                        .getName();
                }
                log.warn("Kit listener {} failed and was skipped", listener, broken);
                continue;
            }
            if (decision != null && !decision.allowed()) {
                return decision.reason()
                    .orElse(
                        listener.getClass()
                            .getName());
            }
        }
        return null;
    }

    private void announce(UUID player, String kit, int delivered, int buffered) {
        KitEvents registry = events.get();
        if (registry == null) {
            return;
        }
        for (KitEvents.Listener listener : registry.listeners()) {
            try {
                listener.afterClaim(player, kit, delivered, buffered);
            } catch (RuntimeException broken) {
                log.warn("Kit listener {} failed after the claim", listener, broken);
            }
        }
    }

    private Claim onMainThread(Supplier<Claim> work) {
        AtomicReference<Claim> answer = new AtomicReference<>();
        AtomicBoolean claimed = new AtomicBoolean();
        CountDownLatch done = new CountDownLatch(1);
        scheduler.onMainThread(() -> {
            if (!claimed.compareAndSet(false, true)) {
                return;
            }
            try {
                answer.set(work.get());
            } catch (RuntimeException broken) {
                log.error("Kit claim broke on the main thread: {}", broken.toString(), broken);
            } finally {
                done.countDown();
            }
        });
        try {
            if (done.await(HomeServiceImpl.AWAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                Claim given = answer.get();
                return given == null ? Claim.unknown() : given;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
        }
        log.warn("Kit claim was dropped from the queue: the main thread did not take it in time");
        return Claim.unknown();
    }

    private KitDefinition decode(String name, KitsFile.Kit kit) {
        if (name == null || kit == null) {
            return null;
        }
        KitDefinition.Builder builder = KitDefinition.named(name)
            .once(kit.once)
            .cooldown(Math.max(0, kit.cooldownSeconds));
        for (Map.Entry<String, String> entry : slotsOf(kit).entrySet()) {
            Integer slot = slotOf(entry.getKey());
            if (slot == null) {
                log.warn("Kit {} holds a slot key {} that is not a number and was skipped", name, entry.getKey());
                continue;
            }
            if (slot.intValue() < 0 || slot.intValue() >= KitDefinition.SLOTS) {
                log.warn(
                    "Kit {} holds a slot {} outside 0..{} and was skipped",
                    name,
                    slot,
                    Integer.valueOf(KitDefinition.SLOTS - 1));
                continue;
            }
            Optional<KitItem> item = KitItem.parse(entry.getValue());
            if (!item.isPresent()) {
                log.warn(
                    "Kit {} holds an unreadable item in slot {} and was skipped: {}",
                    name,
                    slot,
                    entry.getValue());
                continue;
            }
            builder.slot(slot.intValue(), item.get());
        }
        try {
            return builder.build();
        } catch (RuntimeException broken) {
            log.warn("Kit {} is unusable and was skipped: {}", name, broken.getMessage());
            return null;
        }
    }

    private static KitsFile.Kit encode(KitDefinition kit) {
        Map<String, String> slots = new LinkedHashMap<>();
        for (Map.Entry<Integer, KitItem> entry : kit.slots()
            .entrySet()) {
            slots.put(
                String.valueOf(entry.getKey()),
                entry.getValue()
                    .print());
        }
        return new KitsFile.Kit(kit.once(), kit.cooldownSeconds(), slots);
    }

    private static KitDefinition emptyOf(String name) {
        return KitDefinition.named(name)
            .build();
    }

    private static Claim refusedOf(String veto, boolean taken, long wait, int pending) {
        if (veto != null) {
            return Claim.vetoed(veto, 0, pending);
        }
        if (taken) {
            return Claim.refused(Claim.Outcome.TAKEN, 0, pending, 0L);
        }
        if (wait > 0L) {
            return Claim.refused(Claim.Outcome.COOLDOWN, 0, pending, wait);
        }
        return null;
    }

    private static Claim withCounts(Claim refused, int delivered, int pending) {
        if (refused.outcome() == Claim.Outcome.VETOED) {
            return Claim.vetoed(
                refused.reason()
                    .orElse("denied"),
                delivered,
                pending);
        }
        return Claim.refused(refused.outcome(), delivered, pending, refused.waitMillis());
    }

    private static Map<String, String> slotsOf(KitsFile.Kit kit) {
        return kit.slots == null ? Collections.<String, String>emptyMap() : kit.slots;
    }

    private static Integer slotOf(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static int count(List<KitItem> items) {
        int count = 0;
        for (KitItem item : items) {
            count += item.count();
        }
        return count;
    }

    private static void restore(KitsFile file, String name, KitsFile.Kit previous) {
        if (previous == null) {
            file.kits.remove(name);
            return;
        }
        file.kits.put(name, previous);
    }

    private static String cooldownKey(String kit) {
        return COOLDOWN_KEY_PREFIX + kit;
    }

    private static String lower(String name) {
        return name == null ? ""
            : name.trim()
                .toLowerCase(Locale.ROOT);
    }
}
