package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a tender knows about the enemy, and whether its builders are being shot.
 *
 * <p>A tender decides where to build and when to stand a site down from what its side
 * knows, not from where every hostile actually is. Its side knows two things: the contacts
 * its squads remember (at the cell each was last seen, not where it is now), and whatever
 * the tender's own eyes can see, which are the hull and every drone it has out, each looking
 * {@link #OVERHEAD_SIGHT_CELLS} round itself from the air.
 *
 * <p>What it does not know can surprise it. A builder that takes a hit puts its site
 * <b>under fire</b>: the work stops, the builders fly home, and the site stays blocked until
 * {@link #BLOCK_SECONDS} have passed without another hit. A shooter nobody can see therefore
 * keeps a site blocked for as long as it keeps shooting, and costs a builder each time the work
 * resumes into its sights; clearing it is the screen's job, or the squads'.
 *
 * <p>{@code -Dbattle.tender.fairThreat=false} restores the earlier rule for a control run:
 * every live hostile is known, wherever it is.
 */
public final class TenderThreat {

    /** The control switch. On by default. */
    public static final String PROPERTY = "battle.tender.fairThreat";
    /** How far the hull and its drones see from the air, in cells. */
    static final float OVERHEAD_SIGHT_CELLS = 20f;
    /** Seconds a site stays blocked after the last hit on one of its builders. */
    static final float BLOCK_SECONDS = 8f;

    private TenderThreat() {}

    static boolean fair() {
        return !"false".equalsIgnoreCase(System.getProperty(PROPERTY));
    }

    /**
     * Known hostile positions for {@code t}'s side, as flat {@code x, y} pairs: remembered
     * contacts at their last-seen cell, plus live hostiles inside the tender's own sight.
     * With the control switch off, every live hostile.
     */
    static float[] known(BattleSimulation sim, Tender t) {
        Faction side = t.faction();
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        TenderDirector.Points out = new TenderDirector.Points();
        long[] dense = roster.denseArray();
        int n = roster.liveCount();
        if (!fair()) {
            for (int i = 0; i < n; i++) {
                long u = dense[i];
                if (side.hostileTo(roster.identity().faction(u))) out.add(world.x(u), world.y(u));
            }
            return out.array();
        }
        float[] eyes = eyes(t, world);
        Set<Long> seen = new HashSet<>();
        float r2 = OVERHEAD_SIGHT_CELLS * OVERHEAD_SIGHT_CELLS;
        for (int i = 0; i < n; i++) {
            long u = dense[i];
            if (!side.hostileTo(roster.identity().faction(u))) continue;
            float x = world.x(u);
            float y = world.y(u);
            for (int e = 0; e < eyes.length; e += 2) {
                float dx = x - eyes[e];
                float dy = y - eyes[e + 1];
                if (dx * dx + dy * dy <= r2) {
                    out.add(x, y);
                    seen.add(u);
                    break;
                }
            }
        }
        for (Squad squad : sim.getSquads()) {
            if (squad.faction == null || !side.friendlyTo(squad.faction)) continue;
            for (BelievedContact c : squad.believedContacts()) {
                if (!seen.add(c.unitId()) || sim.resolveUnit(c.unitId()) == 0L) continue;
                out.add(c.lastSeenCellX() + 0.5f, c.lastSeenCellY() + 0.5f);
            }
        }
        return out.array();
    }

    /** The hull and every drone it has out, as flat {@code x, y} pairs. */
    private static float[] eyes(Tender t, World world) {
        TenderDirector.Points eyes = new TenderDirector.Points();
        eyes.add(t.ship().x, t.ship().y);
        for (Long id : t.builders) eyes.add(world.x(id), world.y(id));
        for (Long id : t.defenders) eyes.add(world.x(id), world.y(id));
        return eyes.array();
    }

    /** Whether any of {@code points} lies within {@code radius} of {@code (x, y)}. */
    static boolean near(float[] points, float x, float y, float radius) {
        float r2 = radius * radius;
        for (int i = 0; i < points.length; i += 2) {
            float dx = points[i] - x;
            float dy = points[i + 1] - y;
            if (dx * dx + dy * dy <= r2) return true;
        }
        return false;
    }

    /**
     * Notes each builder's hit points and returns whether any of {@code builders} lost some since
     * the last call. A builder seen for the first time has nothing to compare against; a builder
     * no longer listed is forgotten.
     */
    static boolean anyHit(List<Long> builders, World world, Map<Long, Float> lastHp) {
        return hits(builders, world, lastHp, new ArrayList<>());
    }

    /** As {@link #anyHit}, appending each builder that lost hit points to {@code struck}. */
    static boolean hits(List<Long> builders, World world, Map<Long, Float> lastHp, List<Long> struck) {
        boolean hit = false;
        lastHp.keySet().retainAll(new HashSet<>(builders));
        for (Long id : builders) {
            float hp = world.hp(id);
            Float before = lastHp.put(id, hp);
            if (before != null && hp < before) {
                hit = true;
                struck.add(id);
            }
        }
        return hit;
    }
}
