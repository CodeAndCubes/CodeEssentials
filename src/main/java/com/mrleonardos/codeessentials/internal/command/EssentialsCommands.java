package com.mrleonardos.codeessentials.internal.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.command.ArgumentTypes;
import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.command.CommandMessages;
import com.mrleonardos.codecore.api.command.CommandNode;
import com.mrleonardos.codecore.api.command.CommandService;
import com.mrleonardos.codecore.api.util.Durations;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.manage.BackService;
import com.mrleonardos.codeessentials.api.manage.HomeService;
import com.mrleonardos.codeessentials.api.manage.KitService;
import com.mrleonardos.codeessentials.api.manage.SpawnService;
import com.mrleonardos.codeessentials.api.manage.WarpService;
import com.mrleonardos.codeessentials.api.model.BackPoint;
import com.mrleonardos.codeessentials.api.model.HomeRecord;
import com.mrleonardos.codeessentials.api.model.KitDefinition;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.model.WarpRecord;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.CancelReason;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.api.teleport.TeleportJob;
import com.mrleonardos.codeessentials.api.teleport.TeleportRequest;
import com.mrleonardos.codeessentials.api.teleport.TeleportService;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.SharedSettings;
import com.mrleonardos.codeessentials.internal.kits.WornSlots;

public final class EssentialsCommands {

    public static final int MIN_HEIGHT = 0;
    public static final int MAX_HEIGHT = 255;

    private static final String DEFAULT_HOME = "home";
    private static final String FORCE_FLAG = "--force";
    private static final String DIM_FLAG = "--dim";
    private static final String CLEAR_ACTION = "clear";
    private static final String EMPTY_MARKER = "-";

    private static final String NAME = "name";
    private static final String PLAYER = "player";
    private static final String KIT = "kit";
    private static final String WHO = "who";
    private static final String TARGET = "target";
    private static final String SECOND = "second";
    private static final String THIRD = "third";
    private static final String FLAGS = "flags";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";
    private static final String DIMENSION = "dimension";
    private static final String ACTION = "action";

    private final Supplier<EssentialsSettings> settings;
    private final Supplier<SharedSettings> shared;
    private final Supplier<CommandRoots> roots;
    private final Supplier<TeleportService> teleports;
    private final Supplier<HomeService> homes;
    private final Supplier<WarpService> warps;
    private final Supplier<SpawnService> spawns;
    private final Supplier<BackService> backs;
    private final Supplier<KitService> kits;
    private final KitEditors editors;
    private final TeleportRequests requests;
    private final RandomSpots spots;
    private final EssentialsArguments arguments;
    private final EssentialsSubjects subjects;
    private final EssentialsMaintenance maintenance;
    private final Logger log;

    public EssentialsCommands(Supplier<EssentialsSettings> settings, Supplier<SharedSettings> shared,
        Supplier<CommandRoots> roots, Supplier<TeleportService> teleports, Supplier<HomeService> homes,
        Supplier<WarpService> warps, Supplier<SpawnService> spawns, Supplier<BackService> backs,
        Supplier<KitService> kits, KitEditors editors, TeleportRequests requests, RandomSpots spots,
        EssentialsArguments arguments, EssentialsSubjects subjects, EssentialsMaintenance maintenance, Logger log) {
        this.settings = settings;
        this.shared = shared;
        this.roots = roots;
        this.teleports = teleports;
        this.homes = homes;
        this.warps = warps;
        this.spawns = spawns;
        this.backs = backs;
        this.kits = kits;
        this.editors = editors;
        this.requests = requests;
        this.spots = spots;
        this.arguments = arguments;
        this.subjects = subjects;
        this.maintenance = maintenance;
        this.log = log;
    }

    public void register(CommandService commands) {
        for (CommandNode root : roots.get()
            .chosen(allRoots(), log)) {
            commands.register(root);
        }
    }

