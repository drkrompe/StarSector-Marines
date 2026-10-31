package com.dillon.starsectormarines.battle.air;

/**
 * What a fighter on combat air patrol is doing, held on its {@link ShuttleMission}.
 *
 * <p>A cover sortie is an ordinary pool sortie with one extra job. It flies in across the map edge, joins a ring about
 * the ground it was asked to cover and holds it for a bounded time ({@link #STATION_SECONDS}); while it holds it looks
 * for two kinds of work, in this order. <b>Hostile aircraft first</b>: it breaks off the ring, pursues the aircraft and
 * fires its nose guns ({@link AirToAirFire}). <b>Known ground threats second</b>: a hostile concentration near the
 * covered ground that the side itself has sighted gets a gun pass, a bounded number of times, after which the fighter
 * returns to the ring. What it is doing at any moment is the phase ({@link ShuttleState#ORBIT},
 * {@link ShuttleState#INTERCEPT}, or a gun pass in {@link ShuttleState#ATTACK_RUN}); this class holds only what the phase
 * cannot: the prey, the magazine, the clock.
 *
 * <p>Pure data, as {@link ShuttleMission} is. Who chooses the prey is {@code CoverTasking} (it needs the side's
 * knowledge); how the fighter flies to it is {@link AirSystem}'s; how the rounds fly is {@link AirToAirFire}'s.
 *
 * <p>UNTUNED: every figure is a first-pass number.
 */
public final class CoverDuty {

    /** Seconds on station before the fighter turns for home. UNTUNED. */
    public static final float STATION_SECONDS = 80f;
    /** Rounds in the nose guns' air-to-air magazine; a pass that holds the target under the nose spends a handful. UNTUNED. */
    public static final int AIR_ROUNDS = 120;
    /** Longest the fighter chases one aircraft before it gives it up and returns to the ring. UNTUNED. */
    public static final float INTERCEPT_SECONDS = 14f;
    /** Gun passes on known ground threats a sortie may make from its station. UNTUNED. */
    public static final int GROUND_PASSES = 2;
    /** Seconds the fighter holds the ring before it will take another ground pass or a new chase. UNTUNED. */
    public static final float REST_SECONDS = 4f;
    /** Floor on the ring's radius, in cells; the ring is never tighter than the airframe can fly. UNTUNED. */
    public static final float RING_FLOOR_CELLS = 20f;
    /** How much wider than the airframe's own tightest circle the ring is flown. */
    public static final float RING_TURN_MARGIN = 1.2f;

    /** The ground this fighter covers. */
    public final float coverX;
    public final float coverY;

    /** Seconds on station left; counted while the fighter is on the ring or working from it. */
    public float stationSecondsLeft = STATION_SECONDS;
    /** Air-to-air rounds left. */
    public int airRoundsLeft = AIR_ROUNDS;
    /** Chase seconds left against the current prey. */
    public float interceptSecondsLeft;
    /** Seconds before the next chase or ground pass may begin. */
    public float restSecondsLeft;
    /** Seconds before the guns fire the next air-to-air round. */
    public float fireCooldown;
    /** Ground passes left. */
    public int groundPassesLeft = GROUND_PASSES;

    /**
     * Whether this fighter flies the old head-on lead pursuit instead of the tail chase ({@link AirIntercept}). A scene's
     * comparison control and nothing else: it is set where the sortie is made, from the pool it was launched from.
     */
    public boolean headOnPursuit;

    /** The aircraft (or flier, {@link FlierPrey}) being pursued, or {@code 0}. Set by tasking, cleared by flight when the prey is gone. */
    public long preyId;
    /**
     * A flier prey's position last tick and whose it was, so the chase can read its motion: a flier is a unit and
     * carries no velocity the intercept could read.
     */
    public long trackedFlier;
    public float flierLastX;
    public float flierLastY;

    /** Ground the fighter has been told to work, or none: set by tasking, consumed when the pass begins. */
    public boolean strafeRequested;
    public float strafeX;
    public float strafeY;
    /** Whether a ground pass is being flown; while it is, the guns' aim point is {@link #strafeX}/{@link #strafeY}. */
    public boolean strafing;

    /** Rounds fired at aircraft this sortie, for evidence. */
    public int airRoundsFired;
    /** Rounds that struck an aircraft this sortie, for evidence. */
    public int airRoundsHit;
    /** Chases begun this sortie, for evidence. */
    public int chases;
    /** Ground passes flown this sortie, for evidence. */
    public int groundPassesFlown;

    public CoverDuty(float coverX, float coverY) {
        this.coverX = coverX;
        this.coverY = coverY;
    }

    /** The ring radius a hull with this tightest circle flies about the covered ground. */
    public static float ringRadius(float minTurnRadiusCells) {
        float turn = Float.isFinite(minTurnRadiusCells) && minTurnRadiusCells < 1000f ? minTurnRadiusCells : 0f;
        return Math.max(RING_FLOOR_CELLS, turn * RING_TURN_MARGIN);
    }

    /** Phase clocks advance before choosing normal or evasive steering, once per tick. */
    public void advanceClocks(ShuttleState phase, float dt) {
        if (phase == ShuttleState.ORBIT) {
            stationSecondsLeft -= dt;
            restSecondsLeft = Math.max(0f, restSecondsLeft - dt);
        } else if (phase == ShuttleState.INTERCEPT) {
            stationSecondsLeft -= dt;
            interceptSecondsLeft -= dt;
        }
    }

    public boolean spent() { return !hasAirRounds() && groundPassesLeft <= 0; }

    /** Whether the fighter is free to take new work: not resting and not out of the means to do any. */
    public boolean readyForWork() {
        return restSecondsLeft <= 0f && stationSecondsLeft > 0f;
    }

    /** Whether the nose guns can still be fired at aircraft. */
    public boolean hasAirRounds() {
        return airRoundsLeft > 0;
    }
}
