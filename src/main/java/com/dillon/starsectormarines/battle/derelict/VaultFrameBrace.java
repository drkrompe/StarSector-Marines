package com.dillon.starsectormarines.battle.derelict;

/**
 * The vault guardian's stance (`ruins-guardians.md`): the vault frame, derelict security's vault guardian, braces in
 * its vault hall by planting its four legs out on the walls. Braced, it fires at its full rate and its front takes
 * hits on doubled plate, but it cannot turn: its rear is soft, a hit there throws it out of its brace to turn, and a
 * target that leaves its front arc does the same. Bracing again takes {@link #BRACE_SECONDS}. So the counter is two
 * squads through two doors: one holds its front, the other hits it from behind while it turns.
 *
 * <p>The rule is pure: bearings are degrees from the frame's facing to the shooter or target, either sign. Numbers
 * are UNTUNED.
 */
public final class VaultFrameBrace {

    /** Seconds stood still and engaged before the legs are planted. UNTUNED. */
    public static final float BRACE_SECONDS = 2f;
    /** Half-angle of the braced front arc, degrees. */
    public static final float FRONT_ARC_DEGREES = 60f;
    /** Half-angle of the soft rear arc, degrees, measured from directly behind. */
    public static final float REAR_ARC_DEGREES = 50f;
    /** Damage scale on the braced front: doubled plate. UNTUNED. */
    public static final float FRONT_SCALE = 0.5f;
    /** Damage scale on the braced rear. UNTUNED. */
    public static final float REAR_SCALE = 1.5f;
    /** Fire rate scale while not braced: it walks and turns with its guns off their stops. UNTUNED. */
    public static final float FREE_FIRE_SCALE = 0.5f;

    /** The frame's stance. */
    public enum Stance {
        /** Walking or turning: ordinary plate, half fire. */
        FREE,
        /** Planting its legs. */
        BRACING,
        /** Legs on the walls: hard front, soft rear, full fire, no turning. */
        BRACED
    }

    private Stance stance = Stance.FREE;
    private float bracing;

    /**
     * Advances the stance by {@code dt} seconds.
     *
     * @param moving        whether the frame is walking or turning this tick
     * @param engaged       whether it has a target
     * @param targetBearing degrees from its facing to its target (ignored when not engaged)
     */
    public void tick(float dt, boolean moving, boolean engaged, float targetBearing) {
        if (moving || !engaged || !inFront(targetBearing)) {
            free();
            return;
        }
        if (stance == Stance.BRACED) return;
        stance = Stance.BRACING;
        bracing += dt;
        if (bracing >= BRACE_SECONDS) stance = Stance.BRACED;
    }

    /**
     * A hit taken from {@code bearing}; returns the scale on its damage. A braced frame hit in the rear takes the
     * hit soft and comes out of its brace to turn.
     */
    public float hit(float bearing) {
        if (stance != Stance.BRACED) return 1f;
        if (inFront(bearing)) return FRONT_SCALE;
        if (inRear(bearing)) {
            free();
            return REAR_SCALE;
        }
        return 1f;
    }

    /** The scale on its fire rate now. */
    public float fireScale() {
        return stance == Stance.BRACED ? 1f : FREE_FIRE_SCALE;
    }

    public Stance stance() { return stance; }

    private void free() {
        stance = Stance.FREE;
        bracing = 0f;
    }

    static boolean inFront(float bearing) {
        return Math.abs(wrap(bearing)) <= FRONT_ARC_DEGREES;
    }

    static boolean inRear(float bearing) {
        return Math.abs(wrap(bearing)) >= 180f - REAR_ARC_DEGREES;
    }

    /** The bearing in (-180, 180]. */
    static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d > 180f) d -= 360f;
        if (d <= -180f) d += 360f;
        return d;
    }
}
