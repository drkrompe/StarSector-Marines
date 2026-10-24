package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

/**
 * A road hull and its escort moving together ({@code allied-tools.md}, The delivery hook): "escorted" means
 * alongside, not merely assigned. A hull that has an escort coming waits where it was set down until the escort is
 * alongside, then the pair goes at the slower one's pace, and the hull holds whenever its escort falls behind. A hull
 * that has no escort free sets out alone at once, and one whose escort never comes (stuck, or held up fighting) goes
 * on without it after a while rather than standing on the road all battle.
 *
 * <p>The decision is {@link #rule}, a pure function; this holds the little state it needs (set out yet, holding,
 * since when, gone on alone) and applies it through the hull's {@link VehicleMission#speedCap}, the brake a
 * mission driver already has. Mission-neutral: who escorts is the mission's (an {@link SupplyDelivery.Escort}), a hull
 * or a walker (a mech, a rifle team's leader), and the pair keeps to the slower one's pace either way; the Cordon's
 * ammunition trucks and R1's dozer are paced by it, and the heart's trucks can be.
 */
public final class EscortPace {

    /** What a paced hull does this tick. */
    public enum Pace {
        /** Not set out: its escort is on its way to it. */
        WAIT,
        /** Moving, alongside its escort (or with none to keep to). */
        GO,
        /** Set out, stopped: its escort fell behind. */
        HOLD,
        /** Its escort did not come in time: set out, or go on, without it. */
        ALONE
    }

    /** Cells between a hull and its escort within which the escort is alongside: the pair sets out, or goes on. */
    public static final float ALONGSIDE = 8f;
    /** Cells an escort may fall behind its moving hull before the hull holds for it. */
    public static final float FALLEN_BEHIND = 14f;
    /**
     * Ticks a hull set down waits before deciding no escort is free. An escort is assigned at the mission's next look
     * at the road, which comes within this; without it a new hull would set out alone before it was ever offered one.
     */
    public static final int ASSIGN_TICKS = Math.round(2f / BattleSimulation.TICK_DT);
    /** Ticks a hull waits where it was set down for an escort on its way before going without it. */
    public static final int WAIT_PATIENCE_TICKS = Math.round(90f / BattleSimulation.TICK_DT);
    /** Ticks a hull holds on the road for an escort fallen behind before going on without it. */
    public static final int HOLD_PATIENCE_TICKS = Math.round(30f / BattleSimulation.TICK_DT);
    /**
     * Share of the slower one's top speed the pair keeps to. A little under, so the escort, re-sent to the hull's side
     * as it moves, can close up rather than trailing at the edge of {@link #FALLEN_BEHIND}.
     */
    public static final float PACE = 0.85f;

    /**
     * The rule.
     *
     * @param setOut    whether the hull has set out
     * @param holding   whether it is holding for its escort now
     * @param escort    whether an escort is assigned to it (and not given up on)
     * @param distance  cells between the hull and its escort; ignored without one
     * @param waited    ticks since it was set down (not set out) or began holding (holding)
     */
    static Pace rule(boolean setOut, boolean holding, boolean escort, float distance, int waited) {
        if (!setOut) {
            if (!escort) return waited >= ASSIGN_TICKS ? Pace.ALONE : Pace.WAIT;
            if (distance <= ALONGSIDE) return Pace.GO;
            return waited >= WAIT_PATIENCE_TICKS ? Pace.ALONE : Pace.WAIT;
        }
        if (!escort) return Pace.GO;
        if (holding) {
            if (distance <= ALONGSIDE) return Pace.GO;
            return waited >= HOLD_PATIENCE_TICKS ? Pace.ALONE : Pace.HOLD;
        }
        return distance > FALLEN_BEHIND ? Pace.HOLD : Pace.GO;
    }

    /** The pair's pace, cells per second: the slower one's top speed, a little under ({@link #PACE}). */
    static float cap(float hullTopSpeed, float escortTopSpeed) {
        return Math.min(hullTopSpeed, escortTopSpeed) * PACE;
    }

    private final int startTick;
    private boolean setOut;
    private boolean holding;
    private int since;
    private boolean alone;
    private Pace last = Pace.WAIT;

    /** @param startTick the tick the hull was set down */
    public EscortPace(int startTick) {
        this.startTick = startTick;
        this.since = startTick;
    }

    /**
     * One tick: decides the hull's pace against its escort ({@code 0} while none is assigned) and applies it to the
     * hull's speed cap. The caller sends the hull on its way the tick {@link #setOut} first turns true.
     */
    public Pace step(BattleSimulation sim, long hull, long escort) {
        int tick = sim.getSimTickIndex();
        boolean with = !alone && escort != 0L && alive(sim, escort);
        float distance = with ? sim.convoy().body(hull).distanceTo(x(sim, escort), y(sim, escort)) : Float.MAX_VALUE;
        Pace pace = rule(setOut, holding, with, distance, tick - (setOut ? since : startTick));
        VehicleMission mission = sim.convoy().mission(hull);
        switch (pace) {
            case WAIT -> mission.speedCap = 0f;
            case HOLD -> {
                if (!holding) since = tick;
                holding = true;
                mission.speedCap = 0f;
            }
            case GO -> {
                setOut = true;
                holding = false;
                mission.speedCap = with ? cap(type(sim, hull).maxSpeed, topSpeed(sim, escort))
                        : Float.POSITIVE_INFINITY;
            }
            case ALONE -> {
                alone = true;
                setOut = true;
                holding = false;
                mission.speedCap = Float.POSITIVE_INFINITY;
            }
        }
        last = pace;
        return pace;
    }

    /** Lets the hull go at its own pace again: its escorted drive is over. */
    public void release(BattleSimulation sim, long hull) {
        setOut = true;
        holding = false;
        if (sim.convoy().isVehicle(hull) && sim.convoy().mission(hull) != null) {
            sim.convoy().mission(hull).speedCap = Float.POSITIVE_INFINITY;
        }
    }

    /** Whether the hull has set out. */
    public boolean setOut() { return setOut; }

    /**
     * Whether the hull has gone on without an escort: final, so it is offered none again. Offered a fresh one at the
     * next look, a truck that had given up on one waited, or held, for the next in turn while its guns stood dry.
     */
    public boolean alone() { return alone; }

    /** The pace decided at the last step. */
    public Pace last() { return last; }

    private static VehicleType type(BattleSimulation sim, long hull) {
        return sim.convoy().vehicleType(hull);
    }

    /**
     * Whether an escort is still there: a hull with structure left, or a living unit (a mech, a rifle team's
     * leader). An escort may walk: a city has no hulls, and its truck keeps to a Hound or a rifle team instead.
     */
    private static boolean alive(BattleSimulation sim, long escort) {
        if (sim.convoy().isVehicle(escort)) return sim.convoy().structure(escort) > 0f;
        return sim.resolveUnit(escort) != 0L && sim.world().isAlive(escort);
    }

    private static float x(BattleSimulation sim, long escort) {
        return sim.convoy().isVehicle(escort) ? sim.convoy().body(escort).x : sim.world().x(escort);
    }

    private static float y(BattleSimulation sim, long escort) {
        return sim.convoy().isVehicle(escort) ? sim.convoy().body(escort).y : sim.world().y(escort);
    }

    /** An escort's top speed, cells per second: a hull's type's, a walker's own move speed. */
    private static float topSpeed(BattleSimulation sim, long escort) {
        if (sim.convoy().isVehicle(escort)) return type(sim, escort).maxSpeed;
        return sim.world().hasMovement(escort) ? sim.world().moveSpeed(escort) : Float.POSITIVE_INFINITY;
    }
}