    public List<CommandNode> allRoots() {
        List<CommandNode> all = new ArrayList<>();
        all.add(
            CommandNode.literal(CommandRoots.HOME)
                .permission(Nodes.HOME)
                .usage(EssentialsMessages.USAGE_HOME)
                .optionalArg(NAME, arguments.homeName())
                .executes(this::home));
        all.add(
            CommandNode.literal(CommandRoots.SETHOME)
                .permission(Nodes.HOME_SET)
                .usage(EssentialsMessages.USAGE_SETHOME)
                .optionalArg(NAME, arguments.homeName())
                .executes(this::setHome));
        all.add(
            CommandNode.literal(CommandRoots.DELHOME)
                .permission(Nodes.HOME_DELETE)
                .usage(EssentialsMessages.USAGE_DELHOME)
                .arg(NAME, arguments.homeName())
                .executes(this::deleteHome));
        all.add(
            CommandNode.literal(CommandRoots.HOMES)
                .permission(Nodes.HOME)
                .usage(EssentialsMessages.USAGE_HOMES)
                .executes(this::listHomes));
        all.add(
            CommandNode.literal(CommandRoots.WARP)
                .permission(Nodes.WARP)
                .usage(EssentialsMessages.USAGE_WARP)
                .arg(NAME, arguments.warpName())
                .executes(this::warp));
        all.add(
            CommandNode.literal(CommandRoots.WARPS)
                .permission(Nodes.WARP)
                .usage(EssentialsMessages.USAGE_WARPS)
                .executes(this::listWarps));
        all.add(
            CommandNode.literal(CommandRoots.SETWARP)
                .permission(Nodes.WARP_SET)
                .usage(EssentialsMessages.USAGE_SETWARP)
                .arg(NAME, arguments.warpName())
                .optionalArg(FLAGS, ArgumentTypes.text())
                .executes(this::setWarp));
        all.add(
            CommandNode.literal(CommandRoots.DELWARP)
                .permission(Nodes.WARP_DELETE)
                .usage(EssentialsMessages.USAGE_DELWARP)
                .arg(NAME, arguments.warpName())
                .executes(this::deleteWarp));
        all.add(
            CommandNode.literal(CommandRoots.SPAWN)
                .permission(Nodes.SPAWN)
                .usage(EssentialsMessages.USAGE_SPAWN)
                .executes(this::spawn));
        all.add(
            CommandNode.literal(CommandRoots.SETSPAWN)
                .permission(Nodes.SPAWN_SET)
                .usage(EssentialsMessages.USAGE_SETSPAWN)
                .optionalArg(FLAGS, ArgumentTypes.text())
                .executes(this::setSpawn));
        all.add(
            CommandNode.literal(CommandRoots.BACK)
                .permission(Nodes.BACK)
                .usage(EssentialsMessages.USAGE_BACK)
                .executes(this::back));
        all.add(
            CommandNode.literal(CommandRoots.RTP)
                .permission(Nodes.RANDOM)
                .usage(EssentialsMessages.USAGE_RTP)
                .executes(this::randomSpot));
        all.add(kitRoot());
        all.add(
            CommandNode.literal(CommandRoots.KITS)
                .usage(EssentialsMessages.USAGE_KITS)
                .executes(this::listKits));
        all.add(
            CommandNode.literal(CommandRoots.TPA)
                .permission(Nodes.TPA)
                .usage(EssentialsMessages.USAGE_TPA)
                .arg(PLAYER, arguments.playerName())
                .executes(context -> ask(context, false)));
        all.add(
            CommandNode.literal(CommandRoots.TPAHERE)
                .permission(Nodes.TPA_HERE)
                .usage(EssentialsMessages.USAGE_TPAHERE)
                .arg(PLAYER, arguments.playerName())
                .executes(context -> ask(context, true)));
        all.add(
            CommandNode.literal(CommandRoots.TPACCEPT)
                .permission(Nodes.TPA)
                .usage(EssentialsMessages.USAGE_TPACCEPT)
                .optionalArg(PLAYER, arguments.playerName())
                .executes(context -> answer(context, true)));
        all.add(
            CommandNode.literal(CommandRoots.TPDENY)
                .permission(Nodes.TPA)
                .usage(EssentialsMessages.USAGE_TPDENY)
                .optionalArg(PLAYER, arguments.playerName())
                .executes(context -> answer(context, false)));
        all.add(
            CommandNode.literal(CommandRoots.TPACANCEL)
                .permission(Nodes.TPA)
                .usage(EssentialsMessages.USAGE_TPACANCEL)
                .executes(this::withdraw));
        all.add(
            CommandNode.literal(CommandRoots.TPATOGGLE)
                .permission(Nodes.TPA_TOGGLE)
                .usage(EssentialsMessages.USAGE_TPATOGGLE)
                .executes(this::toggleRequests));
        all.add(
            CommandNode.literal(CommandRoots.TP)
                .permission(Nodes.ADMIN_TELEPORT)
                .usage(EssentialsMessages.USAGE_TP)
                .arg(WHO, arguments.playerName())
                .arg(TARGET, arguments.playerName())
                .optionalArg(SECOND, ArgumentTypes.word())
                .optionalArg(THIRD, ArgumentTypes.word())
                .optionalArg(FLAGS, ArgumentTypes.text())
                .executes(this::teleport));
        all.add(
            CommandNode.literal(CommandRoots.TPPOS)
                .permission(Nodes.ADMIN_TELEPORT)
                .usage(EssentialsMessages.USAGE_TPPOS)
                .arg(WHO, arguments.playerName())
                .arg(X, arguments.coordinate())
                .arg(Y, arguments.coordinate())
                .arg(Z, arguments.coordinate())
                .optionalArg(DIMENSION, ArgumentTypes.integer())
                .optionalArg(FLAGS, ArgumentTypes.text())
                .executes(this::teleportToPosition));
        all.add(
            CommandNode.literal(CommandRoots.ECANCEL)
                .usage(EssentialsMessages.USAGE_ECANCEL)
                .optionalArg(PLAYER, arguments.playerName())
                .executes(this::cancelJob));
        all.add(essentialsRoot());
        return all;
    }

    private CommandNode essentialsRoot() {
        return CommandNode.literal(CommandRoots.ESSENTIALS)
            .usage(EssentialsMessages.USAGE_ESSENTIALS)
            .child(
                CommandNode.literal("reload")
                    .permission(Nodes.ADMIN_RELOAD)
                    .usage(EssentialsMessages.USAGE_ESSENTIALS)
                    .executes(this::reload))
            .child(
                CommandNode.literal("cooldown")
                    .permission(Nodes.ADMIN_COOLDOWN)
                    .usage(EssentialsMessages.USAGE_ESSENTIALS)
                    .arg(PLAYER, arguments.playerName())
                    .optionalArg(ACTION, ArgumentTypes.word())
                    .executes(this::cooldowns))
            .child(
                CommandNode.literal("jobs")
                    .permission(Nodes.ADMIN_JOBS)
                    .usage(EssentialsMessages.USAGE_ESSENTIALS)
                    .optionalArg(PLAYER, arguments.playerName())
                    .executes(this::jobs))
            .executes(this::branches);
    }

    private CommandNode kitRoot() {
        return CommandNode.literal(CommandRoots.KIT)
            .usage(EssentialsMessages.USAGE_KIT)
            .child(
                CommandNode.literal("edit")
                    .permission(Nodes.KIT_ADMIN)
                    .usage(EssentialsMessages.USAGE_KIT)
                    .arg(NAME, arguments.kitName())
                    .executes(this::editKit))
            .child(
                CommandNode.literal("save")
                    .permission(Nodes.KIT_ADMIN)
                    .usage(EssentialsMessages.USAGE_KIT)
                    .arg(NAME, arguments.kitName())
                    .executes(this::saveKit))
            .child(
                CommandNode.literal("delete")
                    .permission(Nodes.KIT_ADMIN)
                    .usage(EssentialsMessages.USAGE_KIT)
                    .arg(NAME, arguments.kitName())
                    .executes(this::deleteKit))
            .child(
                CommandNode.literal("give")
                    .permission(Nodes.KIT_ADMIN)
                    .usage(EssentialsMessages.USAGE_KIT)
                    .arg(PLAYER, arguments.playerName())
                    .arg(KIT, arguments.kitName())
                    .executes(context -> giveKit(context)))
            .optionalArg(NAME, arguments.kitName())
            .executes(this::claimKit);
    }

