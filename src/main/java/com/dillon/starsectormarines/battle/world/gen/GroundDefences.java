package com.dillon.starsectormarines.battle.world.gen;

/**
 * The ground defences a battle carries, as its mission declared them: how many fortress batteries, whether a shield
 * generator, and how many defence nests. The mission is the only authority for this. Map generation stages and battle
 * setup place exactly what the declaration says and never consult the market or the map's size to decide it; the
 * market's industries reach a mission only as signals on {@link TargetProfile}, which a mission definition may read
 * when it makes its declaration.
 *
 * <p>{@link #NONE} is the default for a mission that says nothing.
 *
 * @param batteries        fortress batteries to stand on the map; never negative
 * @param shieldGenerator  whether the defending garrison holds a shield generator beside its keep
 * @param nests            defence nests (small hardened anti-air and anti-infantry emplacements) to scatter over the
 *                         map; never negative
 */
public record GroundDefences(int batteries, boolean shieldGenerator, int nests) {

    public static final GroundDefences NONE = new GroundDefences(0, false, 0);

    public GroundDefences {
        if (batteries < 0) batteries = 0;
        if (nests < 0) nests = 0;
    }

    /** A declaration with no nests, for callers that speak only of the large structures. */
    public GroundDefences(int batteries, boolean shieldGenerator) {
        this(batteries, shieldGenerator, 0);
    }

    /** True when the declaration places nothing at all. */
    public boolean isNone() {
        return batteries == 0 && !shieldGenerator && nests == 0;
    }
}
