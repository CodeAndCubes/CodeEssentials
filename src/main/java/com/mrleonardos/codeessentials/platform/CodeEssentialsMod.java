package com.mrleonardos.codeessentials.platform;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.service.ServicePriority;
import com.mrleonardos.codeessentials.Tags;
import com.mrleonardos.codeessentials.api.EssentialsApi;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.manage.BackService;
import com.mrleonardos.codeessentials.api.manage.HomeService;
import com.mrleonardos.codeessentials.api.manage.SpawnService;
import com.mrleonardos.codeessentials.api.manage.WarpService;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.model.SpawnTable;
import com.mrleonardos.codeessentials.api.store.PlayerDataStore;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotPolicy;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.api.teleport.TeleportService;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.Listeners;
import com.mrleonardos.codeessentials.internal.command.CommandRoots;
import com.mrleonardos.codeessentials.internal.command.EssentialsCommands;
import com.mrleonardos.codeessentials.internal.engine.BackLog;
import com.mrleonardos.codeessentials.internal.engine.Cooldowns;
import com.mrleonardos.codeessentials.internal.engine.EngineRules;
import com.mrleonardos.codeessentials.internal.engine.RequestBoard;
import com.mrleonardos.codeessentials.internal.engine.SafeSpotFinder;
import com.mrleonardos.codeessentials.internal.engine.TeleportEngine;
import com.mrleonardos.codeessentials.internal.service.BackServiceImpl;
import com.mrleonardos.codeessentials.internal.service.HomeServiceImpl;
import com.mrleonardos.codeessentials.internal.service.SpawnFile;
import com.mrleonardos.codeessentials.internal.service.SpawnServiceImpl;
import com.mrleonardos.codeessentials.internal.service.WarpServiceImpl;
import com.mrleonardos.codeessentials.internal.service.WarpsFile;
import com.mrleonardos.codeessentials.internal.store.EssentialsState;
import com.mrleonardos.codeessentials.internal.store.JsonPlayerDataStore;
import com.mrleonardos.codeessentials.internal.store.SingleWriterImpl;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

@Mod(
    modid = "codeessentials",
    name = "CodeEssentials",
    version = Tags.VERSION,
    dependencies = "required-after:codecore",
    acceptableRemoteVersions = "*")
public final class CodeEssentialsMod {

    public static final Logger LOG = LogManager.getLogger("CodeEssentials");

    private ConfigFile<EssentialsSettings> settings;
    private ConfigFile<CommandRoots> roots;
    private ConfigFile<WarpsFile> warpsFile;
    private ConfigFile<SpawnFile> spawnFile;

    private ServerThreads threads;
    private SingleWriterImpl writer;
    private JsonPlayerDataStore store;
    private TeleportEngine engine;
    private RequestBoard board;
    private PlayerTracker tracker;
    private ForgeLifecycle lifecycle;
    private HomeServiceImpl homes;
    private WarpServiceImpl warps;
    private SpawnServiceImpl spawns;
    private BackServiceImpl backs;

    private volatile EssentialsLimits ceilings;
    private volatile EngineRules rules;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("CodeEssentials {} is starting up", Tags.VERSION);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ConfigService configs = CodeApi.configs();
        LongSupplier clock = System::currentTimeMillis;
        threads = new ServerThreads(CodeApi.scheduler());

        settings = configs.open(EssentialsSettings.spec());
        roots = configs.open(CommandRoots.spec());
        warpsFile = configs.open(WarpsFile.spec());
        spawnFile = configs.open(SpawnFile.spec());
        refresh();

        Supplier<EssentialsSettings> config = settings::get;
        Listeners listeners = new Listeners();
        CorePermissions rights = new CorePermissions(LOG);

        store = JsonPlayerDataStore.create(configs, ceilings, clock, LOG);
        SafeSpotFinder builtin = new SafeSpotFinder();
        EssentialsApi.registerStore(store);
        EssentialsApi.registerPolicy(builtin);
        ChosenPolicy policy = new ChosenPolicy(
            () -> config.get()
                .policy(),
            builtin,
            LOG);

        writer = new SingleWriterImpl(
            store,
            config.get()
                .provider(),
            EssentialsApi::store,
            threads,
            LOG,
            clock,
            config.get()
                .autosaveTicks());
        StateWriter state = new StateWriter(writer);

        ServerWorlds worlds = new ServerWorlds(config, () -> policy, LOG);
        Cooldowns cooldowns = new Cooldowns(writer, rights, this::rules, clock);
        backs = new BackServiceImpl(config, rights, state, LOG);
        engine = new TeleportEngine(
            this::rules,
            worlds,
            rights,
            policy,
            new WorldMover(new ServerMoves(worlds), LOG),
            cooldowns,
            backs,
            listeners.teleports(),
            threads,
            clock,
            LOG);
        board = new RequestBoard(engine, worlds, cooldowns, this::rules, threads, clock);
        homes = new HomeServiceImpl(config, rights, state, listeners::homes, threads, LOG);
        warps = new WarpServiceImpl(config, warpsFile, worlds, LOG);
        spawns = new SpawnServiceImpl(config, spawnFile, LOG);

