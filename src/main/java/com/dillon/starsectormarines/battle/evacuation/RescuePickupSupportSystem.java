package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Holds a capped local-militia line around the rescue pickup and dispatches a
 * visible replacement shuttle when casualties pull that line below strength.
 *
 * <p>The line is the allied evac cordon the company brings the survivors back
 * to (owner): its shuttles, militia and mech are {@link Faction#ALLY}, so the
 * player never commands them, never counts them as the company and never
 * boards or lands through their shuttles. The cordon replenishes from its own
 * pool, never the company's, for as long as any of the cohort is still out.
 *
 * <p>Where the pickup is fortified ({@link PickupCordonWorks}), each team the
 * shuttles set down at a post is then ordered into that post's stretch of
 * trench, and mans it as a garrison mans a run.
 */
public final class RescuePickupSupportSystem {

    public static final int TARGET_GUARDS = 20;
    public static final int REINFORCEMENT_FLOOR = 16;
    public static final int WAVE_SIZE = 4;
    public static final int TARGET_GUARD_SQUADS = 5;
    public static final int PICKUP_MECHS = 1;
    public static final float WAVE_INTERVAL_SECONDS = 12f;
    public static final float INITIAL_ARRIVAL_DELAY_SECONDS = 12f;
    private static final float INITIAL_SORTIE_STAGGER_SECONDS = 2f;

    private final CivilianEvacuationTracker tracker;
    private CivilianEvacuationPlacement placement;
    private float reinforcementLzX;
    private float reinforcementLzY;
    private float entryX;
    private float entryY;
    private float exitX;
    private float exitY;
    private int[] formationCells;
    /** Each formation point's trench post ({x, y} pairs, -1 where none), or null on an unfortified pickup. */
    private int[] trenchPosts;
    private MechVariant pickupMechVariant;
    private Random loadoutRng;
    private RiskLevel risk;
    private float accumulator = WAVE_INTERVAL_SECONDS;
    private float mechAccumulator = WAVE_INTERVAL_SECONDS;
    private boolean configured;
    /** Whether the initial line waits for {@link #fortify} to stand it in the trench rather than flying in. */
    private boolean lineDeferred;

    public RescuePickupSupportSystem(CivilianEvacuationTracker tracker) {
        if (tracker == null) throw new IllegalArgumentException("tracker is required");
        this.tracker = tracker;
    }

    /** Schedules the initial line's arrivals and arms its casualty-driven reserve. */
    public boolean configure(CivilianEvacuationPlacement placement,
                             float reinforcementLzX, float reinforcementLzY,
                             float entryX, float entryY,
                             float exitX, float exitY,
                             long seed, RiskLevel risk,
                             BattleSimulation sim) {
        return configure(placement, reinforcementLzX, reinforcementLzY, entryX, entryY, exitX, exitY, seed, risk,
                false, sim);
    }

    /**
     * As {@link #configure(CivilianEvacuationPlacement, float, float, float, float, float, float, long, RiskLevel,
     * BattleSimulation)}; where the line is {@code dugIn}, the pickup is a staging post the colony's defence force
     * set up before the company was called in, so its teams are not flown in: {@link #fortify} stands them in their
     * trench before the first tick. Only the mech and the reserve still come by air.
     */
    public boolean configure(CivilianEvacuationPlacement placement,
                             float reinforcementLzX, float reinforcementLzY,
                             float entryX, float entryY,
                             float exitX, float exitY,
                             long seed, RiskLevel risk, boolean dugIn,
                             BattleSimulation sim) {
        if (configured || placement == null || sim == null) return false;
        this.placement = placement;
        this.reinforcementLzX = reinforcementLzX;
        this.reinforcementLzY = reinforcementLzY;
        this.entryX = entryX;
        this.entryY = entryY;
        this.exitX = exitX;
        this.exitY = exitY;
        this.formationCells = placement.formationCells();
        if (formationCells.length != TARGET_GUARD_SQUADS * 2) return false;
        this.pickupMechVariant = (seed & 1L) == 0L
                ? MechVariant.BULWARK : MechVariant.WARDEN;
        this.loadoutRng = new Random(seed ^ 0x72C5A4D93E10B68FL);
        this.risk = risk != null ? risk : RiskLevel.LOW;
        configured = true;
        lineDeferred = dugIn;
        if (!dugIn) dispatchInitialMilitia(sim);
        dispatchInitialMech(sim);
        return true;
    }

    public void tick(float dt, BattleSimulation sim) {
        if (!configured || tracker.isSealed() || tracker.activeCount() == 0) return;
        float elapsed = Math.max(0f, dt);
        accumulator = Math.min(WAVE_INTERVAL_SECONDS, accumulator + elapsed);
        mechAccumulator = Math.min(WAVE_INTERVAL_SECONDS,
                mechAccumulator + elapsed);
        reinforceMilitia(sim);
        reinforceMech(sim);
        manTrench(sim);
    }

    /**
     * Gives the line the cordon's trench: each formation point's post, the trench
     * cell {@link PickupCordonWorks} chose for it. Set once, after {@link #configure}.
     * A dug-in line is stood in its trench here, each team on the run cells nearest
     * its post and already ordered to hold it; a team whose post has no run, or
     * every team when no works went up, flies in as an undug line does.
     */
    public void fortify(int[] posts, BattleSimulation sim) {
        if (!configured) return;
        if (posts != null && posts.length == formationCells.length) trenchPosts = posts.clone();
        if (!lineDeferred) return;
        lineDeferred = false;
        for (int point = 0; point < TARGET_GUARD_SQUADS; point++) {
            if (!standInTrench(sim, point)) dispatchMilitiaShuttle(sim, WAVE_SIZE, initialDelay(point), point);
        }
    }

    /** Stands one team of the line in its stretch of trench at setup; false where its post has no run. */
    private boolean standInTrench(BattleSimulation sim, int point) {
        if (trenchPosts == null || trenchPosts[point * 2] < 0) return false;
        int x = trenchPosts[point * 2], y = trenchPosts[point * 2 + 1];
        FightingPositionIndex index = sim.fightingPositions();
        int run = index.runIdAt(x, y);
        if (run < 0) return false;
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; i < index.runSize(run); i++) cells.add(new int[]{index.runCellX(run, i), index.runCellY(run, i)});
        cells.sort(Comparator.<int[]>comparingInt(c -> (c[0] - x) * (c[0] - x) + (c[1] - y) * (c[1] - y))
                .thenComparingInt(c -> c[1]).thenComparingInt(c -> c[0]));
        int squadId = sim.mintSquad(Faction.ALLY, UnitType.MILITIA);
        Squad squad = sim.getSquad(squadId);
        squad.rescuePickupGuard = true;
        sim.assignSquadCommand(ObjectiveAssignment.defendRun(squadId, x, y, PickupCordonWorks.POST_RADIUS, run),
                CommandAuthority.PAYLOAD, "rescue-pickup-support", "dug in at the staging post");
        MarineLoadout[] loadouts = InfantryLoadoutRolls.defenderSquad(WAVE_SIZE, UnitType.MILITIA, risk, loadoutRng);
        int size = Math.min(WAVE_SIZE, cells.size());
        for (int i = 0; i < size; i++) {
            int[] cell = cells.get(i);
            EntitySpec spec = new EntitySpec("Cordon Militia " + (point + 1) + "-" + (i + 1),
                    Faction.ALLY, UnitType.MILITIA, cell[0], cell[1]).squad(squadId);
            loadouts[i].seedInto(spec);
            long id = sim.spawn(spec);
            if (squad.leaderId == 0L) squad.leaderId = id;
        }
        squad.originalSize = size;
        return size > 0;
    }

    /**
     * Orders every team still on its born escort of a post into that post's
     * stretch of trench: the run under it, near the post. A team whose post has
     * no trench keeps the open ground round it.
     */
    private void manTrench(BattleSimulation sim) {
        if (trenchPosts == null) return;
        for (Squad squad : sim.getSquads()) {
            if (!squad.rescuePickupGuard || squad.rescuePickupMech || squad.aliveMembers <= 0) continue;
            ObjectiveAssignment assignment = squad.assignmentForExecution();
            if (assignment == null || assignment.kind() != AssignmentKind.ESCORT) continue;
            int point = formationPointOf(assignment.targetCellX(), assignment.targetCellY());
            if (point < 0 || trenchPosts[point * 2] < 0) continue;
            int x = trenchPosts[point * 2], y = trenchPosts[point * 2 + 1];
            int run = sim.fightingPositions().runIdAt(x, y);
            sim.assignSquadCommand(run >= 0
                            ? ObjectiveAssignment.defendRun(squad.id, x, y, PickupCordonWorks.POST_RADIUS, run)
                            : ObjectiveAssignment.defendArea(squad.id, x, y, PickupCordonWorks.POST_RADIUS),
                    CommandAuthority.PAYLOAD, "rescue-pickup-support", "man the cordon trench");
        }
    }

    private void reinforceMilitia(BattleSimulation sim) {
        int live = liveGuardCount(sim);
        int inbound = inboundGuardCount(sim);
        if (live + inbound >= REINFORCEMENT_FLOOR
                || accumulator < WAVE_INTERVAL_SECONDS) return;

        int requested = Math.min(WAVE_SIZE, TARGET_GUARDS - live - inbound);
        if (requested <= 0) return;
        dispatchMilitiaShuttle(sim, requested, 0f,
                leastDefendedFormationPoint(sim));
        accumulator = 0f;
    }

    private void reinforceMech(BattleSimulation sim) {
        if (livePickupMechCount(sim) + inboundMechCount(sim) >= PICKUP_MECHS
                || mechAccumulator < WAVE_INTERVAL_SECONDS) return;
        dispatchMechShuttle(sim, 0f);
        mechAccumulator = 0f;
    }

    public boolean isConfigured() {
        return configured;
    }

    public int liveGuardCount(BattleSimulation sim) {
        int count = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().type(unit) != UnitType.MILITIA) continue;
            if (!sim.squad().hasSquad(unit)) continue;
            Squad squad = sim.getSquad(sim.squad().squadId(unit));
            if (squad != null && squad.rescuePickupGuard) count++;
        }
        return count;
    }

    public int livePickupMechCount(BattleSimulation sim) {
        int count = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().type(unit) != UnitType.HEAVY_MECH
                    || !sim.squad().hasSquad(unit)) continue;
            Squad squad = sim.getSquad(sim.squad().squadId(unit));
            if (squad != null && squad.rescuePickupMech) count++;
        }
        return count;
    }

    private int inboundGuardCount(BattleSimulation sim) {
        int count = 0;
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(id);
            if (mission == null || mission.destroyed || !mission.rescueMilitiaTransport
                    || mission.state == ShuttleState.GONE) continue;
            count += mission.marinesRemaining;
        }
        return count;
    }

    private int inboundMechCount(BattleSimulation sim) {
        int count = 0;
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(id);
            if (mission == null || mission.destroyed || !mission.rescuePickupMechTransport
                    || mission.state == ShuttleState.GONE) continue;
            count += mission.marinesRemaining;
        }
        return count;
    }

    private void dispatchMilitiaShuttle(BattleSimulation sim, int requested,
                                        float delaySeconds, int formationPoint) {
        int guardX = formationCells[formationPoint * 2];
        int guardY = formationCells[formationPoint * 2 + 1];
        long shuttle = sim.spawnShuttle(
                ShuttleType.AEROSHUTTLE, Faction.ALLY,
                guardX + 0.5f, guardY + 0.5f,
                entryX, entryY, exitX, exitY, delaySeconds);
        ShuttleMission mission = sim.world().mission(shuttle);
        mission.marinesRemaining = requested;
        mission.deboardUnitType = UnitType.MILITIA;
        mission.marineLoadout = InfantryLoadoutRolls.defenderSquad(
                requested, UnitType.MILITIA, risk, loadoutRng);
        mission.rescueMilitiaTransport = true;
        mission.rescueGuardX = guardX;
        mission.rescueGuardY = guardY;
    }

    private void dispatchInitialMilitia(BattleSimulation sim) {
        for (int point = 0; point < TARGET_GUARD_SQUADS; point++) {
            dispatchMilitiaShuttle(sim, WAVE_SIZE, initialDelay(point), point);
        }
    }

    /** When the initial line's sortie to a formation point lands: staggered, the mech's slot left between them. */
    private static float initialDelay(int point) {
        int sequence = point < 2 ? point : point + 1;
        return INITIAL_ARRIVAL_DELAY_SECONDS + sequence * INITIAL_SORTIE_STAGGER_SECONDS;
    }

    private void dispatchInitialMech(BattleSimulation sim) {
        dispatchMechShuttle(sim,
                INITIAL_ARRIVAL_DELAY_SECONDS
                        + 2f * INITIAL_SORTIE_STAGGER_SECONDS);
    }

    private void dispatchMechShuttle(BattleSimulation sim,
                                     float delaySeconds) {
        long shuttle = sim.spawnShuttle(
                ShuttleType.VALKYRIE, Faction.ALLY,
                reinforcementLzX, reinforcementLzY,
                entryX, entryY, exitX, exitY, delaySeconds);
        ShuttleMission mission = sim.world().mission(shuttle);
        mission.payload = MechSupportPayload.INSTANCE;
        mission.marinesRemaining = PICKUP_MECHS;
        mission.mechVariant = pickupMechVariant;
        mission.rescuePickupMechTransport = true;
        mission.rescueGuardX = placement.liftX;
        mission.rescueGuardY = placement.liftY;
        mission.rescuePatrolCells = formationCells.clone();
    }

    private int leastDefendedFormationPoint(BattleSimulation sim) {
        int[] strength = new int[TARGET_GUARD_SQUADS];
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().type(unit) != UnitType.MILITIA
                    || !sim.squad().hasSquad(unit)) continue;
            Squad squad = sim.getSquad(sim.squad().squadId(unit));
            int point = formationPointOf(squad);
            if (point >= 0) strength[point]++;
        }
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(id);
            if (mission == null || mission.destroyed || !mission.rescueMilitiaTransport
                    || mission.state == ShuttleState.GONE) continue;
            int point = formationPointOf(mission.rescueGuardX,
                    mission.rescueGuardY);
            if (point >= 0) strength[point] += mission.marinesRemaining;
        }
        int best = 0;
        for (int point = 1; point < strength.length; point++) {
            if (strength[point] < strength[best]) best = point;
        }
        return best;
    }

    private int formationPointOf(Squad squad) {
        if (squad == null || !squad.rescuePickupGuard
                || squad.assignmentForExecution() == null) return -1;
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        return formationPointOf(assignment.targetCellX(),
                assignment.targetCellY());
    }

    /** The formation point a cell is: the point itself, or the trench post its team mans. */
    private int formationPointOf(int x, int y) {
        for (int point = 0; point < TARGET_GUARD_SQUADS; point++) {
            if (formationCells[point * 2] == x
                    && formationCells[point * 2 + 1] == y) return point;
            if (trenchPosts != null && trenchPosts[point * 2] == x
                    && trenchPosts[point * 2 + 1] == y) return point;
        }
        return -1;
    }
}
