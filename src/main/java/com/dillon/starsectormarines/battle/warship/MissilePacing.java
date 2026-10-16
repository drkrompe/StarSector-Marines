package com.dillon.starsectormarines.battle.warship;

/**
 * How a warship spaces its missile launches so a salvo reads as a ripple of launches, not a cloud.
 *
 * <p>Each tube keeps its weapon's own volley (its burst size and burst delay), but no tube launches the next round of
 * its volley sooner than {@link #TUBE_INTERVAL_SECONDS}, and no tube on a hull <em>starts</em> a volley within
 * {@link #SHIP_STAGGER_SECONDS} of another tube on the same hull starting one. A pair of launchers therefore ripples
 * left, right, left rather than firing together, and a rack's long volley still runs at its own pace. Nothing about a
 * missile itself changes: its damage, flight and warheads are the weapon's.
 *
 * <p>This paces missiles a hull fires at hulls. A ground volley keeps its archetype's own cadence and stagger
 * ({@link MissileArchetype}): a bombardment rack is meant to fill the sky, and its cadence is tuned against the ground.
 */
public final class MissilePacing {

    /** The least time, in seconds, between two volleys starting from tubes on one hull. */
    public static final float SHIP_STAGGER_SECONDS = 0.75f;
    /** The least time, in seconds, between two rounds of one tube's volley. */
    public static final float TUBE_INTERVAL_SECONDS = 0.25f;

    private MissilePacing() {}

    /**
     * The hull share at or under which a finisher may be spent on a hull that is not overloaded ({@link #softened}).
     */
    public static final float FINISHER_HULL =
            Float.parseFloat(System.getProperty("battle.warship.finisherHull", "0.6"));

    /**
     * Whether {@code w} is a finisher by the base game's own role for it (a Harpoon is): a heavy warhead the base
     * game's AI saves for a hull it has already hurt, rather than an opener.
     */
    static boolean finisher(VanillaWeapon w) {
        return w.missile() && "Finisher".equalsIgnoreCase(VanillaWeapons.role(w.id()));
    }

    /**
     * Whether {@code target} is ready for a finisher: overloaded, or worn to {@link #FINISHER_HULL} of its hull. A
     * finisher is not a magazine to empty at first contact; held until then, an unshielded hull has its guns' time to
     * answer before the warheads come.
     */
    static boolean softened(Warship target) {
        return target.overloaded() || target.hullFraction() <= FINISHER_HULL;
    }

    /** The delay before the next round of a volley of {@code w}: its own, but never under the tube interval. */
    static float roundDelay(VanillaWeapon w, float spec) {
        return w.missile() ? Math.max(spec, TUBE_INTERVAL_SECONDS) : spec;
    }
}
