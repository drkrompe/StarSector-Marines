package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

/**
 * The enemy unit with the most friends around it — what an aircraft is sent at.
 *
 * <p>Density rather than distance or size. Nearest sends aircraft after
 * whichever scout wandered closest to the field; largest picks the same push
 * every time on a map with one big attack. Density is what an aircraft is
 * actually good against, and it is what a player can see the reason for
 * afterwards.
 *
 * <p>Asked by every dispatcher that puts an armed aircraft over the battle,
 * whichever origin the sortie flies from, because the question a strike answers
 * has nothing to do with where the aircraft came from.
 */
public final class EnemyConcentration {

    /** Cells around a candidate within which its friends count toward the concentration. */
    public static final float CLUSTER_RADIUS = 6f;

    /** Units that have to be inside {@link #CLUSTER_RADIUS} before it is worth a sortie. */
    public static final int MIN_CLUSTER = 4;

    /**
     * How far apart two concentrations have to be before they are worth
     * separate aircraft.
     *
     * <p>Two cluster radii: far enough that the two candidates cannot share a
     * single unit between them, so a second sortie sent here is attacking
     * somebody the first one is not. Anything narrower merely picks the
     * next-densest member of the same platoon, which is the same target with a
     * different name on it.
     */
    public static final float SEPARATE_TARGET_DIST = 2f * CLUSTER_RADIUS;

    private EnemyConcentration() {}

    /**
     * The densest live concentration of {@code enemy}, or {@code 0} when
     * nothing on the map is worth a sortie.
     *
     * <p>Walked only when a dispatcher is otherwise ready to fly, so the cost
     * is paid a handful of times in a battle rather than every tick.
     */
    public static long densest(BattleSimulation sim, Faction enemy) {
        return densestAwayFrom(sim, enemy, null, 0, 0f);
    }

    /**
     * The densest live concentration of {@code enemy} that is at least
     * {@code minSeparation} cells from every position in {@code avoidXy}, or
     * {@code 0} when there is no such thing.
     *
     * <p>What lets a field with several aircraft up spread them over the
     * battle instead of stacking them on one platoon. Two aircraft sent at the
     * same concentration is a legitimate tactic and stays available — the
     * caller falls back to {@link #densest} when this finds nothing — but it
     * has to be a decision rather than the only sentence the code can say.
     *
     * @param avoidXy    interleaved x, y of positions already being attacked;
     *                   may be null when nothing is
     * @param avoidCount how many pairs of {@code avoidXy} are populated
     */
    public static long densestAwayFrom(BattleSimulation sim, Faction enemy,
                                       float[] avoidXy, int avoidCount,
                                       float minSeparation) {
        UnitRosterService roster = sim.getRoster();
        long[] candidates = roster.factionDenseArray(enemy);
        int count = roster.factionLiveCount(enemy);
        LongBucket near = new LongBucket();
        World world = sim.world();
        long best = 0L;
        int bestCount = MIN_CLUSTER - 1;
        for (int i = 0; i < count; i++) {
            long u = candidates[i];
            if (tooClose(world.x(u), world.y(u), avoidXy, avoidCount, minSeparation)) continue;
            sim.getUnitIndex().gatherFaction(world.x(u), world.y(u),
                    CLUSTER_RADIUS, enemy, near);
            if (near.size > bestCount) {
                bestCount = near.size;
                best = u;
            }
        }
        return best;
    }

    /** Whether {@code (x, y)} is inside {@code minSeparation} of anything already engaged. */
    private static boolean tooClose(float x, float y, float[] avoidXy, int avoidCount,
                                    float minSeparation) {
        if (avoidXy == null || avoidCount <= 0 || minSeparation <= 0f) return false;
        float limit = minSeparation * minSeparation;
        for (int i = 0; i < avoidCount; i++) {
            float dx = x - avoidXy[i * 2];
            float dy = y - avoidXy[i * 2 + 1];
            if (dx * dx + dy * dy < limit) return true;
        }
        return false;
    }
}
