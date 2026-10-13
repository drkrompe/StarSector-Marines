package com.dillon.starsectormarines.battle.shield;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.StructureFootprint;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

/**
 * Stands a shield generator on a map, on ground a mission points at.
 *
 * <p>Whether a battle carries a generator is the mission's decision, not this
 * class's: a mission states a planetary shield (through its target profile or the
 * DEBUG defences dial) and its recipe calls {@link #nearKeep}. Nothing here counts
 * or randomises, and nothing here consumes the battle's random stream, so a map
 * that does not ask for a generator is untouched.
 */
public final class ShieldGeneratorPlacement {

    /** Nearest ring to try, in cells from the keep's centre; leaves the keep's own walls clear. */
    private static final int MIN_RING = 5;
    /** Farthest ring to try before giving up. */
    private static final int MAX_RING = 40;

    private ShieldGeneratorPlacement() {}

    /**
     * Places a generator held by {@code holder} on open outdoor ground as near
     * the map's keep ({@code COMMAND_POST}) as it will fit, and returns its entity
     * id, or {@code 0} when the map has no keep or no ground wide enough.
     */
    public static long nearKeep(BattleSimulation sim, Faction holder) {
        TacticalNode keep = keepOf(sim.getCompoundService());
        if (keep == null) return 0L;
        return nearest(sim, holder, keep.centerX(), keep.centerY());
    }

    /**
     * Places a generator held by {@code holder} on the open outdoor ground
     * nearest {@code (aimX, aimY)} that holds its whole footprint, and returns its
     * entity id, or {@code 0} when there is none within reach.
     */
    public static long nearest(BattleSimulation sim, Faction holder, int aimX, int aimY) {
        StructureDef def = TurretCatalogRegistry.requireStructure(ShieldGenerator.STRUCTURE_ID);
        CellTopology topology = sim.getTopology();
        for (int ring = MIN_RING; ring <= MAX_RING; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) continue;
                    int x = aimX + dx;
                    int y = aimY + dy;
                    if (!StructureFootprint.canPlace(sim.getGrid(), def, x, y)) continue;
                    if (!isOutdoors(topology, def, x, y)) continue;
                    return sim.spawn(ShieldGenerator.create("shield-generator", holder, x, y));
                }
            }
        }
        return 0L;
    }

    private static TacticalNode keepOf(CompoundService compounds) {
        if (compounds == null) return null;
        for (CompoundService.Record record : compounds.getRecords()) {
            if (record.node.kind == TacticalNode.Kind.COMMAND_POST) return record.node;
        }
        return null;
    }

    private static boolean isOutdoors(CellTopology topology, StructureDef def, int anchorX, int anchorY) {
        int x0 = StructureFootprint.minX(def, anchorX);
        int y0 = StructureFootprint.minY(def, anchorY);
        for (int dy = 0; dy < def.footprintCellsY; dy++) {
            for (int dx = 0; dx < def.footprintCellsX; dx++) {
                if (topology.getBuildingId(x0 + dx, y0 + dy) != 0) return false;
            }
        }
        return true;
    }
}
