package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.sim.BattleView;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Sim-seconds between commander-tier slow ticks. The squad-GOAP replan
     * loop runs every {@code Planner.REPLAN_PERIOD} (2s today);
     * the commander runs at a slower cadence so strategic assignments don't
     * thrash. Set so each commander tick is roughly bracketed by one full
     * GOAP replan cycle — gives squads a chance to act on a fresh assignment
     * before the commander considers reassigning. Tune in playtest.
     */
    public static final float COMMANDER_TICK_PERIOD = 2.5f;

    private final Map<Faction, MissionCommand> commanders = new EnumMap<>(Faction.class);
    private final Map<Faction, CommanderSnapshot<?>> snapshots = new EnumMap<>(Faction.class);
    private final AssignmentArbiter assignments = new AssignmentArbiter();
    private static final Map<AutonomousMissionCommand<?, ?>, AssignmentArbiter>
            DIRECT_SERVICES = java.util.Collections.synchronizedMap(new WeakHashMap<>());

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
            MissionCommand previous = commanders.put(faction, commander);
            if (previous != commander) snapshots.remove(faction);
        }
    }

    /** The commander for {@code faction}, or {@code null} if none is wired. */
    public MissionCommand getCommander(Faction faction) {
        return commanders.get(faction);
    }

    public boolean isEmpty() { return commanders.isEmpty(); }

    /** Latest committed common snapshot for one perspective, or {@code null}. */
    public CommanderSnapshot<?> snapshot(Faction faction) {
        return snapshots.get(faction);
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
        Map<Faction, String> issuers = new EnumMap<>(Faction.class);
        for (Map.Entry<Faction, MissionCommand> entry : commanders.entrySet()) {
            if (entry.getValue() instanceof AutonomousMissionCommand<?, ?> autonomous) {
                issuers.put(entry.getKey(), autonomous.strategyId());
            }
        }
        assignments.synchronizeCompatibilityAssignments(sim, issuers);
        CommandTopology topology = CommandTopology.freeze(sim);
        CommandAssignmentSnapshot assignmentFrame = assignments.snapshot();

        List<FrozenCommand> frozen = new ArrayList<>();
        List<MissionCommand> legacy = new ArrayList<>();
        for (MissionCommand command : commanders.values()) {
            if (command instanceof AutonomousMissionCommand<?, ?> autonomous) {
                frozen.add(freeze(autonomous, sim, topology, assignmentFrame));
            } else {
                legacy.add(command);
            }
        }

        List<PreparedCommand> prepared = new ArrayList<>(frozen.size());
        for (FrozenCommand command : frozen) prepared.add(plan(command));

        // No plan may observe another side's newly committed assignment: every
        // frame and plan exists before this loop begins.
        for (PreparedCommand command : prepared) commit(command, sim, topology);
        for (MissionCommand command : legacy) command.tick(sim);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static FrozenCommand freeze(AutonomousMissionCommand command,
                                        BattleView sim,
                                        CommandTopology topology,
                                        CommandAssignmentSnapshot assignments) {
        CommandFrame frame = command.freeze(sim, topology, assignments);
        return new FrozenCommand(command, frame);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static PreparedCommand plan(FrozenCommand frozen) {
        AutonomousMissionCommand command = frozen.command;
        CommandPlan<?> plan = command.plan(frozen.frame);
        if (plan.perspective() != frozen.command.faction()) {
            throw new IllegalStateException("command plan perspective does not match registration");
        }
        return new PreparedCommand(frozen.command, plan);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void commit(PreparedCommand prepared, BattleView sim,
                        CommandTopology topology) {
        CommanderSnapshot snapshot = assignments.commit(prepared.plan, sim, topology);
        snapshots.put(snapshot.perspective(), snapshot);
        prepared.command.publish(snapshot);
    }

    static <F extends CommandFrame, D> void runSingle(
            AutonomousMissionCommand<F, D> command, BattleView sim) {
        AssignmentArbiter arbiter = DIRECT_SERVICES.computeIfAbsent(command,
                ignored -> new AssignmentArbiter());
        arbiter.synchronizeCompatibilityAssignments(sim,
                Map.of(command.faction(), command.strategyId()));
        CommandTopology topology = CommandTopology.freeze(sim);
        F frame = command.freeze(sim, topology, arbiter.snapshot());
        CommandPlan<D> plan = command.plan(frame);
        if (plan.perspective() != command.faction()) {
            throw new IllegalStateException(
                    "command plan perspective does not match registration");
        }
        command.publish(arbiter.commit(plan, sim, topology));
    }

    private record PreparedCommand(AutonomousMissionCommand<?, ?> command,
                                   CommandPlan<?> plan) { }

    private record FrozenCommand(AutonomousMissionCommand<?, ?> command,
                                 CommandFrame frame) { }
}
