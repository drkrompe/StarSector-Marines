package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;
import java.util.function.Function;

/**
 * Whether a strike sortie should be sent at a hostile hull instead of at the
 * ground, and which hull.
 *
 * <p>A hull is a candidate target beside the densest ground concentration
 * ({@link EnemyConcentration}), and when one is eligible it takes precedence.
 * Eligible means all of: the craft's ordnance can get through that hull's armour
 * at all (the shared armour rule, so a strafing cannon does not chase a plate it
 * glances off), and fewer than {@link #MAX_ATTACKERS} of this side's craft are
 * already working it. The cap is what keeps one capital from absorbing every
 * sortie: the remainder go to the ground as they always did.
 *
 * <p>This chooses a target and nothing else. The craft flies the ordinary attack
 * run at the hull's centre and its rounds land as ordinary detonations; whether
 * they hurt, and what the shield and armour make of them, is the hull's own
 * ({@code WarshipSystem.onDetonation}). There is no second damage route.
 */
public final class HullStrike {

    /** Most craft of one side that will work one hull at a time. */
    public static final int MAX_ATTACKERS = 2;

    /** Cells beyond a hull's own radius within which a sortie's target counts as that hull. */
    private static final float ATTACK_MARGIN_CELLS = 2f;

    private HullStrike() {}

    /**
     * The hostile hull a sortie of {@code side} should strike, or {@code null}
     * when none is eligible and the sortie should go to the ground.
     *
     * @param loadFor the ordnance the craft that would fly at a given hull carries; may return {@code null}
     *                (nothing to drop, never eligible)
     */
    public static HullTarget choose(BattleSimulation sim, Faction side, Function<HullTarget, AirOrdnance> loadFor) {
        List<HullTarget> hulls = sim.hostileHulls(side);
        HullTarget best = null;
        int bestAttackers = Integer.MAX_VALUE;
        for (HullTarget hull : hulls) {
            AirOrdnance load = loadFor.apply(hull);
            if (load == null || !sim.ordnanceHurtsHull(load, hull)) continue;
            int attackers = attackers(sim, side, hull);
            if (attackers >= MAX_ATTACKERS) continue;
            if (attackers < bestAttackers) {
                best = hull;
                bestAttackers = attackers;
            }
        }
        return best;
    }

    /** How many of {@code side}'s sorties still to deliver or delivering are aimed at {@code hull}. */
    static int attackers(BattleSimulation sim, Faction side, HullTarget hull) {
        World world = sim.world();
        float reach = hull.radius() + ATTACK_MARGIN_CELLS;
        int out = 0;
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = world.mission(id);
            if (mission == null || !mission.strikeSortie || !working(mission.state)) continue;
            if (world.airFaction(id) != side) continue;
            if (Math.hypot(mission.lzX - hull.x(), mission.lzY - hull.y()) <= reach) out++;
        }
        return out;
    }

    /**
     * Whether a strike sortie that still has its attack ahead of it or in hand is aimed at the hull centred on
     * {@code (x, y)} with the given radius: its target point lies within the hull's footprint and the strike margin.
     * The same test {@link #attackers} counts by, so an escort and a dispatcher agree on what "working this hull" means.
     */
    public static boolean aimedAt(ShuttleMission mission, float x, float y, float radius) {
        if (mission == null || !mission.strikeSortie || !working(mission.state)) return false;
        return Math.hypot(mission.lzX - x, mission.lzY - y) <= radius + ATTACK_MARGIN_CELLS;
    }

    /** Whether a sortie in this state still has its attack ahead of it or in hand. */
    private static boolean working(ShuttleState state) {
        return switch (state) {
            case PENDING, TAXI_OUT, HOLDING_SHORT, TAKEOFF_ROLL, INCOMING, REPOSITION, ATTACK_RUN -> true;
            default -> false;
        };
    }
}
