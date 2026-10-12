package com.dillon.starsectormarines.battle.warship;

/**
 * How literal a warship's missiles are, selectable per ship.
 *
 * <p>The owner's direction is spectacle first: the reference is a cluster
 * munition that launches and, on reaching an area, splits into submunitions
 * that hit at random for a large burst. The game is 2D, so the aim is the feel.
 * The presets differ only in flair; none of them adds or removes ordnance, so
 * the weapon's damage and area stay the budget in every one
 * ({@link SwarmBudget}).
 *
 * <ul>
 *   <li>{@link #FAITHFUL}: the spec's numbers only. Warheads fly straight, land
 *       in a tight stagger, rack rounds keep the weapon's cone, nothing is
 *       telegraphed and nothing is split that the spec does not split.</li>
 *   <li>{@link #FLAVOURED} (default): a randomly scattered, staggered bloom of
 *       curving, weaving warheads, rack volleys scattered over a footprint and
 *       compressed into a rolling barrage, a faint telegraph ring.</li>
 *   <li>{@link #CINEMATIC}: as flavoured, over a longer and wider drumroll, with
 *       the Hammer family also splitting into a cluster inside the budget rule.</li>
 * </ul>
 */
public enum MissilePreset {

    FAITHFUL(false, false, 0.15f, 0.8f, 1f, Float.POSITIVE_INFINITY, false, false),
    FLAVOURED(true, true, 0.6f, 2.5f, 1f, 3f, true, false),
    CINEMATIC(true, true, 1.2f, 4f, 1.3f, 2.5f, true, true);

    /** Whether a rack volley is scattered over a footprint instead of fired at one cone. */
    private final boolean scatterRacks;
    /** Whether warheads fan out of the split, curve and weave onto their aim points. */
    private final boolean curvedBloom;
    private final float arrivalMinSeconds;
    private final float arrivalMaxSeconds;
    private final float scatterScale;
    private final float rackVolleySeconds;
    private final boolean telegraph;
    private final boolean syntheticSplit;

    MissilePreset(boolean scatterRacks, boolean curvedBloom, float arrivalMinSeconds, float arrivalMaxSeconds,
                  float scatterScale, float rackVolleySeconds, boolean telegraph, boolean syntheticSplit) {
        this.scatterRacks = scatterRacks;
        this.curvedBloom = curvedBloom;
        this.arrivalMinSeconds = arrivalMinSeconds;
        this.arrivalMaxSeconds = arrivalMaxSeconds;
        this.scatterScale = scatterScale;
        this.rackVolleySeconds = rackVolleySeconds;
        this.telegraph = telegraph;
        this.syntheticSplit = syntheticSplit;
    }

    public boolean scatterRacks() {
        return scatterRacks;
    }

    public boolean curvedBloom() {
        return curvedBloom;
    }

    /** Seconds after a split before the first warhead lands. */
    public float arrivalMinSeconds() {
        return arrivalMinSeconds;
    }

    /** Seconds after a split by which the last warhead lands, unless its own travel needs longer. */
    public float arrivalMaxSeconds() {
        return arrivalMaxSeconds;
    }

    /** A multiplier on the footprint a volley or sheaf is scattered over. */
    public float scatterScale() {
        return scatterScale;
    }

    /** The longest a rack's burst is allowed to take to fire, so a barrage rolls rather than trickles. */
    public float rackVolleySeconds() {
        return rackVolleySeconds;
    }

    /** Whether a faint ring marks the footprint while the carrier or volley is in flight. */
    public boolean telegraph() {
        return telegraph;
    }

    /** Whether weapons the spec does not split (the Hammer family) split anyway, inside the budget. */
    public boolean syntheticSplit() {
        return syntheticSplit;
    }

    /** The next preset in the cycle, wrapping; the debug panel steps through them. */
    public MissilePreset next() {
        MissilePreset[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
