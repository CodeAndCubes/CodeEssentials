package com.mrleonardos.codeessentials.internal.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.command.ArgumentSpec;
import com.mrleonardos.codecore.api.command.CommandMessages;
import com.mrleonardos.codecore.api.command.CommandNode;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
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
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.SharedFixtures;
import com.mrleonardos.codeessentials.internal.SharedSettings;

class EssentialsCommandsTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final Point HERE = Point.of(0, 10.0D, 64.0D, 20.0D, 90.0F, 0.0F);
    private static final Point NETHER = Point.of(-1, 5.0D, 70.0D, 5.0D);

    private final Logger log = LogManager.getLogger("codeessentials-test");

    private EssentialsSettings settings;
    private SharedSettings shared;
    private CommandRoots book;
    private CommandTestStubs.Subjects subjects;
    private CommandTestStubs.Teleports teleports;
    private CommandTestStubs.Homes homes;
    private CommandTestStubs.Warps warps;
    private CommandTestStubs.Spawns spawns;
    private CommandTestStubs.Backs backs;
    private CommandTestStubs.Requests requests;
    private CommandTestStubs.Spots spots;
    private CommandTestStubs.Maintenance maintenance;
    private EssentialsCommands commands;

    @BeforeEach
    void setUp() {
        settings = new EssentialsSettings();
        shared = SharedSettings.defaults();
        book = new CommandRoots();
        subjects = new CommandTestStubs.Subjects();
        teleports = new CommandTestStubs.Teleports();
        homes = new CommandTestStubs.Homes();
        warps = new CommandTestStubs.Warps();
        spawns = new CommandTestStubs.Spawns();
        backs = new CommandTestStubs.Backs();
        requests = new CommandTestStubs.Requests();
        spots = new CommandTestStubs.Spots();
        maintenance = new CommandTestStubs.Maintenance();
        commands = new EssentialsCommands(
            () -> settings,
            () -> shared,
            () -> book,
            () -> teleports,
            () -> homes,
            () -> warps,
            () -> spawns,
            () -> backs,
            requests,
            spots,
            CommandTestStubs.arguments(),
            subjects,
            maintenance,
            log);
        subjects.self = STEVE;
        subjects.position = HERE;
        subjects.actor = "Steve";
        subjects.player(STEVE, "Steve", HERE);
        subjects.player(ALEX, "Alex", NETHER);
    }

    @Test
    void everyRootFromTheSpecIsBuilt() {
        assertEquals(
            new ArrayList<>(
                CommandRoots.factoryAliases()
                    .keySet()),
            names(commands.allRoots()));
    }

    @Test
    void rootsCarryTheNodesFromTheSpec() {
        assertEquals(Nodes.HOME, root(CommandRoots.HOME).permissionNode());
        assertEquals(Nodes.HOME_SET, root(CommandRoots.SETHOME).permissionNode());
        assertEquals(Nodes.HOME_DELETE, root(CommandRoots.DELHOME).permissionNode());
        assertEquals(Nodes.HOME, root(CommandRoots.HOMES).permissionNode());
        assertEquals(Nodes.WARP, root(CommandRoots.WARP).permissionNode());
        assertEquals(Nodes.WARP, root(CommandRoots.WARPS).permissionNode());
        assertEquals(Nodes.WARP_SET, root(CommandRoots.SETWARP).permissionNode());
        assertEquals(Nodes.WARP_DELETE, root(CommandRoots.DELWARP).permissionNode());
        assertEquals(Nodes.SPAWN, root(CommandRoots.SPAWN).permissionNode());
        assertEquals(Nodes.SPAWN_SET, root(CommandRoots.SETSPAWN).permissionNode());
        assertEquals(Nodes.BACK, root(CommandRoots.BACK).permissionNode());
        assertEquals(Nodes.RANDOM, root(CommandRoots.RTP).permissionNode());
        assertEquals(Nodes.TPA, root(CommandRoots.TPA).permissionNode());
        assertEquals(Nodes.TPA_HERE, root(CommandRoots.TPAHERE).permissionNode());
        assertEquals(Nodes.TPA, root(CommandRoots.TPACCEPT).permissionNode());
        assertEquals(Nodes.TPA, root(CommandRoots.TPDENY).permissionNode());
        assertEquals(Nodes.TPA, root(CommandRoots.TPACANCEL).permissionNode());
        assertEquals(Nodes.TPA_TOGGLE, root(CommandRoots.TPATOGGLE).permissionNode());
        assertEquals(Nodes.ADMIN_TELEPORT, root(CommandRoots.TP).permissionNode());
        assertEquals(Nodes.ADMIN_TELEPORT, root(CommandRoots.TPPOS).permissionNode());
        assertNull(root(CommandRoots.ECANCEL).permissionNode(), "своя работа снимается без ноды");
        assertNull(root(CommandRoots.ESSENTIALS).permissionNode(), "корень essentials ноды не несёт");
    }

    @Test
    void maintenanceBranchesHideBehindTheirNodes() {
        CommandNode essentials = root(CommandRoots.ESSENTIALS);

        assertEquals(Nodes.ADMIN_RELOAD, child(essentials, "reload").permissionNode());
        assertEquals(Nodes.ADMIN_COOLDOWN, child(essentials, "cooldown").permissionNode());
        assertEquals(Nodes.ADMIN_JOBS, child(essentials, "jobs").permissionNode());
        assertTrue(visible(essentials).isEmpty(), "без нод не видно ни одной ветки");

        subjects.nodes.add(Nodes.ADMIN_JOBS);
        assertEquals(Collections.singletonList("jobs"), visible(essentials));
    }

    @Test
    void essentialsWithoutActionNamesOnlyOpenBranches() {
        TestCommandContext bare = new TestCommandContext();
        execute(root(CommandRoots.ESSENTIALS), bare);
        assertTrue(
            bare.last()
                .is(EssentialsMessages.BRANCHES));
        assertEquals("-", bare.last().arguments.get(0));

        subjects.nodes.add(Nodes.ADMIN_RELOAD);
        subjects.nodes.add(Nodes.ADMIN_JOBS);
        TestCommandContext admin = new TestCommandContext();
        execute(root(CommandRoots.ESSENTIALS), admin);
        assertEquals("reload, jobs", admin.last().arguments.get(0));
    }

    @Test
    void personalCommandsRefuseANonPlayerSender() {
        subjects.self = null;
        subjects.position = null;

        for (String name : Arrays.asList(
            CommandRoots.HOME,
            CommandRoots.SETHOME,
            CommandRoots.DELHOME,
            CommandRoots.HOMES,
            CommandRoots.SPAWN,
            CommandRoots.BACK,
            CommandRoots.TPA,
            CommandRoots.TPAHERE,
            CommandRoots.TPACCEPT,
            CommandRoots.TPDENY,
            CommandRoots.TPACANCEL,
            CommandRoots.TPATOGGLE)) {
            TestCommandContext context = new TestCommandContext().set("name", "base")
                .set("player", "Alex");
            execute(root(name), context);
            assertTrue(context.last().error, name);
            assertTrue(
                context.last()
                    .is(EssentialsMessages.ERROR_SENDER_NOT_PLAYER),
                name);
        }
        assertTrue(teleports.asked.isEmpty(), "отказ значит, что перенос не начинался");
        assertNull(requests.askedTarget, "отказ значит, что доска просьб не тронута");
        assertTrue(homes.owned.isEmpty());
    }

    @Test
    void consoleMovesAPlayerWithTpAndSignsTheAudit() {
        subjects.self = null;
        subjects.position = null;
        subjects.actor = "console";
        teleports.outcome = TeleportJob.State.DONE;

        TestCommandContext context = new TestCommandContext().set("who", "Steve")
            .set("target", "Alex");
        List<String> audit = record(() -> execute(root(CommandRoots.TP), context));

        assertEquals(1, teleports.asked.size());
        TeleportRequest asked = teleports.asked.get(0);
        assertEquals(STEVE, asked.player());
        assertEquals(NETHER, asked.destination());
        assertEquals(TeleportCause.ADMIN, asked.cause());
        assertEquals("console", asked.actor());
        assertTrue(
            context.last()
                .is(EssentialsMessages.MOVED));
        assertTrue(
            audit.stream()
                .anyMatch(line -> line.contains("console") && line.contains("Steve") && line.contains(NETHER.print())),
            () -> "админский перенос обязан оставить строку с автором: " + audit);
    }

    @Test
    void aRefusedAdminMoveWritesNothingToTheAudit() {
        subjects.actor = "console";
        teleports.outcome = TeleportJob.State.FAILED;
        teleports.reason = CancelReason.UNSAFE;

        TestCommandContext context = new TestCommandContext().set("who", "Steve")
            .set("target", "Alex");
        List<String> audit = record(() -> execute(root(CommandRoots.TP), context));

        assertTrue(audit.isEmpty(), () -> "несостоявшийся перенос в журнале не значится: " + audit);
    }

    @Test
    void theAuditOfAdminMovesGoesQuietWithTheFlagOff() {
        shared = SharedFixtures.audit(false, false);
        subjects.actor = "console";
        teleports.outcome = TeleportJob.State.DONE;

        TestCommandContext context = new TestCommandContext().set("who", "Steve")
            .set("target", "Alex");
        List<String> audit = record(() -> execute(root(CommandRoots.TP), context));

        assertTrue(audit.isEmpty(), () -> audit.toString());
    }

    private List<String> record(Runnable work) {
        org.apache.logging.log4j.core.Logger held = (org.apache.logging.log4j.core.Logger) log;
        CapturingAppender appender = new CapturingAppender();
        Level before = held.getLevel();
        held.addAppender(appender);
        held.setLevel(Level.INFO);
        try {
            work.run();
        } finally {
            held.removeAppender(appender);
            held.setLevel(before);
        }
        return appender.lines;
    }

    /** Подставной приёмник строк аудита: ловит отформатированные сообщения. */
    private static final class CapturingAppender extends AbstractAppender {

        private final List<String> lines = new ArrayList<>();

        CapturingAppender() {
            super("capturing", null, null, true);
            start();
        }

        @Override
        public void append(LogEvent event) {
            lines.add(
                event.getMessage()
                    .getFormattedMessage());
        }
    }

    @Test
    void tpposClampsHeightAndForceSkipsTheSearch() {
        subjects.self = null;
        subjects.position = null;
        teleports.outcome = TeleportJob.State.DONE;

        TestCommandContext context = new TestCommandContext().set("who", "Steve")
            .set("x", "1")
            .set("y", "500")
            .set("z", "2")
            .set("flags", "--force");
        execute(root(CommandRoots.TPPOS), context);

        TeleportRequest asked = teleports.asked.get(0);
        assertEquals(
            EssentialsCommands.MAX_HEIGHT,
            asked.destination()
                .y(),
            0.0D);
        assertFalse(asked.safeSpot(), "--force пропускает поиск безопасной точки");
    }

    @Test
    void tpWithBrokenTailChangesNothing() {
        TestCommandContext context = new TestCommandContext().set("who", "Steve")
            .set("target", "1")
            .set("second", "2");
        execute(root(CommandRoots.TP), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_BAD_ARGUMENTS));
        assertTrue(teleports.asked.isEmpty());
    }

    @Test
    void homeNameWithADotIsRefusedBeforeTheStore() {
        TestCommandContext context = new TestCommandContext().set("name", "мой.дом");

        execute(root(CommandRoots.SETHOME), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_INVALID_NAME));
        assertTrue(homes.owned.isEmpty(), "отказ значит, что хранилище не тронуто");
    }

    @Test
    void fullLimitAnswersWithTheNumberAndKeepsTheStore() {
        homes.limit = 3;
        homes.answer = StoreResult.failure(StoreResult.Failure.LIMIT_REACHED, "3");

        TestCommandContext context = new TestCommandContext().set("name", "shop");
        execute(root(CommandRoots.SETHOME), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_HOME_LIMIT));
        assertEquals(Integer.valueOf(3), context.last().arguments.get(0));
        assertTrue(homes.owned.isEmpty());
    }

    @Test
    void sethomeTellsSettingApartFromMoving() {
        TestCommandContext first = new TestCommandContext().set("name", "base");
        execute(root(CommandRoots.SETHOME), first);
        assertTrue(
            first.last()
                .is(EssentialsMessages.HOME_SET));

        TestCommandContext again = new TestCommandContext().set("name", "base");
        execute(root(CommandRoots.SETHOME), again);
        assertTrue(
            again.last()
                .is(EssentialsMessages.HOME_MOVED));
        assertEquals("Steve", homes.lastActor);
    }

    @Test
    void homeWithoutArgumentTakesTheOnlyOneAndListsSeveral() {
        homes.owned.put("base", HomeRecord.of("base", HERE, 1L));

        TestCommandContext only = new TestCommandContext();
        execute(root(CommandRoots.HOME), only);
        assertEquals(1, teleports.asked.size());
        assertEquals(
            TeleportCause.HOME,
            teleports.asked.get(0)
                .cause());

        homes.owned.put("shop", HomeRecord.of("shop", NETHER, 1L));
        TestCommandContext several = new TestCommandContext();
        execute(root(CommandRoots.HOME), several);
        assertEquals(1, teleports.asked.size(), "со списком перенос не начинается");
        assertTrue(
            several.last()
                .is(EssentialsMessages.HOMES));
    }

    @Test
    void homeNamedHomeWinsOverTheRest() {
        homes.owned.put("base", HomeRecord.of("base", NETHER, 1L));
        homes.owned.put("home", HomeRecord.of("home", HERE, 1L));

        execute(root(CommandRoots.HOME), new TestCommandContext());

        assertEquals(
            HERE,
            teleports.asked.get(0)
                .destination());
    }

    @Test
    void warpNameIsReadWithoutRegardToCase() {
        warps.stored.put("shop", WarpRecord.of("shop", NETHER, ""));
        subjects.nodes.add(Nodes.warpGo("shop"));

        TestCommandContext context = new TestCommandContext().set("name", "SHOP");
        execute(root(CommandRoots.WARP), context);

        assertEquals(1, teleports.asked.size());
        assertEquals(
            NETHER,
            teleports.asked.get(0)
                .destination());
    }

    @Test
    void warpWithoutItsNodeIsNeitherSeenNorUsed() {
        warps.stored.put("shop", WarpRecord.of("shop", NETHER, "лавка"));

        TestCommandContext list = new TestCommandContext();
        execute(root(CommandRoots.WARPS), list);
        assertTrue(
            list.last()
                .is(EssentialsMessages.WARPS_EMPTY));

        TestCommandContext go = new TestCommandContext().set("name", "shop");
        execute(root(CommandRoots.WARP), go);
        assertTrue(go.last().error);
        assertTrue(
            go.last()
                .is(EssentialsMessages.ERROR_WARP_DENIED));
        assertTrue(teleports.asked.isEmpty());

        subjects.nodes.add(Nodes.warpGo("shop"));
        TestCommandContext open = new TestCommandContext();
        execute(root(CommandRoots.WARPS), open);
        assertTrue(
            open.last()
                .is(EssentialsMessages.WARPS));
    }

    @Test
    void setwarpForceNeedsTheAdminNode() {
        TestCommandContext denied = new TestCommandContext().set("name", "shop")
            .set("flags", "--force");
        execute(root(CommandRoots.SETWARP), denied);
        assertTrue(denied.last().error);
        assertTrue(
            denied.last()
                .is(CommandMessages.NO_PERMISSION));
        assertTrue(warps.stored.isEmpty());

        subjects.nodes.add(Nodes.ADMIN_TELEPORT);
        TestCommandContext allowed = new TestCommandContext().set("name", "shop")
            .set("flags", "--force");
        execute(root(CommandRoots.SETWARP), allowed);
        assertEquals(Boolean.FALSE, warps.lastSafeSpot);
        assertTrue(
            allowed.last()
                .is(EssentialsMessages.WARP_SET));
    }

    @Test
    void setwarpWithoutForceAsksForTheSafeSpot() {
        TestCommandContext context = new TestCommandContext().set("name", "shop");
        execute(root(CommandRoots.SETWARP), context);

        assertEquals(Boolean.TRUE, warps.lastSafeSpot);
    }

    @Test
    void movingAWarpKeepsTheDescriptionAHumanWrote() {
        warps.stored.put("shop", WarpRecord.of("shop", NETHER, "рынок у ратуши"));

        TestCommandContext context = new TestCommandContext().set("name", "shop");
        execute(root(CommandRoots.SETWARP), context);

        assertTrue(
            context.last()
                .is(EssentialsMessages.WARP_MOVED));
        assertEquals(
            "рынок у ратуши",
            warps.stored.get("shop")
                .description());
        assertEquals(
            HERE,
            warps.stored.get("shop")
                .point());
    }

    @Test
    void aWarpNameOverTheCeilingNeverReachesTheStore() {
        String tooLong = new String(new char[EssentialsLimits.DEFAULT_NAME_LENGTH + 1]).replace('\0', 'a');

        TestCommandContext go = new TestCommandContext().set("name", tooLong);
        execute(root(CommandRoots.WARP), go);
        assertTrue(
            go.last()
                .is(EssentialsMessages.ERROR_INVALID_NAME));
        assertTrue(teleports.asked.isEmpty());

        TestCommandContext drop = new TestCommandContext().set("name", tooLong);
        execute(root(CommandRoots.DELWARP), drop);
        assertTrue(
            drop.last()
                .is(EssentialsMessages.ERROR_INVALID_NAME));
    }

    @Test
    void setspawnWritesTheDimensionOnlyWithTheFlag() {
        TestCommandContext global = new TestCommandContext();
        execute(root(CommandRoots.SETSPAWN), global);
        assertEquals(HERE, spawns.lastGlobal);
        assertNull(spawns.lastDimension);

        TestCommandContext perDimension = new TestCommandContext().set("flags", "--dim");
        execute(root(CommandRoots.SETSPAWN), perDimension);
        assertEquals(HERE, spawns.lastDimension);
    }

    @Test
    void spawnTakesTheDimensionPointFirst() {
        subjects.position = NETHER;
        spawns.held = SpawnTable.of(HERE, Collections.singletonMap(Integer.valueOf(-1), NETHER));

        execute(root(CommandRoots.SPAWN), new TestCommandContext());

        assertEquals(
            NETHER,
            teleports.asked.get(0)
                .destination());
    }

    @Test
    void backAsksForTheCrossworldNodeAndKeepsTheStack() {
        backs.stack.add(BackPoint.of(NETHER, BackPoint.Origin.TELEPORT, 1L));

        TestCommandContext denied = new TestCommandContext();
        execute(root(CommandRoots.BACK), denied);
        assertTrue(denied.last().error);
        assertTrue(
            denied.last()
                .is(EssentialsMessages.ERROR_BACK_CROSSWORLD_DENIED));
        assertEquals(1, backs.stack.size());
        assertEquals(0, backs.popped);

        subjects.nodes.add(Nodes.BACK_CROSSWORLD);
        execute(root(CommandRoots.BACK), new TestCommandContext());
        assertEquals(1, teleports.asked.size());
        assertEquals(
            TeleportCause.BACK,
            teleports.asked.get(0)
                .cause());
    }

    @Test
    void theStackIsLeftToTheEngineOnEveryOutcome() {
        backs.stack.add(BackPoint.of(HERE, BackPoint.Origin.TELEPORT, 1L));

        teleports.outcome = TeleportJob.State.DONE;
        execute(root(CommandRoots.BACK), new TestCommandContext());

        teleports.outcome = TeleportJob.State.WARMUP;
        execute(root(CommandRoots.BACK), new TestCommandContext());

        assertEquals(0, backs.popped, "запись снимает движок в момент переноса, а не команда");
        assertEquals(1, backs.stack.size());
    }

    @Test
    void backToADeathSpotAsksForItsOwnNode() {
        backs.stack.add(BackPoint.of(HERE, BackPoint.Origin.DEATH, 1L));

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.BACK), context);

        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_BACK_DEATH_DENIED));
        assertTrue(teleports.asked.isEmpty());
    }

    @Test
    void failedBackKeepsTheRecordInPlace() {
        backs.stack.add(BackPoint.of(HERE, BackPoint.Origin.TELEPORT, 1L));
        teleports.outcome = TeleportJob.State.FAILED;
        teleports.reason = CancelReason.UNSAFE;

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.BACK), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(EssentialsMessages.FAILED_UNSAFE));
        assertEquals(0, backs.popped);
    }

    @Test
    void cooldownStopsTheCommandAndShowsTheRemainder() {
        teleports.cooldowns.put(TeleportCause.HOME, Long.valueOf(90_000L));
        homes.owned.put("home", HomeRecord.of("home", HERE, 1L));

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.HOME), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_COOLDOWN));
        assertEquals("1m 30s", context.last().arguments.get(0));
        assertTrue(teleports.asked.isEmpty());
    }

    @Test
    void bypassNodeSkipsTheCooldown() {
        teleports.cooldowns.put(TeleportCause.HOME, Long.valueOf(90_000L));
        homes.owned.put("home", HomeRecord.of("home", HERE, 1L));
        teleports.bypassing.add(STEVE);

        execute(root(CommandRoots.HOME), new TestCommandContext());

        assertEquals(1, teleports.asked.size(), "обход спрашивают у сервиса, а не второй раз у отправителя");
    }

    @Test
    void oneCooldownRefusalIsSaidExactlyOnce() {
        homes.owned.put("home", HomeRecord.of("home", HERE, 1L));
        teleports.cooldowns.put(TeleportCause.HOME, Long.valueOf(90_000L));

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.HOME), context);

        assertEquals(
            1,
            context.sent()
                .size(),
            () -> "об одном отказе говорят один раз: " + context.sent());
        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_COOLDOWN));
        assertTrue(teleports.asked.isEmpty(), "ранняя проверка не даёт движку повода отказать второй раз");
    }

    @Test
    void aCooldownRefusalFromTheEngineIsAlsoSaidOnce() {
        homes.owned.put("home", HomeRecord.of("home", HERE, 1L));
        teleports.outcome = TeleportJob.State.CANCELLED;
        teleports.reason = CancelReason.COOLDOWN;

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.HOME), context);

        assertEquals(
            1,
            context.sent()
                .size(),
            () -> "ранняя проверка промолчала, значит говорит движок, и тоже один раз: " + context.sent());
        assertTrue(
            context.last()
                .is(EssentialsMessages.CANCEL_COOLDOWN));
    }

    @Test
    void tpaTellsBothSidesWhenTheBoardTookTheRequest() {
        TestCommandContext sent = new TestCommandContext().set("player", "Alex");
        execute(root(CommandRoots.TPA), sent);

        assertEquals(ALEX, requests.askedTarget);
        assertEquals(Boolean.FALSE, requests.askedHere);
        assertTrue(
            sent.last()
                .is(EssentialsMessages.REQUEST_SENT));
        assertEquals(Collections.singletonList(ALEX + " " + EssentialsMessages.REQUEST_INCOMING), subjects.told);
    }

    @Test
    void anUnknownNickNeverReachesTheBoard() {
        TestCommandContext context = new TestCommandContext().set("player", "Ghost");
        execute(root(CommandRoots.TPA), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_UNKNOWN_PLAYER));
        assertNull(requests.askedTarget);
    }

    @Test
    void everyRefusalOfTheBoardGetsItsOwnAnswer() {
        requests.reply = TeleportRequests.Reply.plain(TeleportRequests.Answer.SELF);
        assertRefusal(CommandRoots.TPA, EssentialsMessages.ERROR_SELF_TARGET);

        requests.reply = TeleportRequests.Reply.of(TeleportRequests.Answer.BLOCKED, "Alex");
        assertRefusal(CommandRoots.TPA, EssentialsMessages.ERROR_REQUESTS_CLOSED);

        requests.reply = TeleportRequests.Reply.waiting(90_000L);
        TestCommandContext waiting = new TestCommandContext().set("player", "Alex");
        execute(root(CommandRoots.TPA), waiting);
        assertTrue(
            waiting.last()
                .is(EssentialsMessages.ERROR_COOLDOWN));
        assertEquals("1m 30s", waiting.last().arguments.get(0));

        requests.reply = TeleportRequests.Reply.of(TeleportRequests.Answer.OFFLINE, "Alex");
        assertRefusal(CommandRoots.TPA, EssentialsMessages.ERROR_PLAYER_OFFLINE);

        assertTrue(subjects.told.isEmpty(), "отказ значит, что второй стороне ничего не сказали");
    }

    @Test
    void tpaHereKeepsTheDirection() {
        execute(root(CommandRoots.TPAHERE), new TestCommandContext().set("player", "Alex"));

        assertEquals(Boolean.TRUE, requests.askedHere);
    }

    @Test
    void anOfflineNickNeverReachesTheBoard() {
        subjects.online.remove(ALEX);

        TestCommandContext context = new TestCommandContext().set("player", "Alex");
        execute(root(CommandRoots.TPA), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_PLAYER_OFFLINE));
        assertNull(requests.askedTarget, "просьба офлайн-игроку не занимает место в доске");
    }

    @Test
    void acceptingATpaTellsTheCarriedPlayerAboutTheWarmup() {
        requests.answered = TeleportRequests.Reply.accepted("Alex", ALEX, warming(ALEX));

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.TPACCEPT), context);

        assertTrue(
            context.last()
                .is(EssentialsMessages.REQUEST_ACCEPTED));
        assertEquals(
            Arrays.asList(ALEX + " " + EssentialsMessages.REQUEST_TAKEN, ALEX + " " + EssentialsMessages.WARMUP),
            subjects.told,
            "переносимый обязан узнать и о согласии, и о задержке");
    }

    @Test
    void acceptingATpaHereWarnsTheAcceptorHimself() {
        requests.answered = TeleportRequests.Reply.accepted("Alex", STEVE, warming(STEVE));

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.TPACCEPT), context);

        assertTrue(
            context.last()
                .is(EssentialsMessages.WARMUP),
            "по /tpahere идёт сам принявший, значит и предупреждение его");
        assertTrue(subjects.told.isEmpty(), "самому себе мод в личку не пишет");
    }

    @Test
    void aCarriedPlayerOnCooldownIsNamedToTheAcceptorAndWarnedHimself() {
        requests.answered = TeleportRequests.Reply.cooling("Steve", ALEX, 90_000L);

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.TPACCEPT), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_MOVED_COOLDOWN),
            "принявшему говорят, кого именно ещё нельзя нести");
        assertEquals("Alex", context.last().arguments.get(0));
        assertEquals("1m 30s", context.last().arguments.get(1));
        assertEquals(Collections.singletonList(ALEX + " " + EssentialsMessages.ERROR_COOLDOWN), subjects.told);
    }

    @Test
    void acceptingATpaHereOnYourOwnCooldownRefusesYouWithoutNamingAnybody() {
        requests.answered = TeleportRequests.Reply.cooling("Alex", STEVE, 90_000L);

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.TPACCEPT), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_COOLDOWN));
        assertEquals("1m 30s", context.last().arguments.get(0));
        assertTrue(subjects.told.isEmpty(), "несут самого принявшего, в личку писать некому");
    }

    @Test
    void aFailedTpaMoveIsReportedToBothSidesInsteadOfAFalseSuccess() {
        requests.answered = TeleportRequests.Reply.accepted("Alex", ALEX, refused(ALEX, CancelReason.UNSAFE));

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.TPACCEPT), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(EssentialsMessages.FAILED_UNSAFE),
            "принявший не должен читать «принято», когда перенос не состоялся");
        assertEquals(Collections.singletonList(ALEX + " " + EssentialsMessages.FAILED_UNSAFE), subjects.told);
    }

    private static TeleportJob warming(UUID moved) {
        return TeleportJob.starting(
            5L,
            TeleportRequest.builder(moved, HERE, TeleportCause.TPA)
                .build());
    }

    private static TeleportJob refused(UUID moved, CancelReason reason) {
        return warming(moved).stopped(reason);
    }

    @Test
    void acceptPassesTheNickOnAndReportsWhatTheBoardDid() {
        TestCommandContext single = new TestCommandContext();
        execute(root(CommandRoots.TPACCEPT), single);
        assertNull(requests.askedName, "без аргумента доска выбирает сама");
        assertTrue(
            single.last()
                .is(EssentialsMessages.REQUEST_ACCEPTED));
        assertEquals("Alex", single.last().arguments.get(0));

        TestCommandContext named = new TestCommandContext().set("player", "Alex");
        execute(root(CommandRoots.TPACCEPT), named);
        assertEquals("Alex", requests.askedName);

        requests.answered = TeleportRequests.Reply.ambiguous(Arrays.asList("Alex", "Notch"));
        TestCommandContext several = new TestCommandContext();
        execute(root(CommandRoots.TPACCEPT), several);
        assertTrue(several.last().error);
        assertTrue(
            several.last()
                .is(EssentialsMessages.ERROR_MANY_REQUESTS));
        assertEquals("Alex, Notch", several.last().arguments.get(0));

        requests.answered = TeleportRequests.Reply.plain(TeleportRequests.Answer.NONE);
        TestCommandContext empty = new TestCommandContext();
        execute(root(CommandRoots.TPACCEPT), empty);
        assertTrue(
            empty.last()
                .is(EssentialsMessages.ERROR_NO_REQUEST));
    }

    @Test
    void denyAnswersWithItsOwnLine() {
        requests.answered = TeleportRequests.Reply.of(TeleportRequests.Answer.DENIED, "Alex");

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.TPDENY), context);

        assertTrue(
            context.last()
                .is(EssentialsMessages.REQUEST_DENIED));
        assertEquals("Alex", context.last().arguments.get(0));
    }

    @Test
    void cancellingWithoutAnOutgoingRequestSaysSo() {
        requests.cancelled = TeleportRequests.Reply.plain(TeleportRequests.Answer.NONE);

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.TPACANCEL), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_NO_OUTGOING));
    }

    @Test
    void cancellingNamesTheSideThatWasAsked() {
        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.TPACANCEL), context);

        assertTrue(
            context.last()
                .is(EssentialsMessages.REQUEST_WITHDRAWN));
        assertEquals("Alex", context.last().arguments.get(0));
    }

    @Test
    void toggleAnswersTheStateItGotBack() {
        requests.toggled = false;
        TestCommandContext off = new TestCommandContext();
        execute(root(CommandRoots.TPATOGGLE), off);
        assertTrue(
            off.last()
                .is(EssentialsMessages.REQUESTS_OFF));

        requests.toggled = true;
        TestCommandContext on = new TestCommandContext();
        execute(root(CommandRoots.TPATOGGLE), on);
        assertTrue(
            on.last()
                .is(EssentialsMessages.REQUESTS_ON));
    }

    @Test
    void ecancelOnSomeoneElseNeedsTheAdminNode() {
        teleports.cancelled = TeleportJob.starting(
            1L,
            TeleportRequest.builder(ALEX, HERE, TeleportCause.HOME)
                .build());

        TestCommandContext denied = new TestCommandContext().set("player", "Alex");
        execute(root(CommandRoots.ECANCEL), denied);
        assertTrue(denied.last().error);
        assertTrue(
            denied.last()
                .is(CommandMessages.NO_PERMISSION));

        subjects.nodes.add(Nodes.ADMIN_TELEPORT);
        TestCommandContext allowed = new TestCommandContext().set("player", "Alex");
        execute(root(CommandRoots.ECANCEL), allowed);
        assertTrue(
            allowed.last()
                .is(EssentialsMessages.CANCELLED_OTHER));
    }

    @Test
    void ecancelSaysWhenItTookTheWaitingJobInsteadOfTheStartedOne() {
        teleports.active = TeleportJob.starting(
            1L,
            TeleportRequest.builder(STEVE, HERE, TeleportCause.HOME)
                .build())
            .moving();
        teleports.cancelled = TeleportJob.starting(
            2L,
            TeleportRequest.builder(STEVE, NETHER, TeleportCause.SPAWN)
                .build());

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.ECANCEL), context);

        assertTrue(
            context.last()
                .is(EssentialsMessages.CANCELLED_WAITING),
            "снята не та работа, о которой думает игрок, значит ответ обязан это сказать");
    }

    @Test
    void ecancelWithoutAJobSaysSo() {
        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.ECANCEL), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_NO_JOB));
    }

    @Test
    void reloadGoesThroughTheMaintenance() {
        TestCommandContext context = new TestCommandContext();

        execute(child(root(CommandRoots.ESSENTIALS), "reload"), context);

        assertEquals(1, maintenance.calls);
        assertTrue(
            context.last()
                .is(EssentialsMessages.RELOAD_DONE));
    }

    @Test
    void failedReloadIsReportedAsFailure() {
        maintenance.answer = StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, "warps.json");

        TestCommandContext context = new TestCommandContext();
        execute(child(root(CommandRoots.ESSENTIALS), "reload"), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_PROVIDER_FAILED));
    }

    @Test
    void cooldownBranchShowsAndClears() {
        teleports.cooldowns.put(TeleportCause.WARP, Long.valueOf(5_000L));
        CommandNode branch = child(root(CommandRoots.ESSENTIALS), "cooldown");

        TestCommandContext shown = new TestCommandContext().set("player", "Alex");
        execute(branch, shown);
        assertTrue(
            shown.last()
                .is(EssentialsMessages.COOLDOWNS));
        assertEquals("warp 5s", shown.last().arguments.get(1));

        TestCommandContext cleared = new TestCommandContext().set("player", "Alex")
            .set("action", "clear");
        execute(branch, cleared);
        assertTrue(
            cleared.last()
                .is(EssentialsMessages.COOLDOWNS_CLEARED));

        teleports.clearAnswer = StoreResult.failure(StoreResult.Failure.NOT_FOUND, "Alex");
        TestCommandContext nothing = new TestCommandContext().set("player", "Alex")
            .set("action", "clear");
        execute(branch, nothing);
        assertTrue(
            nothing.last()
                .is(EssentialsMessages.COOLDOWNS_EMPTY),
            "снимать было нечего, значит и рапорт другой");
    }

    @Test
    void aRefusedCooldownWriteIsNotReportedAsAnEmptyPlayer() {
        teleports.clearAnswer = StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, "disk is full");

        TestCommandContext context = new TestCommandContext().set("player", "Alex")
            .set("action", "clear");
        execute(child(root(CommandRoots.ESSENTIALS), "cooldown"), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_PROVIDER_FAILED),
            "отказ хранилища нельзя выдавать за «кулдаунов нет»");
    }

    @Test
    void jobsBranchListsWhatIsRunning() {
        CommandNode branch = child(root(CommandRoots.ESSENTIALS), "jobs");

        TestCommandContext empty = new TestCommandContext();
        execute(branch, empty);
        assertTrue(
            empty.last()
                .is(EssentialsMessages.JOBS_EMPTY));

        teleports.running.add(
            TeleportJob.starting(
                7L,
                TeleportRequest.builder(ALEX, HERE, TeleportCause.WARP)
                    .build()));
        TestCommandContext listed = new TestCommandContext();
        execute(branch, listed);
        assertTrue(
            listed.last()
                .is(EssentialsMessages.JOBS));
        assertTrue(
            String.valueOf(listed.last().arguments.get(1))
                .startsWith("Alex WARMUP warp"));
    }

    @Test
    void warmupIsAnnouncedAndCorrectionIsAdmitted() {
        homes.owned.put("home", HomeRecord.of("home", HERE, 1L));

        TestCommandContext warming = new TestCommandContext();
        execute(root(CommandRoots.HOME), warming);
        assertTrue(
            warming.last()
                .is(EssentialsMessages.WARMUP));

        teleports.outcome = TeleportJob.State.DONE;
        teleports.landing = NETHER;
        TestCommandContext corrected = new TestCommandContext();
        execute(root(CommandRoots.HOME), corrected);
        assertTrue(
            corrected.last()
                .is(EssentialsMessages.CORRECTED));
    }

    @Test
    void exactLandingIsNotAnnouncedAsACorrection() {
        homes.owned.put("home", HomeRecord.of("home", HERE, 1L));
        teleports.outcome = TeleportJob.State.DONE;

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.HOME), context);

        assertTrue(context.quiet(), "точное приземление не требует объяснений");
    }

    @Test
    void registerHandsOverEveryEnabledRoot() {
        CommandTestStubs.Commands service = new CommandTestStubs.Commands();

        commands.register(service);

        assertEquals(
            new ArrayList<>(
                CommandRoots.factoryAliases()
                    .keySet()),
            service.names());
    }

    @Test
    void theRandomTeleportGoesThroughTheEngineWithItsOwnCause() {
        Point target = Point.of(0, 1200.5D, 70.0D, -800.5D);
        spots.reply = RandomSpots.Reply.found(target, 2);

        execute(root(CommandRoots.RTP), new TestCommandContext());

        assertEquals(Collections.singletonList(HERE), spots.asked, "поиск считает кольцо от места игрока");
        assertEquals(1, teleports.asked.size());
        TeleportRequest asked = teleports.asked.get(0);
        assertEquals(TeleportCause.RANDOM, asked.cause());
        assertEquals(target, asked.destination());
        assertTrue(asked.safeSpot(), "движок проверяет точку ещё раз");
    }

    @Test
    void theRandomTeleportWaitsForItsOwnCooldown() {
        teleports.cooldowns.put(TeleportCause.RANDOM, Long.valueOf(30_000L));

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.RTP), context);

        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_COOLDOWN));
        assertTrue(spots.asked.isEmpty(), "ждущему игроку чанки не перебирают");
        assertTrue(teleports.asked.isEmpty());
    }

    @Test
    void everyRefusalOfTheRandomSearchHasItsOwnLine() {
        assertSpotRefusal(RandomSpots.Outcome.OFF, EssentialsMessages.ERROR_RTP_OFF);
        assertSpotRefusal(RandomSpots.Outcome.WRONG_WORLD, EssentialsMessages.ERROR_RTP_WORLD);
        assertSpotRefusal(RandomSpots.Outcome.NO_WORLD, EssentialsMessages.FAILED_DIMENSION_MISSING);
        assertSpotRefusal(RandomSpots.Outcome.NOT_FOUND, EssentialsMessages.ERROR_RTP_NOT_FOUND);
    }

    @Test
    void theRefusalCountsTheTries() {
        spots.reply = RandomSpots.Reply.refused(RandomSpots.Outcome.NOT_FOUND, 12);

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.RTP), context);

        assertEquals(Integer.valueOf(12), context.last().arguments.get(0));
    }

    @Test
    void theSpawnFallbackIsToldBeforeTheMove() {
        Point spawn = Point.of(0, 0.5D, 64.0D, 0.5D);
        spots.reply = RandomSpots.Reply.spawn(spawn, 8);

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.RTP), context);

        assertTrue(
            context.sent()
                .get(0)
                .is(EssentialsMessages.RTP_SPAWN));
        assertEquals(
            Integer.valueOf(8),
            context.sent()
                .get(0).arguments.get(0));
        assertEquals(
            TeleportCause.RANDOM,
            teleports.asked.get(0)
                .cause());
        assertEquals(
            spawn,
            teleports.asked.get(0)
                .destination());
    }

    @Test
    void theConsoleIsNotSentAnywhereRandomly() {
        subjects.self = null;

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.RTP), context);

        assertTrue(
            context.last()
                .is(EssentialsMessages.ERROR_SENDER_NOT_PLAYER));
        assertTrue(spots.asked.isEmpty());
    }

    private void assertSpotRefusal(RandomSpots.Outcome outcome, String key) {
        teleports.asked.clear();
        spots.reply = RandomSpots.Reply.refused(outcome, 8);

        TestCommandContext context = new TestCommandContext();
        execute(root(CommandRoots.RTP), context);

        assertTrue(context.last().error, key);
        assertTrue(
            context.last()
                .is(key),
            key);
        assertTrue(teleports.asked.isEmpty(), key);
    }

    private void assertRefusal(String rootName, String key) {
        subjects.told.clear();
        TestCommandContext context = new TestCommandContext().set("player", "Alex");
        execute(root(rootName), context);

        assertTrue(context.last().error, key);
        assertTrue(
            context.last()
                .is(key),
            key);
    }

    private CommandNode root(String name) {
        for (CommandNode node : commands.allRoots()) {
            if (node.name()
                .equals(name)) {
                return node;
            }
        }
        throw new AssertionError("No root " + name);
    }

    private List<String> visible(CommandNode branch) {
        List<String> names = new ArrayList<>();
        for (CommandNode node : branch.children()) {
            String permission = node.permissionNode();
            if (permission == null || subjects.nodes.contains(permission)) {
                names.add(node.name());
            }
        }
        return names;
    }

    private static List<String> names(List<CommandNode> nodes) {
        List<String> names = new ArrayList<>();
        for (CommandNode node : nodes) {
            names.add(node.name());
        }
        return names;
    }

    private static CommandNode child(CommandNode node, String name) {
        for (CommandNode candidate : node.children()) {
            if (candidate.name()
                .equals(name)) {
                return candidate;
            }
        }
        throw new AssertionError("No child " + name + " under " + node.name());
    }

    private static void execute(CommandNode node, TestCommandContext context) {
        assertNotNull(node.action(), node.name());
        for (ArgumentSpec spec : node.arguments()) {
            if (!context.has(spec.name())) {
                continue;
            }
            Object raw = context.get(spec.name());
            context.put(
                spec.name(),
                spec.type()
                    .parse(String.valueOf(raw)));
        }
        node.action()
            .run(context);
    }
}
