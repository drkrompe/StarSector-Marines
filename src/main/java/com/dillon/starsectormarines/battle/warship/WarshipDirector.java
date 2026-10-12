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
        return select(xs, ys, hostiles, ship.x, ship.y, Math.min(reach(ship), ship.opportunityRange()), ship.directorRadius(),
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

    // ---- the weighted director: objective bias and the friendly-fire rule ------------------------

    /** The weighted counterpart of {@link #fill}: the same candidates, scored for objective ground and friendlies. */
    static Selection fillWeighted(BattleSimulation sim, Warship ship, Iterable<Warship> allShips,
                                  List<WarshipOrder.FireOnArea> avoid, int slots,
                                  Friendlies friendlies, List<float[]> focus) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] dense = roster.denseArray();
        int n = roster.liveCount();
        float[] xs = new float[n];
        float[] ys = new float[n];
        int hostiles = 0;
        for (int i = 0; i < n; i++) {
            long u = dense[i];
            if (!ship.faction.hostileTo(roster.identity().faction(u))) continue;
            xs[hostiles] = world.x(u);
            ys[hostiles] = world.y(u);
            hostiles++;
        }
        List<WarshipOrder.FireOnArea> others = new ArrayList<>();
        for (Warship other : allShips) {
            if (other == ship || other.faction != ship.faction) continue;
            others.addAll(other.areas());
        }
        float radius = ship.directorRadius();
        return selectWeighted(xs, ys, hostiles, friendlies, focus, blastCells(ship, radius), SAFETY_MARGIN_CELLS,
                ship.dangerClose(), ship.x, ship.y, Math.min(reach(ship), ship.opportunityRange()), radius,
                ship.directorBringsGunsToBear(),
                avoid, others, slots);
    }

    /** Every friendly body on the map, plus the ship's declared protected ground. */
    static Friendlies friendliesOf(BattleSimulation sim, Warship ship) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] dense = roster.denseArray();
        int n = roster.liveCount();
        List<float[]> protectedGround = ship.protectedGround();
        float[] x = new float[n + protectedGround.size()];
        float[] y = new float[x.length];
        float[] weight = new float[x.length];
        float[] extra = new float[x.length];
        int count = 0;
        for (int i = 0; i < n; i++) {
            long u = dense[i];
            if (!ship.faction.friendlyTo(roster.identity().faction(u))) continue;
            x[count] = world.x(u);
            y[count] = world.y(u);
            weight[count] = 1f;
            count++;
        }
        for (float[] p : protectedGround) {
            x[count] = p[0];
            y[count] = p[1];
            weight[count] = PROTECTED_WEIGHT;
            extra[count] = p[2];
            count++;
        }
        return new Friendlies(x, y, weight, extra, count);
    }

    /** Cells added to a weapon's blast radius before a friendly counts as clear of it. */
    static final float SAFETY_MARGIN_CELLS = 3f;
    /** Score lost per friendly in the blast: heavy, so a dirty area loses to nearly any clean one. */
    static final float FRIENDLY_PENALTY = 8f;
    /** How many friendlies a declared protected position counts as. */
    static final float PROTECTED_WEIGHT = 10f;

    /**
     * Friendly bodies and protected ground the weighted director must not shell.
     * Entry {@code i} is at {@code (x[i], y[i])}, counts {@code weight[i]} and
     * is endangered by a round landing within {@code blast + margin + extra[i]}
     * of it (a protected position carries its own radius in {@code extra}).
     */
    record Friendlies(float[] x, float[] y, float[] weight, float[] extra, int count) {
        static final Friendlies NONE = new Friendlies(new float[0], new float[0], new float[0], new float[0], 0);
    }

    /** The weighted choice: the areas picked, and whether hostile ground in reach was refused for friendlies. */
    record Selection(List<Warship.Held> areas, boolean refusedForFriendlies) {}

    /**
     * The weighted selection rule on bare positions: the shipped rule's
     * candidates, scored {@code hostiles * (1 + GAIN * proximity) - FRIENDLY_PENALTY * friendlyWeight}
     * where proximity is the candidate's closeness to the mission's objective
     * ground. A friendly counts when a round aimed at any hostile in the area
     * (so one landing within {@code blast}) would reach it within the safety
     * {@code margin}. An area with any friendly exposure is refused unless
     * {@code dangerClose}, in which case it stays a candidate but pays the
     * penalty, so the ground with fewer friendlies still wins. Ties go to the
     * nearer ground.
     */
    static Selection selectWeighted(float[] xs, float[] ys, int hostiles, Friendlies friendlies,
                                    List<float[]> focus, float blast, float margin, boolean dangerClose,
                                    float shipX, float shipY, float reach, float radius, boolean bringGunsToBear,
                                    List<WarshipOrder.FireOnArea> avoid, List<WarshipOrder.FireOnArea> others,
                                    int slots) {
        List<WarshipOrder.FireOnArea> blocked = new ArrayList<>(avoid);
        List<Warship.Held> out = new ArrayList<>();
        boolean refused = false;
        for (int s = 0; s < slots; s++) {
            boolean foundAny = false;
            boolean foundClean = false;
            float bestScore = -Float.MAX_VALUE;
            float bestDistance = Float.MAX_VALUE;
            int bestCount = 0;
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
                foundAny = true;
                float exposure = exposure(xs, ys, hostiles, cx, cy, radius, friendlies, blast + margin);
                if (exposure > 0f && !dangerClose) continue;
                foundClean = true;
                float proximity = ObjectiveBias.proximity(cx, cy, focus);
                float score = count * (1f + ObjectiveBias.GAIN * proximity) - FRIENDLY_PENALTY * exposure;
                if (score > bestScore || (score == bestScore && distance < bestDistance)) {
                    bestScore = score;
                    bestDistance = distance;
                    bestCount = count;
                    bestX = cx;
                    bestY = cy;
                }
            }
            if (!foundClean) {
                refused |= foundAny;
                break;
            }
            WarshipOrder.FireOnArea area = new WarshipOrder.FireOnArea(bestX, bestY, radius, bringGunsToBear);
            out.add(new Warship.Held(area, bestCount));
            blocked.add(area);
        }
        return new Selection(out, refused && out.isEmpty());
    }

    /**
     * The weight of friendlies a round aimed at any hostile within {@code radius}
     * of {@code (cx, cy)} would reach: those within {@code danger} (plus their
     * own extra radius) of one of those hostiles.
     */
    static float exposure(float[] xs, float[] ys, int hostiles, float cx, float cy, float radius,
                          Friendlies friendlies, float danger) {
        float total = 0f;
        for (int f = 0; f < friendlies.count(); f++) {
            float fx = friendlies.x()[f];
            float fy = friendlies.y()[f];
            float reach = danger + friendlies.extra()[f];
            // Cheap reject: nothing in the area can be within reach of a friendly farther than this.
            if (distance(cx, cy, fx, fy) > radius + reach) continue;
            for (int h = 0; h < hostiles; h++) {
                if (distance(cx, cy, xs[h], ys[h]) > radius) continue;
                if (distance(fx, fy, xs[h], ys[h]) <= reach) {
                    total += friendlies.weight()[f];
                    break;
                }
            }
        }
        return total;
    }

    /** The blast, in cells, that a round of the ship's area weapons can put on a friendly: a shell's, or the whole area for a lance. */
    static float blastCells(Warship ship, float areaRadius) {
        float blast = 1f;
        for (Warship.Mount m : ship.mounts()) {
            if (m.pointDefence()) continue;
            if (m.weapon.beam()) {
                if (m.weapon.damagePerSecond() >= WarshipSystem.LANCE_MIN_DPS) blast = Math.max(blast, areaRadius);
            } else {
                blast = Math.max(blast, WarshipScale.cells(m.weapon.explosionRadiusPx()));
            }
        }
        return blast;
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
