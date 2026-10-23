package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.sim.ConvoyService;

import java.util.Arrays;

/**
 * A hull crew caught in crossfire ({@code convoy-nouns.md}, what presents the front). A crew watches one way: its gun
 * stays on the machine it is fighting and its front on the one it turned to. Hits arriving from two machines
 * at once, from bearings at least {@link #SEPARATION_DEGREES} apart, rattle it, and while they keep coming its gun
 * shoots at a moving shooter's accuracy ({@link FireStance#MOVING}). So a pincer pays through attention: one machine
 * draws the gun head-on while the other works the side, and the first takes less return fire for it.
 *
 * <p>Only two shooters apart count. One machine is never a crossfire however it circles, and two on one approach
 * are not either, so a one-on-one fight and a head-on pair play as before. The bearing is the world bearing from the
 * hull to where the shooter stands when its hit lands, so the hull's own turning moves nothing. A hit is remembered
 * for {@link #WINDOW_SECONDS}; the crew steadies once one side falls quiet that long.
 *
 * <p>Live state of one hull, aged by the turret pass and written on the serialized damage path.
 */
public final class Crossfire {

    /**
     * {@code false} is the control: a crew fires as steadily under fire from two sides as from one. Temporary
     * (2026-10-23): decided by {@code armorMatchup} with this off as the control, two Hounds against the laser,
     * breakthrough and screened tanks at 32 seeds in a pincer ({@code -PattackerSpread=180}) and on one approach
     * ({@code -PattackerSpread=0}), plus the default ladder in both arms. Keep it, dropping the switch and this
     * control, if the pincer beats the single approach clearly while the one-on-one rows hold within noise; remove
     * the class otherwise.
     */
    public static final String PROPERTY = "battle.vehicle.crossfire";

    static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /**
     * Seconds a hit is remembered: a crew steadies once one side has been quiet this long. Long enough to span the
     * pause between an attacker's volleys (a Hound fires in bursts several seconds apart while it sheds flux), so a
     * crossfire holds while both machines keep at it; at four seconds it almost never held (readings in
     * {@code heavy-equipment-progression.md}, crossfire).
     */
    static final float WINDOW_SECONDS = 10f;
    /** Degrees apart two shooters' bearings must be to be two directions rather than one approach. */
    static final float SEPARATION_DEGREES = 90f;
    /** Distinct recent shooters remembered; the stalest gives way. */
    static final int SLOTS = 4;

    private final long[] shooter = new long[SLOTS];
    private final float[] bearing = new float[SLOTS];
    private final float[] age = new float[SLOTS];

    public Crossfire() {
        Arrays.fill(age, WINDOW_SECONDS);
    }

    /** A hit from {@code shooterId}, standing at world bearing {@code bearingDegrees} from the hull. */
    public void record(long shooterId, float bearingDegrees) {
        int slot = -1;
        for (int i = 0; i < SLOTS; i++) {
            if (shooter[i] == shooterId && age[i] < WINDOW_SECONDS) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            slot = 0;
            for (int i = 1; i < SLOTS; i++) {
                if (age[i] > age[slot]) slot = i;
            }
        }
        shooter[slot] = shooterId;
        bearing[slot] = bearingDegrees;
        age[slot] = 0f;
    }

    /** Ages every remembered hit by {@code dt} seconds. */
    public void advance(float dt) {
        if (!(dt > 0f)) return;
        for (int i = 0; i < SLOTS; i++) age[i] = Math.min(WINDOW_SECONDS, age[i] + dt);
    }

    /** Whether two different shooters have hit within the window from bearings at least the separation apart. */
    public boolean active() {
        for (int i = 0; i < SLOTS; i++) {
            if (age[i] >= WINDOW_SECONDS) continue;
            for (int j = i + 1; j < SLOTS; j++) {
                if (age[j] >= WINDOW_SECONDS || shooter[j] == shooter[i]) continue;
                if (separation(bearing[i], bearing[j]) >= SEPARATION_DEGREES) return true;
            }
        }
        return false;
    }

    /** The crew's gun accuracy: a moving shooter's under crossfire, else whole. */
    public float accuracyMultiplier() {
        return active() ? FireStance.MOVING.accuracyMult : 1f;
    }

    /** Hull {@code id}'s crossfire multiplier on its gun: one for anything but an armed hull, or with the control. */
    public static float accuracyMultiplier(ConvoyService convoy, long id) {
        if (!ENABLED || !convoy.isVehicle(id)) return 1f;
        GroundTurret turret = convoy.turret(id);
        return turret != null ? turret.crossfire.accuracyMultiplier() : 1f;
    }

    /** Unsigned angle between two bearings, in [0, 180]. */
    static float separation(float a, float b) {
        return Math.abs(((b - a) % 360f + 540f) % 360f - 180f);
    }
}
