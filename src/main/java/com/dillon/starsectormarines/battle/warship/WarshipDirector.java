package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.List;

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
        List<Warship.Held> picked = fill(sim, ship, allShips, List.of(), 1);
        return picked.isEmpty() ? null : picked.get(0).area;
    }

    /**
     * Up to {@code slots} further areas, densest first, each clear of {@code avoid}
     * (the ship's own areas already held), of every other friendly ship's areas and
     * of one another. Each carries the number of hostiles it held when chosen.
     */
    static List<Warship.Held> fill(BattleSimulation sim, Warship ship, Iterable<Warship> allShips,
                                   List<WarshipOrder.FireOnArea> avoid, int slots) {
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
        List<WarshipOrder.FireOnArea> others = new ArrayList<>();
        for (Warship other : allShips) {
            if (other == ship || other.faction != ship.faction) continue;
            others.addAll(other.areas());
        }
        return select(xs, ys, hostiles, ship.x, ship.y, reach(ship), ship.directorRadius(),
                ship.directorBringsGunsToBear(), avoid, others, slots);
    }

    /**
     * The selection rule on bare positions. A candidate is a hostile within
     * {@code reach + radius} of the ship that no held or other-ship area contains;
     * its area is the centroid of the free hostiles within {@code radius} of it,
     * and is refused if that centre lies closer than {@code radius} to any area
     * already held or chosen. The densest wins; ties go to the nearer ground.
     */
    static List<Warship.Held> select(float[] xs, float[] ys, int hostiles, float shipX, float shipY,
                                     float reach, float radius, boolean bringGunsToBear,
                                     List<WarshipOrder.FireOnArea> avoid,
                                     List<WarshipOrder.FireOnArea> others, int slots) {
        List<WarshipOrder.FireOnArea> blocked = new ArrayList<>(avoid);
        List<Warship.Held> out = new ArrayList<>();
        for (int s = 0; s < slots; s++) {
            int bestScore = 0;
            float bestDistance = Float.MAX_VALUE;
            float bestX = 0f;
            float bestY = 0f;
            for (int i = 0; i < hostiles; i++) {
                float distance = distance(shipX, shipY, xs[i], ys[i]);
                if (distance > reach + radius || inAny(others, xs[i], ys[i]) || inAny(blocked, xs[i], ys[i])) continue;
                int count = 0;
                float sumX = 0f;
                float sumY = 0f;
                for (int j = 0; j < hostiles; j++) {
                    if (distance(xs[i], ys[i], xs[j], ys[j]) > radius
                            || inAny(blocked, xs[j], ys[j]) || inAny(others, xs[j], ys[j])) continue;
                    count++;
                    sumX += xs[j];
                    sumY += ys[j];
                }
                float cx = sumX / count;
                float cy = sumY / count;
                if (tooClose(blocked, cx, cy, radius) || inAny(others, cx, cy)) continue;
                if (count > bestScore || (count == bestScore && distance < bestDistance)) {
                    bestScore = count;
                    bestDistance = distance;
                    bestX = cx;
                    bestY = cy;
                }
            }
            if (bestScore == 0) break;
            WarshipOrder.FireOnArea area = new WarshipOrder.FireOnArea(bestX, bestY, radius, bringGunsToBear);
            out.add(new Warship.Held(area, bestScore));
            blocked.add(area);
        }
        return out;
    }

    private static boolean inAny(List<WarshipOrder.FireOnArea> areas, float x, float y) {
        for (WarshipOrder.FireOnArea a : areas) if (a.contains(x, y)) return true;
        return false;
    }

    private static boolean tooClose(List<WarshipOrder.FireOnArea> areas, float x, float y, float radius) {
        for (WarshipOrder.FireOnArea a : areas) if (distance(a.x(), a.y(), x, y) < radius) return true;
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
