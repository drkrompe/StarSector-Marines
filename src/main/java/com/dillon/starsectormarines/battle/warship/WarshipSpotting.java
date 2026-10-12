package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vision.PlayerVisionState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a friendly warship lets the player see: the hull's own surroundings, the
 * ground it has been ordered to fire on, and every place one of its rounds or
 * lance tips is about to land.
 *
 * <p>A ship that destroys ground the player cannot see is a ship the player
 * cannot judge, and a capital overhead is exactly where a spotter would be, so
 * these are observation sources like a shuttle's: they are pushed into fog as
 * temporary sources and open ground, and roofs, by the ordinary reveal rule.
 * A hostile ship gives the player nothing; fog does not show them what an enemy
 * is hitting. Fog remains presentation authority: nothing here changes what the
 * ship can fire at.
 */
public final class WarshipSpotting {

    /** How far a hull sees about itself, in cells. */
    private static final int HULL_RANGE = 50;
    /** Walls within this many cells of a spotted point are seen over, as for an aircraft. */
    private static final float HULL_WALL_CLEARANCE = 8f;
    private static final float GROUND_WALL_CLEARANCE = 6f;
    /** Rounds are lit this far past their blast so the whole burst is seen, not only its centre. */
    private static final int BLAST_MARGIN = 4;
    private static final int MAX_ROUND_SOURCES = 24;
    private static final int LANCE_TIP_RANGE = 8;

    private WarshipSpotting() {}

    /** One observation source: its cell, how far it sees and how far over walls it sees. */
    public record Source(int cellX, int cellY, int range, float wallClearance) {}

    /** Every source the player's side gets from the warships over the battle this frame. */
    public static List<Source> sources(BattleSimulation sim) {
        PlayerVisionState vision = sim.getVisionState();
        List<Source> out = new ArrayList<>();
        Set<Long> roundCells = new HashSet<>();
        for (Warship ship : sim.warships().ships()) {
            if (ship.destroyed() || !vision.isContributor(ship.faction)) continue;
            out.add(new Source((int) Math.floor(ship.x), (int) Math.floor(ship.y), HULL_RANGE, HULL_WALL_CLEARANCE));
            for (WarshipOrder.FireOnArea area : ship.areas()) {
                out.add(new Source((int) Math.floor(area.x()), (int) Math.floor(area.y()),
                        (int) Math.ceil(area.radius()) + BLAST_MARGIN, GROUND_WALL_CLEARANCE));
            }
        }
        for (WarshipShell shell : sim.warships().shells()) {
            if (!vision.isContributor(shell.faction) || roundCells.size() >= MAX_ROUND_SOURCES) continue;
            int cx = (int) Math.floor(shell.toX);
            int cy = (int) Math.floor(shell.toY);
            // Rounds that land within two cells of one already lit share it.
            if (!roundCells.add(((long) (cx / 2) << 32) | ((cy / 2) & 0xffffffffL))) continue;
            out.add(new Source(cx, cy, (int) Math.ceil(shell.blastCells) + BLAST_MARGIN, GROUND_WALL_CLEARANCE));
        }
        for (LanceSweepSystem.Active a : sim.lanceSweeps().active()) {
            LanceSweep s = a.sweep;
            if (!vision.isContributor(s.faction()) || !a.isBurning()) continue;
            float angle = a.angle();
            out.add(new Source(
                    (int) Math.floor(s.originX() + Math.cos(angle) * s.reach() * 0.6f),
                    (int) Math.floor(s.originY() + Math.sin(angle) * s.reach() * 0.6f),
                    LANCE_TIP_RANGE + (int) Math.ceil(s.halfWidth()), GROUND_WALL_CLEARANCE));
        }
        return out;
    }
}