        ServicePriority priority = config.get()
            .priority(LOG);
        ServiceBridge.register(TeleportService.class, engine, priority);
        ServiceBridge.register(HomeService.class, homes, priority);
        ServiceBridge.register(WarpService.class, warps, priority);
        ServiceBridge.register(SpawnService.class, spawns, priority);
        ServiceBridge.register(BackService.class, backs, priority);
        ServiceBridge.register(PlayerDataStore.class, store, ServicePriority.BUILTIN);
        ServiceBridge.register(SafeSpotPolicy.class, policy, ServicePriority.BUILTIN);
        ServiceBridge.install(listeners);

        Supplier<HomeService> homeHolder = ServiceBridge.holder(HomeService.class, homes);
        Supplier<WarpService> warpHolder = ServiceBridge.holder(WarpService.class, warps);
        Supplier<SpawnService> spawnHolder = ServiceBridge.holder(SpawnService.class, spawns);
        NameResolver names = new NameResolver(writer::state);

        new EssentialsCommands(
            config,
            roots::get,
            ServiceBridge.holder(TeleportService.class, engine),
            homeHolder,
            warpHolder,
            spawnHolder,
            ServiceBridge.holder(BackService.class, backs),
            new RequestBridge(board),
            new PlatformArguments(names, homeHolder, warpHolder, rights),
            new SenderSubjects(names, rights),
            new PlatformMaintenance(settings, roots, warpsFile, spawnFile, this::refresh),
            LOG).register(CodeApi.commands());

        tracker = new PlayerTracker(engine);
        lifecycle = new ForgeLifecycle(engine, board, state, new RespawnChoice(spawnHolder), threads);
        FMLCommonHandler.instance()
            .bus()
            .register(lifecycle);
        FMLCommonHandler.instance()
            .bus()
            .register(tracker);
        MinecraftForge.EVENT_BUS.register(lifecycle);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        threads.attach(Thread.currentThread());
        EssentialsApi.freeze();
        writer.start();
        board.start();
        summary();
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        board.stop();
        tracker.rest();
        writer.stop();
    }

    private EngineRules rules() {
        return rules;
    }

    private void refresh() {
        ceilings = settings.get()
            .ceilings(LOG);
        rules = build();
    }

    private EngineRules build() {
        EssentialsSettings current = settings.get();
        EssentialsLimits held = ceilings;
        EngineRules.Builder builder = EngineRules.builder()
            .limits(held)
            .warmupSeconds(current.warmupSeconds(held))
            .moveRadius(current.warmupMoveRadius())
            .verticalMoveRadius(current.warmupMoveHeight())
            .cancelOnDamage(current.warmupCancelOnDamage())
            .requestRateSeconds(current.requestRateSeconds())
            .requestTimeoutSeconds(current.requestTimeoutSeconds(held))
            .maxPending(current.maxPending(held))
            .backDepth(current.defaultBackDepth(held))
            .backTrigger(trigger(current))
            .safeSpot(current.spotLimits(held));
        for (TeleportCause cause : TeleportCause.values()) {
            builder.cooldown(cause, current.cooldownSeconds(cause));
        }
        return builder.build();
    }

    private BackLog.Trigger trigger(EssentialsSettings current) {
        String mode = current.backMode(LOG);
        if (EssentialsSettings.BACK_NONE.equals(mode)) {
            return BackLog.Trigger.OFF;
        }
        if (EssentialsSettings.BACK_TELEPORT.equals(mode)) {
            return BackLog.Trigger.TELEPORT;
        }
        return EssentialsSettings.BACK_DEATH.equals(mode) ? BackLog.Trigger.DEATH : BackLog.Trigger.BOTH;
    }

    private String provider() {
        return EssentialsApi.store(
            settings.get()
                .provider())
            .map(PlayerDataStore::id)
            .orElseGet(store::id);
    }

    private void summary() {
        EssentialsState state = writer.state();
        int homeCount = 0;
        for (PlayerRecord player : state.players()
            .values()) {
            homeCount += player.homes()
                .size();
        }
        SpawnTable table = spawns.table();
        int spawnCount = table.byDimension()
            .size()
            + (table.global()
                .isPresent() ? 1 : 0);
        LOG.info(
            "CodeEssentials is up: storage {} holds {} player(s) and {} home(s), {} warp(s) and {} spawn point(s) are loaded",
            provider(),
            Integer.valueOf(
                state.players()
                    .size()),
            Integer.valueOf(homeCount),
            Integer.valueOf(
                warps.warps()
                    .size()),
            Integer.valueOf(spawnCount));
        LOG.info(
            "Services: TeleportService {}, HomeService {}, WarpService {}, SpawnService {}, BackService {}, SafeSpotPolicy {}",
            ServiceBridge.owner(TeleportService.class),
            ServiceBridge.owner(HomeService.class),
            ServiceBridge.owner(WarpService.class),
            ServiceBridge.owner(SpawnService.class),
            ServiceBridge.owner(BackService.class),
            ServiceBridge.owner(SafeSpotPolicy.class));
    }
}