    private void home(CommandContext context) {
        if (stateOff(context)) {
            return;
        }
        UUID self = playerOf(context);
        if (self == null) {
            return;
        }
        Map<String, HomeRecord> owned = homes.get()
            .homes(self);
        if (owned.isEmpty()) {
            context.replyError(EssentialsMessages.ERROR_NO_HOMES);
            return;
        }
        HomeRecord chosen;
        if (context.has(NAME)) {
            String key = lower(context.get(NAME));
            chosen = owned.get(key);
            if (chosen == null) {
                context.replyError(EssentialsMessages.ERROR_UNKNOWN_HOME, key);
                return;
            }
        } else if (owned.containsKey(DEFAULT_HOME)) {
            chosen = owned.get(DEFAULT_HOME);
        } else if (owned.size() == 1) {
            chosen = owned.values()
                .iterator()
                .next();
        } else {
            replyHomes(context, self, owned);
            return;
        }
        if (waiting(context, self, TeleportCause.HOME)) {
            return;
        }
        start(context, self, chosen.point(), TeleportCause.HOME, true);
    }

    private void setHome(CommandContext context) {
        if (stateOff(context)) {
            return;
        }
        UUID self = playerOf(context);
        if (self == null) {
            return;
        }
        Point here = positionOf(context);
        if (here == null) {
            return;
        }
        String name = context.has(NAME) ? lower(context.get(NAME)) : DEFAULT_HOME;
        if (!ceilings().acceptsName(name)) {
            context.replyError(EssentialsMessages.ERROR_INVALID_NAME, name, EssentialsLimits.NAME_PATTERN);
            return;
        }
        HomeService service = homes.get();
        boolean overwrite = service.home(self, name)
            .isPresent();
        StoreResult written = service.setHome(self, name, here, subjects.actorOf(context));
        if (written.successful()) {
            context.reply(overwrite ? EssentialsMessages.HOME_MOVED : EssentialsMessages.HOME_SET, name);
            return;
        }
        if (written.failure()
            .orElse(null) == StoreResult.Failure.LIMIT_REACHED) {
            context.replyError(EssentialsMessages.ERROR_HOME_LIMIT, Integer.valueOf(service.homeLimit(self)));
            return;
        }
        context.replyError(EssentialsMessages.failureKey(written), name);
    }

    private void deleteHome(CommandContext context) {
        if (stateOff(context)) {
            return;
        }
        UUID self = playerOf(context);
        if (self == null) {
            return;
        }
        String name = lower(context.get(NAME));
        StoreResult written = homes.get()
            .deleteHome(self, name, subjects.actorOf(context));
        if (written.successful()) {
            context.reply(EssentialsMessages.HOME_DELETED, name);
            return;
        }
        context.replyError(EssentialsMessages.failureKey(written), name);
    }

    private void listHomes(CommandContext context) {
        if (stateOff(context)) {
            return;
        }
        UUID self = playerOf(context);
        if (self == null) {
            return;
        }
        replyHomes(
            context,
            self,
            homes.get()
                .homes(self));
    }

    private void replyHomes(CommandContext context, UUID self, Map<String, HomeRecord> owned) {
        if (owned.isEmpty()) {
            context.reply(EssentialsMessages.HOMES_EMPTY);
            return;
        }
        List<String> lines = new ArrayList<>();
        for (HomeRecord home : owned.values()) {
            lines.add(
                home.name() + " ("
                    + home.point()
                        .dimension()
                    + ")");
        }
        context.reply(
            EssentialsMessages.HOMES,
            Integer.valueOf(owned.size()),
            Integer.valueOf(
                homes.get()
                    .homeLimit(self)),
            join(lines));
    }

    private void warp(CommandContext context) {
        UUID self = playerOf(context);
        if (self == null) {
            return;
        }
        String name = lower(context.get(NAME));
        if (!ceilings().acceptsName(name)) {
            context.replyError(EssentialsMessages.ERROR_INVALID_NAME, name, EssentialsLimits.NAME_PATTERN);
            return;
        }
        WarpRecord record = warps.get()
            .warp(name)
            .orElse(null);
        if (record == null) {
            context.replyError(EssentialsMessages.ERROR_UNKNOWN_WARP, name);
            return;
        }
        if (!subjects.allowed(context, Nodes.warpGo(name))) {
            context.replyError(EssentialsMessages.ERROR_WARP_DENIED, name);
            return;
        }
        if (waiting(context, self, TeleportCause.WARP)) {
            return;
        }
        start(context, self, record.point(), TeleportCause.WARP, true);
    }

    private void listWarps(CommandContext context) {
        List<String> lines = new ArrayList<>();
        for (WarpRecord record : warps.get()
            .warps()
            .values()) {
            if (!subjects.allowed(context, Nodes.warpGo(record.name()))) {
                continue;
            }
            lines.add(
                record.description()
                    .isEmpty() ? record.name() : record.name() + " (" + record.description() + ")");
        }
        if (lines.isEmpty()) {
            context.reply(EssentialsMessages.WARPS_EMPTY);
            return;
        }
        context.reply(EssentialsMessages.WARPS, join(lines));
    }

