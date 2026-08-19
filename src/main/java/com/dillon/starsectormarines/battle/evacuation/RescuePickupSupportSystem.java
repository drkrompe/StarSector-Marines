package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * Holds a capped local-militia line around the rescue pickup and dispatches a
 * visible replacement shuttle when casualties pull that line below strength.
 */
public final class RescuePickupSupportSystem {

    public static final int TARGET_GUARDS = 8;
    public static final int REINFORCEMENT_FLOOR = 5;
    public static final int WAVE_SIZE = 4;
    public static final int PICKUP_MECHS = 1;
    public static final float WAVE_INTERVAL_SECONDS = 12f;
    public static final float INITIAL_ARRIVAL_DELAY_SECONDS = 12f;
    private static final float INITIAL_SORTIE_STAGGER_SECONDS = 4f;

    private final CivilianEvacuationTracker tracker;
    private CivilianEvacuationPlacement placement;
    private float reinforcementLzX;
    private float reinforcementLzY;
    private float entryX;
    private float entryY;
    private float exitX;
    private float exitY;
    private float accumulator = WAVE_INTERVAL_SECONDS;
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
                             long seed,
                             BattleSimulation sim) {
        if (configured || placement == null || sim == null) return false;
        this.placement = placement;
        this.reinforcementLzX = reinforcementLzX;
        this.reinforcementLzY = reinforcementLzY;
        this.entryX = entryX;
        this.entryY = entryY;
        this.exitX = exitX;
        this.exitY = exitY;
        configured = true;
        dispatchInitialSupport(sim, seed);
        return true;
    }

    public void tick(float dt, BattleSimulation sim) {
        if (!configured || tracker.isSealed() || tracker.activeCount() == 0) return;
        accumulator = Math.min(WAVE_INTERVAL_SECONDS,
                accumulator + Math.max(0f, dt));
        int live = liveGuardCount(sim);
        int inbound = inboundGuardCount(sim);
        if (live + inbound >= REINFORCEMENT_FLOOR
                || accumulator < WAVE_INTERVAL_SECONDS) return;

        int requested = Math.min(WAVE_SIZE, TARGET_GUARDS - live - inbound);
        if (requested <= 0) return;
        dispatchMilitiaShuttle(sim, requested, 0f);
        accumulator = 0f;
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

    private void dispatchMilitiaShuttle(BattleSimulation sim, int requested,
                                        float delaySeconds) {
        long shuttle = sim.spawnShuttle(
                ShuttleType.AEROSHUTTLE, Faction.MARINE,
                reinforcementLzX, reinforcementLzY,
                entryX, entryY, exitX, exitY, delaySeconds);
        ShuttleMission mission = sim.world().mission(shuttle);
        mission.marinesRemaining = requested;
        mission.deboardUnitType = UnitType.MILITIA;
        mission.rescueMilitiaTransport = true;
        mission.rescueGuardX = placement.liftX;
        mission.rescueGuardY = placement.liftY;
    }

    private void dispatchInitialSupport(BattleSimulation sim, long seed) {
        dispatchMilitiaShuttle(sim, WAVE_SIZE, INITIAL_ARRIVAL_DELAY_SECONDS);
        dispatchMechShuttle(sim, seed,
                INITIAL_ARRIVAL_DELAY_SECONDS + INITIAL_SORTIE_STAGGER_SECONDS);
        dispatchMilitiaShuttle(sim, TARGET_GUARDS - WAVE_SIZE,
                INITIAL_ARRIVAL_DELAY_SECONDS + 2f * INITIAL_SORTIE_STAGGER_SECONDS);
    }

    private void dispatchMechShuttle(BattleSimulation sim, long seed,
                                     float delaySeconds) {
        MechVariant variant = (seed & 1L) == 0L
                ? MechVariant.BULWARK : MechVariant.SIROCCO;
        long shuttle = sim.spawnShuttle(
                ShuttleType.VALKYRIE, Faction.MARINE,
                reinforcementLzX, reinforcementLzY,
                entryX, entryY, exitX, exitY, delaySeconds);
        ShuttleMission mission = sim.world().mission(shuttle);
        mission.payload = MechSupportPayload.INSTANCE;
        mission.marinesRemaining = PICKUP_MECHS;
        mission.mechVariant = variant;
        mission.rescuePickupMechTransport = true;
        mission.rescueGuardX = placement.liftX;
        mission.rescueGuardY = placement.liftY;
    }
}
