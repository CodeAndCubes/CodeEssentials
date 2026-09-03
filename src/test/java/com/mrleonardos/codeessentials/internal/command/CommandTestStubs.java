package com.mrleonardos.codeessentials.internal.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.mrleonardos.codecore.api.command.ArgumentType;
import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.command.CommandNode;
import com.mrleonardos.codecore.api.command.CommandService;
import com.mrleonardos.codeessentials.api.manage.BackService;
import com.mrleonardos.codeessentials.api.manage.HomeService;
import com.mrleonardos.codeessentials.api.manage.SpawnService;
import com.mrleonardos.codeessentials.api.manage.WarpService;
import com.mrleonardos.codeessentials.api.model.BackPoint;
import com.mrleonardos.codeessentials.api.model.HomeRecord;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.model.SpawnTable;
import com.mrleonardos.codeessentials.api.model.WarpRecord;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.CancelReason;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.api.teleport.TeleportJob;
import com.mrleonardos.codeessentials.api.teleport.TeleportRequest;
import com.mrleonardos.codeessentials.api.teleport.TeleportService;

final class CommandTestStubs {

    private CommandTestStubs() {}

    static EssentialsArguments arguments() {
        return new EssentialsArguments() {

            @Override
            public ArgumentType<String> playerName() {
                return raw -> raw;
            }

            @Override
            public ArgumentType<String> homeName() {
                return raw -> raw;
            }

            @Override
            public ArgumentType<String> warpName() {
                return raw -> raw;
            }

            @Override
            public ArgumentType<Double> coordinate() {
                return Double::valueOf;
            }
        };
    }

    static final class Subjects implements EssentialsSubjects {

        UUID self;
        Point position;
        String actor = "console";
        final Set<String> nodes = new LinkedHashSet<>();
        final Map<String, UUID> known = new LinkedHashMap<>();
        final Map<UUID, String> names = new LinkedHashMap<>();
        final Map<UUID, Point> positions = new LinkedHashMap<>();
        final Set<UUID> online = new LinkedHashSet<>();
        final List<String> told = new ArrayList<>();

        void player(UUID id, String name, Point where) {
            known.put(name, id);
            names.put(id, name);
            positions.put(id, where);
            online.add(id);
        }

        @Override
        public Optional<UUID> playerOf(CommandContext context) {
            return Optional.ofNullable(self);
        }

        @Override
        public String actorOf(CommandContext context) {
            return actor;
        }

        @Override
        public boolean allowed(CommandContext context, String node) {
            return nodes.contains(node);
        }

        @Override
        public Optional<Point> positionOf(CommandContext context) {
            return Optional.ofNullable(position);
        }

        @Override
        public Optional<Point> positionOf(UUID player) {
            return Optional.ofNullable(positions.get(player));
        }

        @Override
        public Optional<UUID> resolve(String name) {
            return Optional.ofNullable(known.get(name));
        }

        @Override
        public Optional<String> nameOf(UUID player) {
            return Optional.ofNullable(names.get(player));
        }

        @Override
        public boolean online(UUID player) {
            return online.contains(player);
        }

        @Override
        public void tell(UUID player, String translationKey, Object... arguments) {
            told.add(player + " " + translationKey);
        }
    }

    static final class Teleports implements TeleportService {

        final List<TeleportRequest> asked = new ArrayList<>();
        final Map<TeleportCause, Long> cooldowns = new EnumMap<>(TeleportCause.class);
        final Set<UUID> bypassing = new LinkedHashSet<>();
        final List<TeleportJob> running = new ArrayList<>();

        TeleportJob.State outcome = TeleportJob.State.WARMUP;
        CancelReason reason = CancelReason.UNSAFE;
        Point landing;
        TeleportJob cancelled;
        TeleportJob active;
        StoreResult clearAnswer = StoreResult.success();
        long nextId;

        @Override
        public TeleportJob request(TeleportRequest request) {
            asked.add(request);
            TeleportJob job = TeleportJob.starting(nextId++, request);
            switch (outcome) {
                case DONE:
                    return job.moving()
                        .done(landing == null ? request.destination() : landing);
                case MOVING:
                    return job.moving();
                case WARMUP:
                    return job;
                default:
                    return job.stopped(reason);
            }
        }

        @Override
        public Optional<TeleportJob> job(UUID player) {
            if (active != null && active.player()
                .equals(player)) {
                return Optional.of(active);
            }
            for (TeleportJob job : running) {
                if (job.player()
                    .equals(player)) {
                    return Optional.of(job);
                }
            }
            return Optional.empty();
        }

        @Override
        public List<TeleportJob> jobs() {
            return running;
        }

        @Override
        public Optional<TeleportJob> cancel(UUID player, CancelReason cancelReason) {
            return Optional.ofNullable(cancelled);
        }

        @Override
        public long cooldownRemaining(UUID player, TeleportCause cause) {
            Long left = cooldowns.get(cause);
            return left == null || bypassing.contains(player) ? 0L : left.longValue();
        }

        @Override
        public StoreResult clearCooldowns(UUID player) {
            return clearAnswer;
        }
    }