    private void setWarp(CommandContext context) {
        Point here = positionOf(context);
        if (here == null) {
            return;
        }
        List<String> tail = tokens(context, FLAGS);
        boolean force = tail.remove(FORCE_FLAG);
        if (!tail.isEmpty()) {
            context.replyError(EssentialsMessages.ERROR_BAD_ARGUMENTS, join(tail));
            return;
        }
        if (force && !subjects.allowed(context, Nodes.ADMIN_TELEPORT)) {
            context.replyError(CommandMessages.NO_PERMISSION);
            return;
        }
        String name = lower(context.get(NAME));
        if (!ceilings().acceptsName(name)) {
            context.replyError(EssentialsMessages.ERROR_INVALID_NAME, name, EssentialsLimits.NAME_PATTERN);
            return;
        }
        WarpService service = warps.get();
        WarpRecord previous = service.warp(name)
            .orElse(null);
        String description = previous == null ? "" : previous.description();
        StoreResult written = service
            .setWarp(WarpRecord.of(name, here, description), !force, subjects.actorOf(context));
        if (written.successful()) {
            context.reply(previous == null ? EssentialsMessages.WARP_SET : EssentialsMessages.WARP_MOVED, name);
            return;
        }
        context.replyError(EssentialsMessages.failureKey(written), name);
    }

    private void deleteWarp(CommandContext context) {
        String name = lower(context.get(NAME));
        if (!ceilings().acceptsName(name)) {
            context.replyError(EssentialsMessages.ERROR_INVALID_NAME, name, EssentialsLimits.NAME_PATTERN);
            return;
        }
        StoreResult written = warps.get()
            .deleteWarp(name, subjects.actorOf(context));
        if (written.successful()) {
            context.reply(EssentialsMessages.WARP_DELETED, name);
            return;
        }
        context.replyError(EssentialsMessages.failureKey(written), name);
    }

    private void spawn(CommandContext context) {
        UUID self = playerOf(context);
        if (self == null) {
            return;
        }
        Point here = positionOf(context);
        if (here == null) {
            return;
        }
        Point target = spawns.get()
            .spawnFor(here.dimension())
            .orElse(null);
        if (target == null) {
            context.replyError(EssentialsMessages.ERROR_NO_SPAWN);
            return;
        }
        if (waiting(context, self, TeleportCause.SPAWN)) {
            return;
        }
        start(context, self, target, TeleportCause.SPAWN, true);
    }

    private void setSpawn(CommandContext context) {
        Point here = positionOf(context);
        if (here == null) {
            return;
        }
        List<String> tail = tokens(context, FLAGS);
        boolean perDimension = tail.remove(DIM_FLAG);
        if (!tail.isEmpty()) {
            context.replyError(EssentialsMessages.ERROR_BAD_ARGUMENTS, join(tail));
            return;
        }
        SpawnService service = spawns.get();
        String actor = subjects.actorOf(context);
        StoreResult written = perDimension ? service.setDimensionSpawn(here, actor)
            : service.setGlobalSpawn(here, actor);
        if (written.successful()) {
            context.reply(
                perDimension ? EssentialsMessages.SPAWN_DIMENSION_SET : EssentialsMessages.SPAWN_GLOBAL_SET,
                perDimension ? String.valueOf(here.dimension()) : here.print());
            return;
        }
        context.replyError(EssentialsMessages.failureKey(written), here.print());
    }

    private void back(CommandContext context) {
        if (stateOff(context)) {
            return;
        }
        UUID self = playerOf(context);
        if (self == null) {
            return;
        }
        Point here = positionOf(context);
        if (here == null) {
            return;
        }
        BackPoint top = backs.get()
            .peek(self)
            .orElse(null);
        if (top == null) {
            context.replyError(EssentialsMessages.ERROR_NO_BACK);
            return;
        }
        if (top.fromDeath() && !subjects.allowed(context, Nodes.BACK_DEATH)) {
            context.replyError(EssentialsMessages.ERROR_BACK_DEATH_DENIED);
            return;
        }
        if (!top.point()
            .sameDimension(here) && !subjects.allowed(context, Nodes.BACK_CROSSWORLD)) {
            context.replyError(EssentialsMessages.ERROR_BACK_CROSSWORLD_DENIED);
            return;
        }
        if (waiting(context, self, TeleportCause.BACK)) {
            return;
        }
        start(context, self, top.point(), TeleportCause.BACK, true);
    }

    private void randomSpot(CommandContext context) {
        UUID self = playerOf(context);
        if (self == null) {
            return;
        }
        Point here = positionOf(context);
        if (here == null) {
            return;
        }
        if (waiting(context, self, TeleportCause.RANDOM)) {
            return;
        }
        RandomSpots.Reply reply = spots.find(here);
        Point target = reply.spot()
            .orElse(null);
        if (target == null) {
            reportSpot(context, reply);
            return;
        }
        if (reply.outcome() == RandomSpots.Outcome.SPAWN) {
            context.reply(EssentialsMessages.RTP_SPAWN, Integer.valueOf(reply.attempts()));
        }
        start(context, self, target, TeleportCause.RANDOM, true);
    }

    private void claimKit(CommandContext context) {
        if (!context.has(NAME)) {
            listKits(context);
            return;
        }
        if (stateOff(context)) {
            return;
        }
        UUID self = playerOf(context);
        if (self == null) {
            return;
        }
        String name = lower(context.get(NAME));
        if (!subjects.allowed(context, Nodes.kit(name))) {
            context.replyError(EssentialsMessages.ERROR_KIT_DENIED, name);
            return;
        }
        claim(context, name, self, false);
    }

