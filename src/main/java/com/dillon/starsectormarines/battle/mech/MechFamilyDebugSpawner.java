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
        MechSpawnPlacement.Point[] points = new MechSpawnPlacement.Point[FAMILY.length];
        for (int i = 0; i < FAMILY.length; i++) {
            points[i] = findPosition(sim, FAMILY[i], points, i);
            if (points[i] == null) return new long[0];
        }

        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.HEAVY_MECH);
        Squad squad = sim.getSquad(squadId);
        long[] spawned = new long[FAMILY.length];
        for (int i = 0; i < FAMILY.length; i++) {
            MechVariant variant = FAMILY[i];
            EntitySpec spec = new EntitySpec("debug-mech-" + variant.id,
                    Faction.DEFENDER, UnitType.HEAVY_MECH, points[i].cellX(), points[i].cellY())
                    .atPosition(points[i].x(), points[i].y())
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

    private static MechSpawnPlacement.Point findPosition(BattleSimulation sim, MechVariant variant,
                                                          MechSpawnPlacement.Point[] reserved, int count) {
        NavigationGrid grid = sim.getGrid();
        int centerX = grid.getWidth() / 2;
        int centerY = grid.getHeight() / 2;
        for (int distance = 0; distance <= grid.getWidth() + grid.getHeight(); distance++) {
            for (int y = 0; y < grid.getHeight(); y++) {
                for (int x = 0; x < grid.getWidth(); x++) {
                    if (Math.abs(x - centerX) + Math.abs(y - centerY) != distance) continue;
                    MechSpawnPlacement.Point point = MechSpawnPlacement.nearCell(grid, variant.radius, x, y,
                            (px, py) -> true, (px, py) -> {
                                if (!MechSpawnPlacement.unoccupied(sim.getRoster(), px, py, variant.radius, 0L)) return false;
                                for (int i = 0; i < count; i++) {
                                    float dx = px - reserved[i].x();
                                    float dy = py - reserved[i].y();
                                    float separation = variant.radius + FAMILY[i].radius;
                                    if (dx * dx + dy * dy < separation * separation) return false;
                                }
                                return true;
                            });
                    if (point != null) return point;
                }
            }
        }
        return null;
    }
}
