package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Per-faction strategic commanders. A faction with no entry here has no
 * commander tier active — its squads run on ambient ENGAGEMENT goals with
 * {@code Squad.assignedObjective} left null. Missions that want
 * commander-driven coordination (Conquest spreads marine squads across
 * charge sites via {@link ConquestCommand}) install one via
 * {@link #setCommander(Faction, MissionCommand)} during {@code BattleSetup}.
 *
 * <p>Owned by {@link com.dillon.starsectormarines.battle.sim.BattleSimulation};
 * sibling slice to {@link com.dillon.starsectormarines.battle.combat.fx.EffectsService},
 * {@link FogOfWarService}, and
 * {@link com.dillon.starsectormarines.battle.combat.ShotService}.
 *
 * <p>{@link #tick(float, BattleView)} owns the COMMANDER_TICK_PERIOD cadence.
 * Migrated autonomous commands freeze every perspective before any strategy
 * plans, then commit every proposal through one assignment arbiter. Legacy
 * commands retain their old direct tick temporarily while missions migrate.
 */
public final class CommanderService {

    /** Shares the opt-in host CPU diagnostic with public-topology freezing. */
    private static final String CPU_PROFILE_PROPERTY = "battle.profile.commandTopologyCpu";

    private static final class CpuClock {
        private static final ThreadMXBean BEAN = ManagementFactory.getThreadMXBean();

        static long now() {
            return BEAN.isCurrentThreadCpuTimeSupported() && BEAN.isThreadCpuTimeEnabled()
                    ? BEAN.getCurrentThreadCpuTime() : -1L;
        }
    }

    /**
     * Sim-seconds between commander-tier slow ticks. The squad-GOAP replan
     * loop runs every {@code Planner.REPLAN_PERIOD} (2s today);
     * the commander runs at a slower cadence so strategic assignments don't
     * thrash. Set so each commander tick is roughly bracketed by one full
     * GOAP replan cycle — gives squads a chance to act on a fresh assignment
     * before the commander considers reassigning. Tune in playtest.
     */
    public static final float COMMANDER_TICK_PERIOD = 2.5f;

    private final Map<Faction, Registration> commanders = new EnumMap<>(Faction.class);
    private final Map<Faction, CommanderSnapshot<?>> snapshots = new EnumMap<>(Faction.class);
    private final AssignmentArbiter assignments = new AssignmentArbiter();
    private CommandTopology cachedTopology;
    private long cachedGridRevision = Long.MIN_VALUE;
    private long cachedTopologyRevision = Long.MIN_VALUE;
    private static final Map<AutonomousMissionCommand<?, ?>, AssignmentArbiter>
            DIRECT_SERVICES = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Sim-seconds accumulated since the last commander slow-tick. When this
     * crosses {@link #COMMANDER_TICK_PERIOD}, every registered commander is
     * dispatched via the {@code tickHandler} supplied to {@link #tick}.
     */
    private float accumulator = 0f;

    /**
     * Install (or replace) the strategic commander for one faction. Pass
     * {@code null} to clear an existing commander — the faction's squads
     * fall back to ambient ENGAGEMENT goals. Typically called once during
     * {@code BattleSetup} per faction that wants the layer.
     */
    public void setCommander(Faction faction, MissionCommand commander) {
        if (commander == null) {
            commanders.remove(faction);
            snapshots.remove(faction);
        } else {
            install(faction, new Registration(commander, null));
        }
    }

    /** Installs a frame-only strategy with its trusted battle disclosure. */
    public <F extends CommandFrame, D> void setAutonomousCommander(
            Faction faction, AutonomousMissionCommand<F, D> commander,
            CommandFrameDisclosure<F> disclosure) {
        if (commander == null) {
            setCommander(faction, null);
            return;
        }
        install(faction, new Registration(commander,
                Objects.requireNonNull(disclosure, "disclosure")));
    }

    private void install(Faction faction, Registration registration) {
        if (registration.strategy().faction() != faction) {
            throw new IllegalArgumentException(
                    "commander faction does not match registration");
        }
        Registration previous = commanders.put(faction, registration);
        if (previous == null || previous.strategy() != registration.strategy()
                || previous.disclosure() != registration.disclosure()) {
            snapshots.remove(faction);
        }
    }

    /** The commander for {@code faction}, or {@code null} if none is wired. */
    public CommandStrategy getCommander(Faction faction) {
        Registration registration = commanders.get(faction);
        return registration != null ? registration.strategy() : null;
    }

    public boolean isEmpty() { return commanders.isEmpty(); }

    /** Latest committed common snapshot for one perspective, or {@code null}. */
    public CommanderSnapshot<?> snapshot(Faction faction) {
        return snapshots.get(faction);
    }

    /**
     * Current authoritative command-ledger entry for one squad, independent
     * of whether its mission publishes a {@link CommanderSnapshot}.
     */
    public CommandDirective activeDirective(int squadId) {
        return assignments.activeDirective(squadId);
    }

    public AssignmentArbiter assignments() {
        return assignments;
    }

    /**
     * Accumulates {@code dt} into the cadence timer and, when it crosses
     * {@link #COMMANDER_TICK_PERIOD}, dispatches every registered commander
     * through {@code tickHandler}. Skipped entirely when no commanders are
     * registered (the common case for non-Conquest / non-Assault missions).
     * Per-faction order is enum-declaration order via the EnumMap —
     * deterministic across runs.
     */
    public void tick(float dt, BattleView sim) {
        if (commanders.isEmpty()) return;
        accumulator += dt;
        if (accumulator < COMMANDER_TICK_PERIOD) return;
        accumulator -= COMMANDER_TICK_PERIOD;
        runPulse(sim);
    }

    private void runPulse(BattleView sim) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        if (profile != null) {
            profile.record(TickInnerProfile.Bucket.COMMANDER_PULSE, 0L);
        }
        long stageStart = System.nanoTime();
        Map<Faction, String> issuers = new EnumMap<>(Faction.class);
        for (Map.Entry<Faction, Registration> entry : commanders.entrySet()) {
            if (entry.getValue().strategy()
                    instanceof AutonomousMissionCommand<?, ?> autonomous) {
                issuers.put(entry.getKey(), autonomous.strategyId());
            }
        }
        assignments.synchronizeCompatibilityAssignments(sim, issuers);
        record(profile, TickInnerProfile.Bucket.COMMANDER_SYNC, stageStart);

        stageStart = System.nanoTime();
        CommandTopology topology = freezeTopology(sim);
        record(profile, TickInnerProfile.Bucket.COMMANDER_TOPOLOGY_LOOKUP, stageStart);

        stageStart = System.nanoTime();
        long frameCpuStarted = profile != null && Boolean.getBoolean(CPU_PROFILE_PROPERTY)
                ? CpuClock.now() : -1L;
        List<FrozenCommand> frozen = new ArrayList<>();
        List<MissionCommand> legacy = new ArrayList<>();
        try {
            CommandAssignmentSnapshot assignmentFrame = assignments.snapshot();
            for (Registration registration : commanders.values()) {
                if (registration.strategy()
                        instanceof AutonomousMissionCommand<?, ?> autonomous) {
                    frozen.add(freeze(autonomous, registration.disclosure(), sim,
                            topology, assignmentFrame));
                } else {
                    legacy.add((MissionCommand) registration.strategy());
                }
            }
        } finally {
            record(profile, TickInnerProfile.Bucket.COMMANDER_FRAME, stageStart);
            if (frameCpuStarted >= 0L) {
                long cpuEnded = CpuClock.now();
                if (cpuEnded >= frameCpuStarted) profile.record(
                        TickInnerProfile.Bucket.COMMANDER_FRAME_CPU,
                        cpuEnded - frameCpuStarted);
            }
        }

        stageStart = System.nanoTime();
        List<PreparedCommand> prepared = new ArrayList<>(frozen.size());
        for (FrozenCommand command : frozen) prepared.add(plan(command));
        record(profile, TickInnerProfile.Bucket.COMMANDER_PLAN, stageStart);

        // No plan may observe another side's newly committed assignment: every
        // frame and plan exists before this loop begins.
        stageStart = System.nanoTime();
        for (PreparedCommand command : prepared) commit(command, sim, topology);
        SquadDirectiveControl directives = assignments.control(sim);
        for (MissionCommand command : legacy) command.tick(sim, directives);
        record(profile, TickInnerProfile.Bucket.COMMANDER_COMMIT, stageStart);
    }

    private static void record(TickInnerProfile profile,
                               TickInnerProfile.Bucket bucket,
                               long stageStart) {
        if (profile != null) profile.record(bucket, System.nanoTime() - stageStart);
    }

    /** Reuses the immutable full-map snapshot until a flushed breach changes navigation. */
    CommandTopology freezeTopology(BattleView sim) {
        long gridRevision = sim.getNavigationGridRevision();
        long topologyRevision = sim.getNavigationTopologyRevision();
        if (cachedTopology == null
                || cachedGridRevision != gridRevision
                || cachedTopologyRevision != topologyRevision) {
            TickInnerProfile profile = TickInnerProfile.currentIfBound();
            long rebuildStart = System.nanoTime();
            cachedTopology = CommandTopology.freeze(sim);
            record(profile, TickInnerProfile.Bucket.COMMANDER_TOPOLOGY_REBUILD,
                    rebuildStart);
            cachedGridRevision = gridRevision;
            cachedTopologyRevision = topologyRevision;
        }
        return cachedTopology;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static FrozenCommand freeze(AutonomousMissionCommand command,
                                        CommandFrameDisclosure disclosure,
                                        BattleView sim,
                                        CommandTopology topology,
                                        CommandAssignmentSnapshot assignments) {
        CommandFrame frame = disclosure.freeze(sim, command.faction(), topology,
                assignments);
        return new FrozenCommand(command, frame);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static PreparedCommand plan(FrozenCommand frozen) {
        AutonomousMissionCommand command = frozen.command;
        CommandPlan<?> plan = command.plan(frozen.frame);
        if (plan.perspective() != frozen.command.faction()) {
            throw new IllegalStateException("command plan perspective does not match registration");
        }
        if (!plan.strategy().equals(frozen.command.strategyId())) {
            throw new IllegalStateException("command plan strategy does not match registration");
        }
        if (plan.tick() != frozen.frame.tick()) {
            throw new IllegalStateException("command plan tick does not match frozen frame");
        }
        return new PreparedCommand(frozen.command, plan);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void commit(PreparedCommand prepared, BattleView sim,
                        CommandTopology topology) {
        CommanderSnapshot snapshot = assignments.commit(prepared.plan, sim, topology);
        snapshot = prepared.command.reconcile(snapshot);
        snapshots.put(snapshot.perspective(), snapshot);
        prepared.command.publish(snapshot);
    }

    static <F extends CommandFrame, D> void runSingle(
            AutonomousMissionCommand<F, D> command,
            CommandFrameDisclosure<F> disclosure, BattleView sim) {
        AssignmentArbiter arbiter = DIRECT_SERVICES.computeIfAbsent(command,
                ignored -> new AssignmentArbiter());
        arbiter.synchronizeCompatibilityAssignments(sim,
                Map.of(command.faction(), command.strategyId()));
        CommandTopology topology = CommandTopology.freeze(sim);
        F frame = disclosure.freeze(sim, command.faction(), topology,
                arbiter.snapshot());
        CommandPlan<D> plan = command.plan(frame);
        if (plan.perspective() != command.faction()) {
            throw new IllegalStateException(
                    "command plan perspective does not match registration");
        }
        if (!plan.strategy().equals(command.strategyId())) {
            throw new IllegalStateException(
                    "command plan strategy does not match registration");
        }
        if (plan.tick() != frame.tick()) {
            throw new IllegalStateException(
                    "command plan tick does not match frozen frame");
        }
        CommanderSnapshot<D> snapshot = arbiter.commit(plan, sim, topology);
        snapshot = command.reconcile(snapshot);
        command.publish(snapshot);
    }

    private record PreparedCommand(AutonomousMissionCommand<?, ?> command,
                                   CommandPlan<?> plan) { }

    private record FrozenCommand(AutonomousMissionCommand<?, ?> command,
                                 CommandFrame frame) { }

    private record Registration(CommandStrategy strategy,
                                CommandFrameDisclosure<?> disclosure) { }
}
