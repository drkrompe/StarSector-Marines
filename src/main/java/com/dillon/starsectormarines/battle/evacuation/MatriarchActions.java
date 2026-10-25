package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.modifier.UnitModifier;
import com.dillon.starsectormarines.battle.modifier.UnitStat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each Matriarch is doing with its scythes and its crown: the one action in hand, when it began,
 * and whether its blow has landed. Its behaviour starts an action and lands the blow part-way through,
 * so a reap is seen coming before it cuts; its rig reads the action and its age to pose the strike.
 *
 * <p>Written by the parallel unit update, but each Matriarch only ever touches its own record.
 */
public final class MatriarchActions {

    /** One action; the order is the rig's codes, {@code RavMatriarchRig.NONE} first. */
    public enum Kind {
        NONE(0f, 0f),
        /** On emergence: it stands and roars. No blow. */
        ROAR(1.0f, -1f),
        /** The right scythe across the front, then the left on the next. */
        REAP_RIGHT(1.03f, 0.37f),
        REAP_LEFT(1.03f, 0.37f),
        /** Rocks back and drives the crown into a hull, an emplacement, a mech or a wall. */
        CROWN(1.27f, 0.50f),
        /** Before a run: it stands crown-down and bellows ({@link MatriarchRunRules#TELL}). No blow. */
        RUN_TELL(MatriarchRunRules.TELL, -1f);

        /** Seconds the action holds the body. */
        public final float seconds;
        /** Seconds in when the blow lands, or negative for none. */
        public final float hitSeconds;

        Kind(float seconds, float hitSeconds) {
            this.seconds = seconds;
            this.hitSeconds = hitSeconds;
        }
    }

    static final class Action {
        Kind kind = Kind.NONE;
        int startTick;
        boolean landed;
        long target;
        /** The way a reap sweeps, a unit vector from the body toward its prey when it began. */
        float axisX, axisY;
        /** The wall a crown blow is aimed at, when it is not aimed at a body. */
        SwarmBruteBehavior.BreachPoint breach;
        boolean roared;
        boolean nextLeft;
        /** Whether it is running, at whom, since when, and the tick its next run may begin. */
        boolean running;
        long runQuarry;
        int runStartTick;
        int runReadyTick;
    }

    private final ConcurrentHashMap<Long, Action> actions = new ConcurrentHashMap<>();

    Action of(long matriarch) {
        return actions.computeIfAbsent(matriarch, id -> new Action());
    }

    /** The action {@code matriarch} has in hand at {@code tick}, {@link Kind#NONE} when it is free. */
    public Kind kind(long matriarch, int tick) {
        Action a = actions.get(matriarch);
        if (a == null || a.kind == Kind.NONE) return Kind.NONE;
        return age(a, tick) < a.kind.seconds ? a.kind : Kind.NONE;
    }

    /** Seconds since its current action began, or 0 when it has none. */
    public float ageSeconds(long matriarch, int tick) {
        Action a = actions.get(matriarch);
        return a == null || a.kind == Kind.NONE ? 0f : age(a, tick);
    }

    /** Whether {@code matriarch} is running: presentation, and the pace its movement reads. */
    public boolean running(long matriarch) {
        Action a = actions.get(matriarch);
        return a != null && a.running;
    }

    /** Her run pace, as a stat modifier on the runner. */
    public UnitModifier runModifier() {
        return new UnitModifier() {
            @Override public String name(long unit) { return "Running"; }
            @Override public Set<UnitStat> stats() { return Set.of(UnitStat.MOVE_SPEED); }
            @Override public boolean appliesTo(long unit) { return running(unit); }
            @Override public float multiplier(long unit, UnitStat stat) { return MatriarchRunRules.PACE; }
        };
    }

    static float age(Action a, int tick) {
        return (tick - a.startTick) * SwarmMatriarchBehavior.TICK_SECONDS;
    }
}
