package com.mrleonardos.codeessentials.platform;

import java.util.Random;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.adapter.RoleServices;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigRoles;
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
import com.mrleonardos.codeessentials.api.teleport.TeleportService;
import com.mrleonardos.codeessentials.internal.EssentialsClaim;
import com.mrleonardos.codeessentials.internal.EssentialsRole;
import com.mrleonardos.codeessentials.internal.EssentialsRules;
import com.mrleonardos.codeessentials.internal.EssentialsSection;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.Listeners;
import com.mrleonardos.codeessentials.internal.SharedSettings;
import com.mrleonardos.codeessentials.internal.command.CommandRoots;
import com.mrleonardos.codeessentials.internal.command.EssentialsCommands;
import com.mrleonardos.codeessentials.internal.engine.Cooldowns;
import com.mrleonardos.codeessentials.internal.engine.EngineRules;
import com.mrleonardos.codeessentials.internal.engine.RandomFinder;
import com.mrleonardos.codeessentials.internal.engine.RequestBoard;
import com.mrleonardos.codeessentials.internal.engine.SafeSpotFinder;
import com.mrleonardos.codeessentials.internal.engine.TeleportEngine;
import com.mrleonardos.codeessentials.internal.service.BackServiceImpl;
import com.mrleonardos.codeessentials.internal.service.HomeServiceImpl;
import com.mrleonardos.codeessentials.internal.service.SpawnFile;
import com.mrleonardos.codeessentials.internal.service.SpawnServiceImpl;
import com.mrleonardos.codeessentials.internal.service.StateWriter;
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

    private ConfigService configs;
    private ConfigFile<EssentialsSection> section;
    private ConfigFile<EssentialsSettings> settings;
    private ConfigFile<CommandRoots> roots;
    private ConfigFile<WarpsFile> warpsFile;
    private ConfigFile<SpawnFile> spawnFile;

    private ServerThreads threads;
    private CorePermissions rights;
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
    private volatile SharedSettings shared;
    private volatile EngineRules rules;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("CodeEssentials {} is starting up", Tags.VERSION);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        configs = CodeApi.configs();
        section = configs.section(EssentialsSection.spec());
        CodeApi.adapters()
            .declareRole(EssentialsRole.spec());
        CodeApi.adapters()
            .offer(new EssentialsClaim(this::build));
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        String owner = CodeApi.adapters()
            .owner(ConfigRoles.ESSENTIALS);
        if (!EssentialsClaim.NAME.equals(owner)) {
            LOG.info(
                "Role {} is held by {}, CodeEssentials stands aside: no command roots, no files of its own, no listeners",
                ConfigRoles.ESSENTIALS,
                owner == null ? ServiceBridge.NOBODY : owner);
            return;
        }
        rights.reviewMeta(
            CodeApi.adapters()
                .missing(ConfigRoles.PERMISSIONS));
        threads.attach(Thread.currentThread());
        EssentialsApi.freeze();
        writer.start();
        board.start();
        summary();
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (board == null) {
            return;
        }
        board.stop();
        tracker.rest();
        writer.stop();
    }

    private RoleServices build() {
        LongSupplier clock = System::currentTimeMillis;
        threads = new ServerThreads(CodeApi.scheduler());

        settings = configs.open(EssentialsSettings.spec());
        roots = configs.open(CommandRoots.spec());
        warpsFile = configs.open(WarpsFile.spec());
        spawnFile = configs.open(SpawnFile.spec());
        refresh();

        Supplier<EssentialsSettings> config = settings::get;
        Supplier<SharedSettings> common = this::shared;
        Listeners listeners = new Listeners();
        rights = new CorePermissions(common, LOG);

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
            shared.provider(),
            EssentialsApi::store,
            threads,
            LOG,
            clock,
            shared.autosaveTicks());
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
        homes = new HomeServiceImpl(config, common, rights, state, listeners::homes, threads, LOG);
        warps = new WarpServiceImpl(config, common, warpsFile, worlds, LOG);
        spawns = new SpawnServiceImpl(common, spawnFile, LOG);

        ServiceBridge.register(PlayerDataStore.class, store, ServicePriority.BUILTIN);
        ServiceBridge.register(SafeSpotPolicy.class, policy, ServicePriority.BUILTIN);
        ServiceBridge.install(listeners);

        Supplier<HomeService> homeHolder = ServiceBridge.holder(HomeService.class, homes);
        Supplier<WarpService> warpHolder = ServiceBridge.holder(WarpService.class, warps);
        Supplier<SpawnService> spawnHolder = ServiceBridge.holder(SpawnService.class, spawns);
        NameResolver names = new NameResolver(writer::state);

        new EssentialsCommands(
            config,
            common,
            roots::get,
            ServiceBridge.holder(TeleportService.class, engine),
            homeHolder,
            warpHolder,
            spawnHolder,
            ServiceBridge.holder(BackService.class, backs),
            new RequestBridge(board),
            new RandomFinder(worlds, () -> policy, this::rules, spawnHolder, new Random()),
            new PlatformArguments(names, homeHolder, warpHolder, rights),
            new SenderSubjects(names, rights),
            new PlatformMaintenance(
                configs,
                settings,
                section,
                roots,
                warpsFile,
                spawnFile,
                this::rules,
                this::refresh,
                LOG),
            LOG).register(CodeApi.commands());
        completeRoots();

        tracker = new PlayerTracker(engine);
        lifecycle = new ForgeLifecycle(engine, board, state, new RespawnChoice(spawnHolder), threads);
        FMLCommonHandler.instance()
            .bus()
            .register(lifecycle);
        FMLCommonHandler.instance()
            .bus()
            .register(tracker);
        MinecraftForge.EVENT_BUS.register(lifecycle);

        return RoleServices.builder()
            .add(TeleportService.class, engine)
            .add(HomeService.class, homes)
            .add(WarpService.class, warps)
            .add(SpawnService.class, spawns)
            .add(BackService.class, backs)
            .build();
    }

    private EngineRules rules() {
        return rules;
    }

    private SharedSettings shared() {
        return shared;
    }

    private void completeRoots() {
        if (!roots.get()
            .filledIn()) {
            return;
        }
        try {
            roots.save();
            LOG.info("Records of the new command root(s) are written to {}", CommandRoots.FILE_NAME);
        } catch (RuntimeException failure) {
            LOG.warn(
                "{} was not written, the new command root(s) stay only in memory: {}",
                CommandRoots.FILE_NAME,
                failure.toString(),
                failure);
        }
    }

    private void refresh() {
        EssentialsSettings current = settings.get();
        ceilings = current.ceilings(LOG);
        shared = SharedSettings.of(configs, section.get());
        current.backMode(LOG);
        current.centerMode(LOG);
        current.failureMode(LOG);
        rules = EssentialsRules.of(current, shared, ceilings);
    }

    private String provider() {
        return EssentialsApi.store(shared.provider())
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
