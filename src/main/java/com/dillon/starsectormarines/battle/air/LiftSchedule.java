package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/**
 * When the next lift sets down at each of a side's drop-off points, read off
 * the transports actually flying them.
 *
 * <p>A <b>drop-off point</b> is the ground one or more troop lifts set down on:
 * a pad, or the two berths of an arrival area, which sit close enough that the
 * player reads them as one place. The schedule invents no wave clock. Every
 * figure is a reading of a live {@link ShuttleMission} — its re-arm countdown,
 * where its body is and how fast its airframe flies — so the timer the player
 * watches and the shuttle that arrives are the same fact.
 *
 * <p>Only infantry deliveries count. A gunship, a strike, a cover patrol or a
 * pickup is not bringing anybody, and a sortie with no more sorties behind it
 * leaves the schedule once its passengers are off.
 */
public final class LiftSchedule {

    /** Pads nearer than this, in cells, are one drop-off point. An arrival area's two berths are well inside it. */
    public static final float SAME_POINT_CELLS = 12f;

    /**
     * Seconds added to the flight leg for the settle onto the pad: the brake
     * out of the run in, the hover and the sink, less the last stretch of the
     * leg the craft never flies at cruise because the descent takes over short
     * of the pad. Measured, not derived: on the reinforced-south Conquest
     * (`lift-timer` snapshot suite) 2.5 s read 1.6 to 1.9 s late at every lead
     * time, and this reads within a fifth of a second on average and under a
     * second at worst.
     */
    public static final float SETTLE_SECONDS = 0.75f;

    private LiftSchedule() {}

    /** What one transport is doing about its drop-off point right now. */
    public enum Phase {
        /** Re-arming in orbit or flying down; {@link Lift#secondsToTouchdown} counts it in. */
        INBOUND,
        /** Over or on the pad, putting its passengers down. */
        UNLOADING
    }

    /**
     * One transport's next delivery.
     *
     * @param lzX                where it sets down, in cells
     * @param lzY                where it sets down, in cells
     * @param phase              inbound, or on the pad now
     * @param secondsToTouchdown sim-seconds until it is down; zero while unloading
     * @param marines            passengers this sortie brings, or is still putting down
     * @param liftsToCome        sorties after the one {@code phase} describes
     * @param turnaroundSeconds  a whole cycle, re-arm and both legs, for a timer that fills
     */
    public record Lift(float lzX, float lzY, Phase phase, float secondsToTouchdown,
                       int marines, int liftsToCome, float turnaroundSeconds) {}

    /**
     * Lifts due at one drop-off point within this many seconds of the first are
     * one wave: an arrival area's pair comes down together, and the player
     * reads it as one landing of a whole squad rather than two.
     */
    public static final float WAVE_SECONDS = 3f;

    /**
     * One drop-off point and its next wave.
     *
     * @param x           centre of its pads, in cells
     * @param y           centre of its pads, in cells
     * @param next        the wave's first lift: one unloading, else the soonest inbound
     * @param marines     passengers the whole wave brings, or is still putting down
     * @param liftsToCome every sortie still to set down here after the wave
     */
    public record DropOff(float x, float y, Lift next, int marines, int liftsToCome) {}

    /** The side's drop-off points with a lift still due or on the ground. */
    public static List<DropOff> capture(BattleView sim, Faction side) {
        World world = sim.world();
        List<Lift> lifts = new ArrayList<>();
        for (long id : sim.getAirEntityIds()) {
            if (world.airFaction(id) != side) continue;
            ShuttleMission mission = world.mission(id);
            AirBody body = world.kinematics(id);
            Airframe frame = world.airframe(id);
            if (mission == null || body == null || frame == null) continue;
            Lift lift = lift(mission, body.x, body.y, FittedBoat.handling(frame, mission).maxSpeed());
            if (lift != null) lifts.add(lift);
        }
        return group(lifts);
    }

