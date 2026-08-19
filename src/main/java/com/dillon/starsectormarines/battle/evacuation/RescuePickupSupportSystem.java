package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.Random;

/**
 * Holds a capped local-militia line around the rescue pickup and dispatches a
 * visible replacement shuttle when casualties pull that line below strength.
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
    private MechVariant pickupMechVariant;
    private Random loadoutRng;
    private RiskLevel risk;
    private float accumulator = WAVE_INTERVAL_SECONDS;
    private float mechAccumulator = WAVE_INTERVAL_SECONDS;
    private boolean configured;

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
                ? MechVariant.BULWARK : MechVariant.SIROCCO;
        this.loadoutRng = new Random(seed ^ 0x72C5A4D93E10B68FL);
        this.risk = risk != null ? risk : RiskLevel.LOW;
        configured = true;
        dispatchInitialSupport(sim);
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
            if (mission == null || !mission.rescueMilitiaTransport
                    || mission.state == ShuttleState.GONE) continue;
            count += mission.marinesRemaining;
        }
        return count;
    }

    private int inboundMechCount(BattleSimulation sim) {
        int count = 0;
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(id);
            if (mission == null || !mission.rescuePickupMechTransport
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
                ShuttleType.AEROSHUTTLE, Faction.MARINE,
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

    private void dispatchInitialSupport(BattleSimulation sim) {
        for (int point = 0; point < TARGET_GUARD_SQUADS; point++) {
            int sequence = point < 2 ? point : point + 1;
            dispatchMilitiaShuttle(sim, WAVE_SIZE,
                    INITIAL_ARRIVAL_DELAY_SECONDS
                            + sequence * INITIAL_SORTIE_STAGGER_SECONDS,
                    point);
        }
        dispatchMechShuttle(sim,
                INITIAL_ARRIVAL_DELAY_SECONDS
                        + 2f * INITIAL_SORTIE_STAGGER_SECONDS);
    }

    private void dispatchMechShuttle(BattleSimulation sim,
                                     float delaySeconds) {
        long shuttle = sim.spawnShuttle(
                ShuttleType.VALKYRIE, Faction.MARINE,
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
            if (mission == null || !mission.rescueMilitiaTransport
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
                || squad.assignedObjective == null) return -1;
        return formationPointOf(squad.assignedObjective.targetCellX(),
                squad.assignedObjective.targetCellY());
    }

    private int formationPointOf(int x, int y) {
        for (int point = 0; point < TARGET_GUARD_SQUADS; point++) {
            if (formationCells[point * 2] == x
                    && formationCells[point * 2 + 1] == y) return point;
        }
        return -1;
    }
}
