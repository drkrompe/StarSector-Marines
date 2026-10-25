package com.dillon.starsectormarines.battle.evacuation.flier;

/**
 * A Harrier sent off its pack to fetch a body home to the nest (`colony-breach-nouns.md`, Harrier, Carrying feed):
 * it {@linkplain Phase#SNATCH swoops} on a loose body, {@linkplain Phase#HAUL hauls} it low and slow to the nearest
 * living bore's mouth, where the harvest feeds it in, and {@linkplain Phase#RETURN flies back} to its pack.
 *
 * <p>A pack with nothing to hunt sends at most {@link #carriersFor} of its number ({@link #sends}); the rest keep the
 * ring. A carrier never bites and is no part of its pack's dives, but it is still one of its members: shot, drawn and
 * heard as any Harrier, and a carrier shot down drops its load where it falls.
 *
 * <p>Pure: it steers its own body and reports where it is in the job; the battle takes the body up and lets the
 * harvest deliver it.
 */
public final class HarrierCarry {

    public enum Phase { SNATCH, HAUL, RETURN }

    /** How far a pack looks for a body to fetch, in cells: a patrol's eye. */
    public static final float SENSE = HarrierPatrol.SPOT_RANGE;
    /** Swoop speed, cells a second, and the reach at which it takes the body up. */
    public static final float SNATCH_SPEED = 7f, SNATCH_REACH = 0.9f;
    /** Seconds it swoops before giving the body up. */
    public static final float SNATCH_SECONDS = 10f;
    /** Haul speed, cells a second: laden, it labours. */
    public static final float HAUL_SPEED = 3f;
    /** Height it hauls at, a share of loiter height: low, within reach of rifles. */
    public static final float HAUL_ALTITUDE = 0.35f;
    /** A pack sends one carrier for every this many of its number. */
    public static final int PACK_PER_CARRIER = 3;

    public final long body;
    Phase phase = Phase.SNATCH;
    float seconds;

    public HarrierCarry(long body) {
        this.body = body;
    }

    public Phase phase() { return phase; }

    /** How many carriers a pack of {@code size} (its members, carriers among them) may have out at once. */
    public static int carriersFor(int size) {
        return size / PACK_PER_CARRIER;
    }

    /**
     * Whether a pack sends another carrier: it is loitering with no prey, every member hatched, and fewer than
     * {@link #carriersFor} its size are out.
     */
    public static boolean sends(HarrierPack.Phase phase, boolean prey, int unhatched, int size, int out) {
        return phase == HarrierPack.Phase.LOITER && !prey && unhatched <= 0 && out < carriersFor(size);
    }

    /**
     * Swoops on the body at ({@code bx}, {@code by}): fast, dropping as it closes. Returns whether it is within
     * {@link #SNATCH_REACH} and low enough to take it up; past {@link #SNATCH_SECONDS} it gives up and returns.
     */
    boolean swoop(HarrierFlight m, float bx, float by, float dt) {
        seconds += dt;
        float d = m.body.distanceTo(bx, by);
        float low = Math.max(HarrierRules.DIVE_FLOOR_ALTITUDE, Math.min(1f, d / HarrierRules.DIVE_GLIDE_CELLS));
        m.altitude = HarrierSteering.ease(m.altitude, low, dt);
        HarrierSteering.fly(m.body, bx, by, 0f, 0f, Math.min(SNATCH_SPEED, 1.5f + 2f * d), HarrierRules.DIVE_TURN_DEG_PER_SEC, dt);
        if (seconds >= SNATCH_SECONDS) {
            phase = Phase.RETURN;
            return false;
        }
        return d <= SNATCH_REACH && m.altitude <= HarrierRules.LOW_ALTITUDE;
    }

    /** It has the body: off to the mouth. */
    void took() {
        phase = Phase.HAUL;
        seconds = 0f;
    }

    /** Hauls toward the mouth at ({@code mx}, {@code my}), low and slow. */
    void haul(HarrierFlight m, float mx, float my, float dt) {
        seconds += dt;
        m.altitude = HarrierSteering.ease(m.altitude, HAUL_ALTITUDE, dt);
        HarrierSteering.fly(m.body, mx, my, 0f, 0f, HAUL_SPEED, HarrierRules.LOITER_TURN_DEG_PER_SEC, dt);
    }

    /** The job is over (fed, lost or given up): back to the pack. */
    void giveUp() {
        phase = Phase.RETURN;
        seconds = 0f;
    }

    /**
     * Flies back to its pack's loiter point ({@code px}, {@code py}) at height; returns whether it is back on the
     * pack's ring of {@code ring} cells, and with it again.
     */
    boolean rejoin(HarrierFlight m, float px, float py, float ring, float dt) {
        seconds += dt;
        m.altitude = HarrierSteering.ease(m.altitude, 1f, dt);
        HarrierSteering.fly(m.body, px, py, 0f, 0f, HarrierRules.LOITER_SPEED, HarrierRules.LOITER_TURN_DEG_PER_SEC, dt);
        return m.body.distanceTo(px, py) <= ring + HarrierRules.GATHER_MARGIN;
    }
}