    /**
     * Reads one transport, or null when it is not bringing infantry to a pad.
     *
     * @param bodyX    where the craft is now, in cells
     * @param bodyY    where the craft is now, in cells
     * @param maxSpeed the airframe's top speed, cells per sim-second
     */
    static Lift lift(ShuttleMission m, float bodyX, float bodyY, float maxSpeed) {
        if (m.destroyed || m.aborted || m.gunship || m.strikeSortie || m.poolSortie || m.cover != null
                || m.awaitingEvacuees || m.pickup != null) return null;
        if (m.payload != null && m.payload != InfantryPayload.INSTANCE) return null;
        float speed = Math.max(0.1f, maxSpeed);
        float down = (float) Math.hypot(m.lzX - m.entryX, m.lzY - m.entryY) / speed + SETTLE_SECONDS;
        float back = (float) Math.hypot(m.exitX - m.lzX, m.exitY - m.lzY) / speed;
        float turnaround = Math.max(1f, back + m.rearmDelay + down);
        int after = Math.max(0, m.totalCycles - m.currentCycle - 1);
        return switch (m.state) {
            case PENDING -> m.marinesRemaining <= 0 ? null : new Lift(m.lzX, m.lzY, Phase.INBOUND,
                    Math.max(0f, m.pendingDelay) + down, m.marinesRemaining, after, turnaround);
            case INCOMING -> m.marinesRemaining <= 0 ? null : new Lift(m.lzX, m.lzY, Phase.INBOUND,
                    (float) Math.hypot(m.lzX - bodyX, m.lzY - bodyY) / speed + SETTLE_SECONDS,
                    m.marinesRemaining, after, turnaround);
            case PAD_DESCENT, LANDED -> m.marinesRemaining <= 0 && after == 0 ? null
                    : new Lift(m.lzX, m.lzY, Phase.UNLOADING, 0f, m.marinesRemaining, after, turnaround);
            case DEPARTING -> after == 0 ? null : new Lift(m.lzX, m.lzY, Phase.INBOUND,
                    (float) Math.hypot(m.exitX - bodyX, m.exitY - bodyY) / speed + m.rearmDelay + down,
                    nextSortieSeats(m), after - 1, turnaround);
            default -> null;
        };
    }

    /** Folds lifts onto drop-off points, in the order their first lift was read. */
    static List<DropOff> group(List<Lift> lifts) {
        List<List<Lift>> points = new ArrayList<>();
        for (Lift lift : lifts) {
            List<Lift> home = null;
            for (List<Lift> point : points) {
                Lift first = point.get(0);
                if (Math.hypot(first.lzX - lift.lzX, first.lzY - lift.lzY) <= SAME_POINT_CELLS) {
                    home = point;
                    break;
                }
            }
            if (home == null) points.add(home = new ArrayList<>());
            home.add(lift);
        }
        List<DropOff> out = new ArrayList<>(points.size());
        for (List<Lift> point : points) {
            float x = 0f, y = 0f;
            Lift next = null;
            int sorties = 0;
            for (Lift lift : point) {
                x += lift.lzX;
                y += lift.lzY;
                sorties += 1 + lift.liftsToCome;
                if (next == null || sooner(lift, next)) next = lift;
            }
            float by = next.phase == Phase.UNLOADING ? WAVE_SECONDS : next.secondsToTouchdown + WAVE_SECONDS;
            int marines = 0, wave = 0;
            for (Lift lift : point) {
                if (lift.phase != Phase.UNLOADING && lift.secondsToTouchdown > by) continue;
                marines += lift.marines;
                wave++;
            }
            out.add(new DropOff(x / point.size(), y / point.size(), next, marines, sorties - wave));
        }
        return out;
    }

    private static boolean sooner(Lift a, Lift b) {
        if (a.phase != b.phase) return a.phase == Phase.UNLOADING;
        return a.secondsToTouchdown < b.secondsToTouchdown;
    }

    private static int nextSortieSeats(ShuttleMission m) {
        int next = m.currentCycle + 1;
        if (m.cycleLoadouts != null && next < m.cycleLoadouts.length && m.cycleLoadouts[next] != null) {
            return m.cycleLoadouts[next].length;
        }
        return m.seatsPerSortie;
    }
}