    private void listKits(CommandContext context) {
        Map<String, KitDefinition> all = kits.get()
            .kits();
        if (all.isEmpty()) {
            context.reply(EssentialsMessages.KITS_EMPTY);
            return;
        }
        UUID self = subjects.playerOf(context)
            .orElse(null);
        Map<String, Integer> pending = self == null ? Collections.<String, Integer>emptyMap()
            : kits.get()
                .pendingByKit(self);
        context.reply(EssentialsMessages.KITS, Integer.valueOf(all.size()));
        for (KitDefinition kit : all.values()) {
            replyKitLine(
                context,
                kit,
                self,
                pending.containsKey(kit.name()) ? pending.get(kit.name())
                    .intValue() : 0);
        }
    }

    private void replyKitLine(CommandContext context, KitDefinition kit, UUID self, int buffered) {
        String name = kit.name();
        boolean locked = !subjects.allowed(context, Nodes.kit(name));
        if (buffered > 0) {
            context.reply(
                locked ? EssentialsMessages.KITS_LINE_BUFFERED_LOCKED : EssentialsMessages.KITS_LINE_BUFFERED,
                name,
                Integer.valueOf(buffered));
            return;
        }
        if (self != null && kit.once()
            && kits.get()
                .taken(self, name)) {
            context
                .reply(locked ? EssentialsMessages.KITS_LINE_TAKEN_LOCKED : EssentialsMessages.KITS_LINE_TAKEN, name);
            return;
        }
        long left = self == null ? 0L
            : kits.get()
                .cooldownLeft(self, name);
        if (left > 0L) {
            context.reply(
                locked ? EssentialsMessages.KITS_LINE_WAIT_LOCKED : EssentialsMessages.KITS_LINE_WAIT,
                name,
                Durations.format(seconds(left)));
            return;
        }
        context.reply(locked ? EssentialsMessages.KITS_LINE_READY_LOCKED : EssentialsMessages.KITS_LINE_READY, name);
    }

    private void editKit(CommandContext context) {
        UUID self = playerOf(context);
        if (self == null) {
            return;
        }
        String name = lower(context.get(NAME));
        KitDefinition kit = kits.get()
            .kit(name)
            .orElseGet(
                () -> KitDefinition.named(name)
                    .build());
        KitEditors.Opening opening = editors.edit(self, kit);
        if (opening == KitEditors.Opening.OFFLINE) {
            context.replyError(EssentialsMessages.ERROR_SENDER_NOT_PLAYER);
            return;
        }
        if (opening == KitEditors.Opening.UNSHOWABLE) {
            return;
        }
        context.reply(EssentialsMessages.KIT_EDITOR, name);
    }

    private void saveKit(CommandContext context) {
        UUID self = playerOf(context);
        if (self == null) {
            return;
        }
        String name = lower(context.get(NAME));
        WornSlots worn = editors.capture(self)
            .orElse(null);
        if (worn == null) {
            context.replyError(EssentialsMessages.ERROR_SENDER_NOT_PLAYER);
            return;
        }
        KitDefinition.Builder builder = KitDefinition.named(name);
        for (int slot = 0; slot < KitDefinition.SLOTS; slot++) {
            if (worn.item(slot) != null) {
                builder.slot(slot, worn.item(slot));
            }
        }
        define(context, builder.build());
    }

    private void deleteKit(CommandContext context) {
        String name = lower(context.get(NAME));
        StoreResult deleted = kits.get()
            .delete(name, subjects.actorOf(context));
        if (deleted.successful()) {
            context.reply(EssentialsMessages.KIT_DELETED, name);
            return;
        }
        context.replyError(EssentialsMessages.failureKey(deleted), name);
    }

    private void giveKit(CommandContext context) {
        if (stateOff(context)) {
            return;
        }
        String name = lower(context.get(KIT));
        if (!kits.get()
            .kit(name)
            .isPresent()) {
            context.replyError(EssentialsMessages.ERROR_KIT_UNKNOWN, name);
            return;
        }
        String asked = context.get(PLAYER);
        UUID target = subjects.resolve(asked)
            .orElse(null);
        if (target == null) {
            context.replyError(EssentialsMessages.ERROR_UNKNOWN_PLAYER, asked);
            return;
        }
        claim(context, name, target, true);
    }

    private void claim(CommandContext context, String name, UUID target, boolean given) {
        KitService.Claim answer = kits.get()
            .claim(target, name, subjects.actorOf(context));
        switch (answer.outcome()) {
            case DELIVERED:
                context.reply(EssentialsMessages.KIT_CLAIMED, name, Integer.valueOf(answer.delivered()));
                if (answer.buffered() > 0) {
                    context.reply(EssentialsMessages.KIT_OVERFLOW, Integer.valueOf(answer.buffered()));
                }
                if (given) {
                    context.reply(EssentialsMessages.KIT_GIVEN, name, nameOf(target));
                }
                return;
            case STASHED:
                context.reply(EssentialsMessages.KIT_STASHED, name, Integer.valueOf(answer.pending()));
                if (given) {
                    context.reply(EssentialsMessages.KIT_GIVEN, name, nameOf(target));
                }
                return;
            case COOLDOWN:
                reportDebt(context, name, answer);
                context.replyError(
                    EssentialsMessages.ERROR_KIT_COOLDOWN,
                    name,
                    Durations.format(seconds(answer.waitMillis())));
                return;
            case TAKEN:
                reportDebt(context, name, answer);
                context.replyError(EssentialsMessages.ERROR_KIT_TAKEN, name);
                return;
            case VETOED:
                reportDebt(context, name, answer);
                context.replyError(
                    EssentialsMessages.ERROR_VETOED,
                    answer.reason()
                        .orElse(EMPTY_MARKER));
                return;
            default:
                context.replyError(EssentialsMessages.ERROR_KIT_UNKNOWN, name);
        }
    }

    private void reportDebt(CommandContext context, String name, KitService.Claim answer) {
        if (answer.delivered() > 0) {
            context.reply(EssentialsMessages.KIT_DEBT, name, Integer.valueOf(answer.delivered()));
        }
    }