    static final class Homes implements HomeService {

        final Map<String, HomeRecord> owned = new TreeMap<>();
        int limit = 3;
        StoreResult answer = StoreResult.success();
        String lastActor;

        @Override
        public Optional<HomeRecord> home(UUID player, String name) {
            return Optional.ofNullable(owned.get(name));
        }

        @Override
        public Map<String, HomeRecord> homes(UUID player) {
            return Collections.unmodifiableMap(owned);
        }

        @Override
        public int homeLimit(UUID player) {
            return limit;
        }

        @Override
        public StoreResult setHome(UUID player, String name, Point point, String actor) {
            lastActor = actor;
            if (answer.successful()) {
                owned.put(name, HomeRecord.of(name, point, 1L));
            }
            return answer;
        }

        @Override
        public StoreResult deleteHome(UUID player, String name, String actor) {
            lastActor = actor;
            if (answer.successful()) {
                owned.remove(name);
            }
            return answer;
        }
    }

    static final class Warps implements WarpService {

        final Map<String, WarpRecord> stored = new TreeMap<>();
        StoreResult answer = StoreResult.success();
        Boolean lastSafeSpot;

        @Override
        public Optional<WarpRecord> warp(String name) {
            return Optional.ofNullable(stored.get(name));
        }

        @Override
        public Map<String, WarpRecord> warps() {
            return Collections.unmodifiableMap(stored);
        }

        @Override
        public StoreResult setWarp(WarpRecord warp, boolean safeSpot, String actor) {
            lastSafeSpot = Boolean.valueOf(safeSpot);
            if (answer.successful()) {
                stored.put(warp.name(), warp);
            }
            return answer;
        }

        @Override
        public StoreResult deleteWarp(String name, String actor) {
            if (answer.successful()) {
                stored.remove(name);
            }
            return answer;
        }
    }

    static final class Spawns implements SpawnService {

        SpawnTable held = SpawnTable.empty();
        StoreResult answer = StoreResult.success();
        Point lastGlobal;
        Point lastDimension;

        @Override
        public SpawnTable table() {
            return held;
        }

        @Override
        public Optional<Point> spawnFor(int dimension) {
            return held.pointFor(dimension);
        }

        @Override
        public StoreResult setGlobalSpawn(Point point, String actor) {
            lastGlobal = point;
            return answer;
        }

        @Override
        public StoreResult setDimensionSpawn(Point point, String actor) {
            lastDimension = point;
            return answer;
        }
    }

    static final class Backs implements BackService {

        final List<BackPoint> stack = new ArrayList<>();
        int popped;

        @Override
        public Optional<BackPoint> peek(UUID player) {
            return stack.isEmpty() ? Optional.empty() : Optional.of(stack.get(0));
        }

        @Override
        public List<BackPoint> stack(UUID player) {
            return stack;
        }

        @Override
        public int depthFor(UUID player) {
            return 1;
        }

        @Override
        public StoreResult record(UUID player, BackPoint point) {
            stack.add(0, point);
            return StoreResult.success();
        }

        @Override
        public boolean pop(UUID player) {
            if (stack.isEmpty()) {
                return false;
            }
            stack.remove(0);
            popped++;
            return true;
        }
    }

    static final class Requests implements TeleportRequests {

        UUID askedTarget;
        Boolean askedHere;
        String askedName;
        boolean toggled = true;
        Reply reply = Reply.of(Answer.SENT, "");
        Reply answered = Reply.of(Answer.ACCEPTED, "Alex");
        Reply cancelled = Reply.of(Answer.CANCELLED, "Alex");

        @Override
        public Reply send(UUID from, String fromName, UUID to, String toName, boolean here) {
            askedTarget = to;
            askedHere = Boolean.valueOf(here);
            return reply;
        }

        @Override
        public Reply accept(UUID target, String fromName) {
            askedName = fromName;
            return answered;
        }

        @Override
        public Reply deny(UUID target, String fromName) {
            askedName = fromName;
            return answered;
        }

        @Override
        public Reply cancel(UUID from) {
            return cancelled;
        }

        @Override
        public boolean toggle(UUID player) {
            return toggled;
        }
    }

    static final class Spots implements RandomSpots {

        final List<Point> asked = new ArrayList<>();
        Reply reply = Reply.refused(Outcome.NOT_FOUND, 8);

        @Override
        public Reply find(Point origin) {
            asked.add(origin);
            return reply;
        }
    }

    static final class Maintenance implements EssentialsMaintenance {

        StoreResult answer = StoreResult.success("config, commands, warps, spawn");
        int calls;

        @Override
        public StoreResult reloadSettings() {
            calls++;
            return answer;
        }
    }

    static final class Commands implements CommandService {

        final List<CommandNode> registered = new ArrayList<>();

        @Override
        public void register(CommandNode root) {
            registered.add(root);
        }

        List<String> names() {
            List<String> names = new ArrayList<>();
            for (CommandNode root : registered) {
                names.add(root.name());
            }
            return names;
        }
    }
}
