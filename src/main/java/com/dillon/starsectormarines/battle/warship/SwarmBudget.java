package com.dillon.starsectormarines.battle.warship;

/**
 * The arithmetic that keeps a swarm honest: however many bodies a salvo becomes,
 * the weapon's damage and area are the budget, never a ceiling or a floor.
 *
 * <p>Damage adds, so {@code n} bodies carry {@code D / n} each. Area adds too,
 * and area goes with the square of the radius, so {@code n} bodies carry a
 * radius of {@code R / sqrt(n)} each. Where the spec already gives a body count
 * (a burst, a warhead count) the shares are the spec's and nothing is re-derived.
 *
 * <p>Bodies cost drawing, point-defence scans and detonations, so they are capped:
 * {@link #PER_VOLLEY} in one pull of the trigger, {@link #PER_SHIP} live in the
 * air from one hull, {@link #PER_BATTLE} in the whole battle. Over a cap
 * bodies are <i>fused</i>: folded into fewer, heavier ones whose damage and area
 * are the sums, so the picture is thinned and the budget is not.
 */
public final class SwarmBudget {

    /** The most bodies one pull of a trigger puts in the air. */
    public static final int PER_VOLLEY = 32;
    /** The most bodies one hull has in the air at once. */
    public static final int PER_SHIP = 160;
    /** The most bodies in the air in the whole battle. */
    public static final int PER_BATTLE = 480;

    private SwarmBudget() {}

    /** The damage one of {@code n} equal bodies carries so that together they carry {@code total}. */
    public static float shareDamage(float total, int n) {
        return total / Math.max(1, n);
    }

    /** The blast radius one of {@code n} equal bodies has so that together they cover the area of one of {@code radius}. */
    public static float shareRadius(float radius, int n) {
        return radius / (float) Math.sqrt(Math.max(1, n));
    }

    /**
     * How many of {@code wanted} bodies may be put in the air now: at least one,
     * and no more than the volley, the hull's remaining room and the battle's.
     */
    public static int allowed(int wanted, int shipLive, int battleLive) {
        int room = Math.min(PER_VOLLEY, Math.min(PER_SHIP - shipLive, PER_BATTLE - battleLive));
        return Math.max(1, Math.min(wanted, room));
    }

    /** The radius of one body that has the combined area of two. */
    public static float mergeRadius(float a, float b) {
        return (float) Math.sqrt(a * a + b * b);
    }

    /**
     * Folds {@code damage.length} bodies into {@code cap}: body {@code i} joins
     * group {@code i % cap}, and a group's damage is the sum of its members' and
     * its radius the root of the sum of their squared radii, so damage and area
     * are both conserved. Returns {@code {damages, radii}}; with room to spare it
     * returns the inputs unchanged.
     */
    public static float[][] fuse(float[] damage, float[] radius, int cap) {
        int n = damage.length;
        if (cap >= n) return new float[][]{damage.clone(), radius.clone()};
        int groups = Math.max(1, cap);
        float[] fusedDamage = new float[groups];
        float[] areaSum = new float[groups];
        for (int i = 0; i < n; i++) {
            fusedDamage[i % groups] += damage[i];
            areaSum[i % groups] += radius[i] * radius[i];
        }
        float[] fusedRadius = new float[groups];
        for (int g = 0; g < groups; g++) fusedRadius[g] = (float) Math.sqrt(areaSum[g]);
        return new float[][]{fusedDamage, fusedRadius};
    }
}