    private void define(CommandContext context, KitDefinition kit) {
        StoreResult written = kits.get()
            .define(kit, subjects.actorOf(context));
        if (!written.successful()) {
            if (written.failure()
                .orElse(null) == StoreResult.Failure.INVALID_VALUE) {
                context.replyError(EssentialsMessages.ERROR_KIT_EMPTY);
                return;
            }
            context.replyError(EssentialsMessages.failureKey(written), kit.name());
            return;
        }
        context.reply(EssentialsMessages.KIT_SAVED, kit.name(), Integer.valueOf(kit.size()));
    }

    private void reportSpot(CommandContext context, RandomSpots.Reply reply) {
        switch (reply.outcome()) {
            case OFF:
                context.replyError(EssentialsMessages.ERROR_RTP_OFF);
                return;
            case WRONG_WORLD:
                context.replyError(EssentialsMessages.ERROR_RTP_WORLD);
                return;
            case NO_WORLD:
                context.replyError(EssentialsMessages.FAILED_DIMENSION_MISSING);
                return;
            default:
                context.replyError(EssentialsMessages.ERROR_RTP_NOT_FOUND, Integer.valueOf(reply.attempts()));
        }
    }

    private void ask(CommandContext context, boolean here) {
        UUID self = playerOf(context);
        if (self == null) {
            return;
        }
        UUID target = target(context, context.get(PLAYER));
        if (target == null) {
            return;
        }
        TeleportRequests.Reply reply = requests.send(self, nameOf(self), target, nameOf(target), here);
        if (reply.answer() != TeleportRequests.Answer.SENT) {
            reportRequest(context, reply);
            return;
        }
        String waiting = Durations.format(
            settings.get()
                .requestTimeoutSeconds(ceilings()));
        context.reply(EssentialsMessages.REQUEST_SENT, nameOf(target), waiting);
        subjects.tell(target, EssentialsMessages.REQUEST_INCOMING, nameOf(self), waiting);
    }

    private void answer(CommandContext context, boolean accept) {
        UUID self = playerOf(context);
        if (self == null) {
            return;
        }
        String from = context.has(PLAYER) ? context.get(PLAYER) : null;
        TeleportRequests.Reply reply = accept ? requests.accept(self, from) : requests.deny(self, from);
        if (reply.answer() == TeleportRequests.Answer.ACCEPTED) {
            reportAccepted(context, self, reply);
            return;
        }
        if (reply.answer() == TeleportRequests.Answer.DENIED) {
            context.reply(EssentialsMessages.REQUEST_DENIED, reply.subject());
            return;
        }
        if (reply.answer() == TeleportRequests.Answer.COOLING_DOWN) {
            reportCooling(context, self, reply);
            return;
        }
        reportRequest(context, reply);
    }

    private void reportCooling(CommandContext context, UUID self, TeleportRequests.Reply reply) {
        String wait = Durations.format(seconds(reply.waitMillis()));
        UUID moved = reply.moved()
            .orElse(null);
        if (moved == null || moved.equals(self)) {
            context.replyError(EssentialsMessages.ERROR_COOLDOWN, wait);
            return;
        }
        context.replyError(EssentialsMessages.ERROR_MOVED_COOLDOWN, nameOf(moved), wait);
        subjects.tell(moved, EssentialsMessages.ERROR_COOLDOWN, wait);
    }

    private void reportAccepted(CommandContext context, UUID self, TeleportRequests.Reply reply) {
        TeleportJob job = reply.job()
            .orElse(null);
        UUID moved = reply.moved()
            .orElse(null);
        boolean carried = moved != null && !moved.equals(self);
        if (job != null && job.finished() && !job.applied()) {
            String outcome = EssentialsMessages.outcomeKey(
                job.reason()
                    .orElse(CancelReason.BY_COMMAND));
            context.replyError(outcome);
            if (carried) {
                subjects.tell(moved, outcome);
            }
            return;
        }
        context.reply(EssentialsMessages.REQUEST_ACCEPTED, reply.subject());
        if (carried) {
            subjects.tell(moved, EssentialsMessages.REQUEST_TAKEN, nameOf(self));
        }
        if (job == null || job.state() != TeleportJob.State.WARMUP) {
            return;
        }
        if (carried) {
            subjects.tell(moved, EssentialsMessages.WARMUP);
            return;
        }
        context.reply(EssentialsMessages.WARMUP);
    }

    private void withdraw(CommandContext context) {
        UUID self = playerOf(context);
        if (self == null) {
            return;
        }
        TeleportRequests.Reply reply = requests.cancel(self);
        if (reply.answer() == TeleportRequests.Answer.CANCELLED) {
            context.reply(EssentialsMessages.REQUEST_WITHDRAWN, reply.subject());
            return;
        }
        if (reply.answer() == TeleportRequests.Answer.NONE) {
            context.replyError(EssentialsMessages.ERROR_NO_OUTGOING);
            return;
        }
        reportRequest(context, reply);
    }

    private void reportRequest(CommandContext context, TeleportRequests.Reply reply) {
        switch (reply.answer()) {
            case SELF:
                context.replyError(EssentialsMessages.ERROR_SELF_TARGET);
                return;
            case BLOCKED:
                context.replyError(EssentialsMessages.ERROR_REQUESTS_CLOSED, reply.subject());
                return;
            case RATE_LIMITED:
                context.replyError(EssentialsMessages.ERROR_COOLDOWN, Durations.format(seconds(reply.waitMillis())));
                return;
            case OFFLINE:
                context.replyError(EssentialsMessages.ERROR_PLAYER_OFFLINE, reply.subject());
                return;
            case AMBIGUOUS:
                context.replyError(EssentialsMessages.ERROR_MANY_REQUESTS, join(reply.names()));
                return;
            default:
                context.replyError(EssentialsMessages.ERROR_NO_REQUEST);
        }
    }

