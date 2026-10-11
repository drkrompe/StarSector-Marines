package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

/**
 * Chooses where a warship should fire: the densest concentration of hostile
 * force within its reach that no friendly warship is already working.
 *
 * <p>It only ever returns an ordinary area order, so a director is nothing a
 * mission could not do by hand; it is the mission's default when it has not
 * named ground. Density is the count of hostile units within the area's radius
 * of a candidate, the centre is the centroid of those, and ties go to the
 * nearer ground.
 */
public final class WarshipDirector {

    private WarshipDirector() {}

    /**
     * The best area for {@code ship} to fire on, or {@code null} when no hostile
     * stands inside its reach that another ship is not already covering.
     */
    public static WarshipOrder.FireOnArea choose(BattleSimulation sim, Warship ship, Iterable<Warship> allShips) {
        float radius = ship.directorRadius();
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] dense = roster.denseArray();
        int n = roster.liveCount();
        float[] xs = new float[n];
        float[] ys = new float[n];
        int hostiles = 0;
        for (int i = 0; i < n; i++) {
            long u = dense[i];
            Faction f = roster.identity().faction(u);
            if (!ship.faction.hostileTo(f)) continue;
            xs[hostiles] = world.x(u);
            ys[hostiles] = world.y(u);
            hostiles++;
        }
        float reach = reach(ship) + radius;
        float bestScore = 0f;
        float bestDistance = Float.MAX_VALUE;
        float bestX = 0f;
        float bestY = 0f;
        for (int i = 0; i < hostiles; i++) {
            float distance = distance(ship.x, ship.y, xs[i], ys[i]);
            if (distance > reach || covered(ship, allShips, xs[i], ys[i])) continue;
            int count = 0;
            float sumX = 0f;
            float sumY = 0f;
            for (int j = 0; j < hostiles; j++) {
                if (distance(xs[i], ys[i], xs[j], ys[j]) > radius) continue;
                count++;
                sumX += xs[j];
                sumY += ys[j];
            }
            if (count > bestScore || (count == bestScore && distance < bestDistance)) {
                bestScore = count;
                bestDistance = distance;
                bestX = sumX / count;
                bestY = sumY / count;
            }
        }
        return bestScore == 0f ? null
                : new WarshipOrder.FireOnArea(bestX, bestY, radius, ship.directorBringsGunsToBear());
    }

    /** Whether any other ship of {@code ship}'s side already has an area order over this ground. */
    private static boolean covered(Warship ship, Iterable<Warship> allShips, float x, float y) {
        for (Warship other : allShips) {
            if (other == ship || other.faction != ship.faction) continue;
            if (other.order() instanceof WarshipOrder.FireOnArea area && area.contains(x, y)) return true;
        }
        return false;
    }

    /** The longest reach of any gun the ship carries, in cells. */
    static float reach(Warship ship) {
        float longest = 0f;
        for (Warship.Mount m : ship.mounts()) {
            longest = Math.max(longest, WarshipScale.cells(m.weapon.rangePx()));
        }
        return longest;
    }

    private static float distance(float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
