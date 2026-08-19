package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

/** Deterministic in-battle comparison spawn for the first three mech profiles. */
public final class MechFamilyDebugSpawner {

    private static final MechVariant[] FAMILY = {
            MechVariant.BULWARK, MechVariant.HOUND, MechVariant.SIROCCO
    };

    private MechFamilyDebugSpawner() {}

    public static long[] spawn(BattleSimulation sim) {
        if (sim == null) return new long[0];
        NavigationGrid grid = sim.getGrid();
        int[][] cells = findCells(grid, sim.getOccupancyMap(), FAMILY.length);
        if (cells.length < FAMILY.length) return new long[0];

        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.HEAVY_MECH);
        Squad squad = sim.getSquad(squadId);
        long[] spawned = new long[FAMILY.length];
        for (int i = 0; i < FAMILY.length; i++) {
            MechVariant variant = FAMILY[i];
            EntitySpec spec = new EntitySpec("debug-mech-" + variant.id,
                    Faction.DEFENDER, UnitType.HEAVY_MECH, cells[i][0], cells[i][1])
                    .mechVariant(variant)
                    .role(UnitRole.PATROL)
                    .squad(squadId);
            long id = sim.spawn(spec);
            sim.world().attachMechLoadout(id, variant.createLoadout(variant.defaultRole));
            spawned[i] = id;
            if (squad != null && squad.leaderId == 0L) squad.leaderId = id;
        }
        if (squad != null) squad.originalSize = FAMILY.length;
        return spawned;
    }

    private static int[][] findCells(NavigationGrid grid, byte[] occupancy, int count) {
        int[][] result = new int[count][2];
        int found = 0;
        int centerX = grid.getWidth() / 2;
        int centerY = grid.getHeight() / 2;
        int maxRadius = grid.getWidth() + grid.getHeight();
        for (int radius = 0; radius <= maxRadius && found < count; radius++) {
            for (int y = 0; y < grid.getHeight() && found < count; y++) {
                for (int x = 0; x < grid.getWidth() && found < count; x++) {
                    if (Math.abs(x - centerX) + Math.abs(y - centerY) != radius) continue;
                    int index = y * grid.getWidth() + x;
                    if (!grid.isWalkable(x, y) || (occupancy[index] & 0xFF) > 0) continue;
                    result[found][0] = x;
                    result[found][1] = y;
                    found++;
                }
            }
        }
        if (found == count) return result;
        int[][] partial = new int[found][2];
        System.arraycopy(result, 0, partial, 0, found);
        return partial;
    }
}