    private void toggleRequests(CommandContext context) {
        if (stateOff(context)) {
            return;
        }
        UUID self = playerOf(context);
        if (self == null) {
            return;
        }
        boolean accepting = requests.toggle(self);
        context.reply(accepting ? EssentialsMessages.REQUESTS_ON : EssentialsMessages.REQUESTS_OFF);
    }

    private void teleport(CommandContext context) {
        UUID who = target(context, context.get(WHO));
        if (who == null) {
            return;
        }
        List<String> tail = new ArrayList<>();
        tail.add(context.get(TARGET));
        if (context.has(SECOND)) {
            tail.add(context.get(SECOND));
        }
        if (context.has(THIRD)) {
            tail.add(context.get(THIRD));
        }
        tail.addAll(tokens(context, FLAGS));
        boolean force = tail.remove(FORCE_FLAG);
        Point here = subjects.positionOf(who)
            .orElse(null);
        if (here == null) {
            context.replyError(EssentialsMessages.ERROR_PLAYER_OFFLINE, context.get(WHO));
            return;
        }
        Point destination;
        if (tail.size() == 1) {
            UUID to = target(context, tail.get(0));
            if (to == null) {
                return;
            }
            destination = subjects.positionOf(to)
                .orElse(null);
            if (destination == null) {
                context.replyError(EssentialsMessages.ERROR_PLAYER_OFFLINE, tail.get(0));
                return;
            }
        } else if (tail.size() == 3) {
            destination = coordinates(context, here, tail.get(0), tail.get(1), tail.get(2));
            if (destination == null) {
                return;
            }
        } else {
            context.replyError(EssentialsMessages.ERROR_BAD_ARGUMENTS, join(tail));
            return;
        }
        move(context, who, destination, force);
    }

    private void teleportToPosition(CommandContext context) {
        UUID who = target(context, context.get(WHO));
        if (who == null) {
            return;
        }
        List<String> tail = tokens(context, FLAGS);
        boolean force = tail.remove(FORCE_FLAG);
        if (!tail.isEmpty()) {
            context.replyError(EssentialsMessages.ERROR_BAD_ARGUMENTS, join(tail));
            return;
        }
        Point here = subjects.positionOf(who)
            .orElse(null);
        if (here == null) {
            context.replyError(EssentialsMessages.ERROR_PLAYER_OFFLINE, context.get(WHO));
            return;
        }
        int dimension = context.has(DIMENSION) ? ((Integer) context.get(DIMENSION)).intValue() : here.dimension();
        double x = ((Double) context.get(X)).doubleValue();
        double y = ((Double) context.get(Y)).doubleValue();
        double z = ((Double) context.get(Z)).doubleValue();
        move(context, who, Point.of(dimension, x, clampHeight(y), z, here.yaw(), here.pitch()), force);
    }

    private void move(CommandContext context, UUID who, Point destination, boolean force) {
        TeleportJob job = start(context, who, destination, TeleportCause.ADMIN, !force);
        if (job == null || !job.applied()) {
            return;
        }
        context.reply(EssentialsMessages.MOVED, nameOf(who), destination.print());
        if (shared.get()
            .logChanges()) {
            log.info(
                "{} moved {} to {}",
                job.request()
                    .actor(),
                nameOf(who),
                job.landing()
                    .orElse(destination)
                    .print());
        }
    }

    private void cancelJob(CommandContext context) {
        UUID target;
        boolean other;
        if (context.has(PLAYER)) {
            target = target(context, context.get(PLAYER));
            if (target == null) {
                return;
            }
            other = !target.equals(
                subjects.playerOf(context)
                    .orElse(null));
        } else {
            target = playerOf(context);
            if (target == null) {
                return;
            }
            other = false;
        }
        if (other && !subjects.allowed(context, Nodes.ADMIN_TELEPORT)) {
            context.replyError(CommandMessages.NO_PERMISSION);
            return;
        }
        TeleportService service = teleports.get();
        TeleportJob active = service.job(target)
            .orElse(null);
        TeleportJob stopped = service.cancel(target, CancelReason.BY_COMMAND)
            .orElse(null);
        if (stopped == null) {
            context.replyError(EssentialsMessages.ERROR_NO_JOB, nameOf(target));
            return;
        }
        if (active != null && active.id() != stopped.id()) {
            context.reply(EssentialsMessages.CANCELLED_WAITING, nameOf(target));
            return;
        }
        if (other) {
            context.reply(EssentialsMessages.CANCELLED_OTHER, nameOf(target));
            return;
        }
        context.reply(EssentialsMessages.CANCELLED);
    }

    private void branches(CommandContext context) {
        List<String> open = new ArrayList<>();
        if (subjects.allowed(context, Nodes.ADMIN_RELOAD)) {
            open.add("reload");
        }
        if (subjects.allowed(context, Nodes.ADMIN_COOLDOWN)) {
            open.add("cooldown");
        }
        if (subjects.allowed(context, Nodes.ADMIN_JOBS)) {
            open.add("jobs");
        }
        context.reply(EssentialsMessages.BRANCHES, join(open));
    }

    private void reload(CommandContext context) {
        StoreResult reloaded = maintenance.reloadSettings();
        if (reloaded.successful()) {
            context.reply(
                EssentialsMessages.RELOAD_DONE,
                reloaded.message()
                    .orElse(EMPTY_MARKER));
            return;
        }
        context.replyError(
            EssentialsMessages.failureKey(reloaded),
            reloaded.message()
                .orElse(EMPTY_MARKER));
    }

