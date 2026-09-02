package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
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

    /** Cached once; {@code values()} clones its array on every call. */
    private static final Faction[] FACTIONS = Faction.values();

    private EnemyConcentration() {}

    /**
     * The densest live concentration of whatever {@code side} fights, or
     * {@code 0} when nothing on the map is worth a sortie.
     *
     * <p>Takes the searcher's own faction rather than a named enemy one: a side
     * may fight more than one faction, and a sortie sent at "the other side"
     * would fly past an allied militia standing in front of the garrison it was
     * meant to attack. Hostility is {@link Faction}'s to answer.
     *
     * <p>Walked only when a dispatcher is otherwise ready to fly, so the cost
     * is paid a handful of times in a battle rather than every tick.
     */
    public static long densest(BattleSimulation sim, Faction side) {
        return densestAwayFrom(sim, side, null, 0, 0f);
    }

    /**
     * The densest live concentration of whatever {@code side} fights that is at
     * least {@code minSeparation} cells from every position in {@code avoidXy},
     * or {@code 0} when there is no such thing.
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
    public static long densestAwayFrom(BattleSimulation sim, Faction side,
                                       float[] avoidXy, int avoidCount,
                                       float minSeparation) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long best = 0L;
        int bestCount = MIN_CLUSTER - 1;
        for (Faction enemy : FACTIONS) {
            if (!side.hostileTo(enemy)) continue;
            long[] candidates = roster.factionDenseArray(enemy);
            int count = roster.factionLiveCount(enemy);
            for (int i = 0; i < count; i++) {
                long u = candidates[i];
                if (tooClose(world.x(u), world.y(u), avoidXy, avoidCount, minSeparation)) continue;
                // Everything this side fights, not just the candidate's own
                // faction: a militia standing among a garrison is one
                // concentration and worth one sortie.
                int near = sim.getUnitIndex().countHostileCombatants(
                        world.x(u), world.y(u), CLUSTER_RADIUS, side, 0L);
                if (near > bestCount) {
                    bestCount = near;
                    best = u;
                }
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
