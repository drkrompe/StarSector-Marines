package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/**
 * Stands up the company's Vulcans at the lift at setup, as a flying tender's
 * finished builds would leave them ({@code colony-breach-nouns.md},
 * Hardpoint). A fixture statement only: a launched battle gets its hardpoints
 * from a tender called down in play, and this exists so evidence can ask the
 * readiness gate what a hardened lift does without flying one.
 *
 * <p>The turrets go on open ground in rings round the pickup cell, outside the
 * lift zone itself, at least {@link #SPACING} cells apart, nearest ring first
 * and in scan order within a ring, so the same map always gets the same
 * hardpoints.
 */
public final class LiftHardpoints {

    /** The nearest ring searched, in cells from the pickup. */
    static final int MIN_RING = 5;
    /** The furthest ring searched. */
    static final int MAX_RING = 9;
    /** Cells kept between two hardpoints. */
    static final int SPACING = 4;

    private LiftHardpoints() { }

    /** Spawns up to {@code count} Marine Vulcans round the lift; returns their cells. */
    public static List<int[]> standUp(BattleSimulation sim, CivilianEvacuationPlacement placement, int count) {
        List<int[]> placed = new ArrayList<>();
        if (count <= 0) return placed;
        NavigationGrid grid = sim.getGrid();
        for (int ring = MIN_RING; ring <= MAX_RING && placed.size() < count; ring++) {
            for (int dy = -ring; dy <= ring && placed.size() < count; dy++) {
                for (int dx = -ring; dx <= ring && placed.size() < count; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) continue;
                    int x = placement.liftX + dx, y = placement.liftY + dy;
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || crowded(placed, x, y)) continue;
                    sim.spawn(MapTurret.create("Lift hardpoint " + (placed.size() + 1), Faction.MARINE,
                            TurretCatalogRegistry.VULCAN_STRUCTURE_ID, x, y));
                    placed.add(new int[]{x, y});
                }
            }
        }
        return placed;
    }

    private static boolean crowded(List<int[]> placed, int x, int y) {
        for (int[] cell : placed) {
            if (Math.max(Math.abs(cell[0] - x), Math.abs(cell[1] - y)) < SPACING) return true;
        }
        return false;
    }
}
