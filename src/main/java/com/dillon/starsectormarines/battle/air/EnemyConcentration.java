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

    private EnemyConcentration() {}

    /**
     * The densest live concentration of {@code enemy}, or {@code 0} when
     * nothing on the map is worth a sortie.
     *
     * <p>Walked only when a dispatcher is otherwise ready to fly, so the cost
     * is paid a handful of times in a battle rather than every tick.
     */
    public static long densest(BattleSimulation sim, Faction enemy) {
        UnitRosterService roster = sim.getRoster();
        long[] candidates = roster.factionDenseArray(enemy);
        int count = roster.factionLiveCount(enemy);
        LongBucket near = new LongBucket();
        World world = sim.world();
        long best = 0L;
        int bestCount = MIN_CLUSTER - 1;
        for (int i = 0; i < count; i++) {
            long u = candidates[i];
            sim.getUnitIndex().gatherFaction(world.x(u), world.y(u),
                    CLUSTER_RADIUS, enemy, near);
            if (near.size > bestCount) {
                bestCount = near.size;
                best = u;
            }
        }
        return best;
    }
}