    private void cooldowns(CommandContext context) {
        if (stateOff(context)) {
            return;
        }
        UUID target = target(context, context.get(PLAYER));
        if (target == null) {
            return;
        }
        TeleportService service = teleports.get();
        if (context.has(ACTION)) {
            String action = lower(context.get(ACTION));
            if (!CLEAR_ACTION.equals(action)) {
                context.replyError(EssentialsMessages.ERROR_BAD_ARGUMENTS, action);
                return;
            }
            StoreResult cleared = service.clearCooldowns(target);
            if (cleared.successful()) {
                context.reply(EssentialsMessages.COOLDOWNS_CLEARED, nameOf(target));
                return;
            }
            if (cleared.failure()
                .orElse(null) == StoreResult.Failure.NOT_FOUND) {
                context.reply(EssentialsMessages.COOLDOWNS_EMPTY, nameOf(target));
                return;
            }
            context.replyError(EssentialsMessages.failureKey(cleared), nameOf(target));
            return;
        }
        List<String> lines = new ArrayList<>();
        for (TeleportCause cause : TeleportCause.values()) {
            if (!cause.chargesCooldown()) {
                continue;
            }
            long left = service.cooldownRemaining(target, cause);
            if (left > 0L) {
                lines.add(cause.key() + " " + Durations.format(seconds(left)));
            }
        }
        if (lines.isEmpty()) {
            context.reply(EssentialsMessages.COOLDOWNS_EMPTY, nameOf(target));
            return;
        }
        context.reply(EssentialsMessages.COOLDOWNS, nameOf(target), join(lines));
    }

    private void jobs(CommandContext context) {
        TeleportService service = teleports.get();
        List<TeleportJob> shown = new ArrayList<>();
        if (context.has(PLAYER)) {
            UUID target = target(context, context.get(PLAYER));
            if (target == null) {
                return;
            }
            service.job(target)
                .ifPresent(shown::add);
        } else {
            shown.addAll(service.jobs());
        }
        if (shown.isEmpty()) {
            context.reply(EssentialsMessages.JOBS_EMPTY);
            return;
        }
        List<String> lines = new ArrayList<>();
        for (TeleportJob job : shown) {
            lines.add(
                nameOf(job.player()) + " "
                    + job.state()
                    + " "
                    + job.cause()
                        .key()
                    + " -> "
                    + job.request()
                        .destination()
                        .print());
        }
        context.reply(EssentialsMessages.JOBS, Integer.valueOf(shown.size()), join(lines));
    }

    private TeleportJob start(CommandContext context, UUID player, Point destination, TeleportCause cause,
        boolean safeSpot) {
        TeleportJob job = teleports.get()
            .request(
                TeleportRequest.builder(player, destination, cause)
                    .safeSpot(safeSpot)
                    .actor(subjects.actorOf(context))
                    .build());
        if (job.state() == TeleportJob.State.WARMUP) {
            context.reply(EssentialsMessages.WARMUP);
            return job;
        }
        if (job.finished() && !job.applied()) {
            context.replyError(
                EssentialsMessages.outcomeKey(
                    job.reason()
                        .orElse(CancelReason.BY_COMMAND)));
            return job;
        }
        if (job.applied() && job.corrected()) {
            context.reply(
                EssentialsMessages.CORRECTED,
                job.landing()
                    .get()
                    .print());
        }
        return job;
    }

    private boolean waiting(CommandContext context, UUID player, TeleportCause cause) {
        long left = teleports.get()
            .cooldownRemaining(player, cause);
        if (left <= 0L) {
            return false;
        }
        context.replyError(EssentialsMessages.ERROR_COOLDOWN, Durations.format(seconds(left)));
        return true;
    }

    private boolean stateOff(CommandContext context) {
        if (maintenance.stateOn()) {
            return false;
        }
        context.replyError(EssentialsMessages.ERROR_STATE_OFF);
        return true;
    }

    private UUID playerOf(CommandContext context) {
        UUID self = subjects.playerOf(context)
            .orElse(null);
        if (self == null) {
            context.replyError(EssentialsMessages.ERROR_SENDER_NOT_PLAYER);
        }
        return self;
    }

    private Point positionOf(CommandContext context) {
        Point here = subjects.positionOf(context)
            .orElse(null);
        if (here == null) {
            context.replyError(EssentialsMessages.ERROR_SENDER_NOT_PLAYER);
        }
        return here;
    }

    private UUID target(CommandContext context, String name) {
        UUID found = subjects.resolve(name)
            .orElse(null);
        if (found == null) {
            context.replyError(EssentialsMessages.ERROR_UNKNOWN_PLAYER, name);
            return null;
        }
        if (!subjects.online(found)) {
            context.replyError(EssentialsMessages.ERROR_PLAYER_OFFLINE, name);
            return null;
        }
        return found;
    }

    private Point coordinates(CommandContext context, Point here, String x, String y, String z) {
        try {
            return Point.of(
                here.dimension(),
                Double.parseDouble(x),
                clampHeight(Double.parseDouble(y)),
                Double.parseDouble(z),
                here.yaw(),
                here.pitch());
        } catch (IllegalArgumentException broken) {
            context.replyError(EssentialsMessages.ERROR_BAD_ARGUMENTS, x + " " + y + " " + z);
            return null;
        }
    }

    private EssentialsLimits ceilings() {
        return settings.get()
            .ceilings();
    }

    private String nameOf(UUID player) {
        return subjects.nameOf(player)
            .orElseGet(player::toString);
    }

    private static List<String> tokens(CommandContext context, String argument) {
        List<String> tokens = new ArrayList<>();
        if (!context.has(argument)) {
            return tokens;
        }
        for (String token : ((String) context.get(argument)).split(" ")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static double clampHeight(double y) {
        return Math.max(MIN_HEIGHT, Math.min(y, MAX_HEIGHT));
    }

    private static int seconds(long millis) {
        return (int) Math.min(Integer.MAX_VALUE, (millis + 999L) / 1000L);
    }

    private static String join(List<String> parts) {
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(part);
        }
        return joined.length() == 0 ? EMPTY_MARKER : joined.toString();
    }

    private static String lower(String value) {
        return value == null ? ""
            : value.trim()
                .toLowerCase(Locale.ROOT);
    }
}
