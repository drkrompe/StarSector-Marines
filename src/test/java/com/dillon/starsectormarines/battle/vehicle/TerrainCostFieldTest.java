package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice-0 acceptance for {@link TerrainCostField}: roads are the cheap baseline,
 * open ground is dearer, rubble dearer still, and interiors/water are strongly
 * avoided — the ordering the cost-field router relies on to prefer roads while
 * still crossing open ground for a big enough shortcut.
 */
public class TerrainCostFieldTest {

    @Test
    public void groundKindOrderingIsRoadCheapestInteriorDearest() {
        // Road is the 1.0 baseline (nothing cheaper → octile heuristic admissible).
        assertEquals(1.0f, TerrainCostField.costFor(GroundKind.STREET), 1e-6f);
        assertEquals(TerrainCostField.costFor(GroundKind.STREET),
                TerrainCostField.costFor(GroundKind.SIDEWALK), 1e-6f);

        float road = TerrainCostField.costFor(GroundKind.STREET);
        float hardscape = TerrainCostField.costFor(GroundKind.COURTYARD);
        float open = TerrainCostField.costFor(GroundKind.GRASS);
        float rubble = TerrainCostField.costFor(GroundKind.RUBBLE);
        float interior = TerrainCostField.costFor(GroundKind.INDOOR);

        assertTrue(road < hardscape, "road should be cheaper than hardscape");
        assertTrue(hardscape < open, "hardscape should be cheaper than open ground");
        assertTrue(open < rubble, "open ground should be cheaper than rubble");
        assertTrue(rubble <= interior, "rubble should be no dearer than building interior");
        assertTrue(road >= 1.0f, "road baseline must be >= 1.0 to keep the heuristic admissible");

        // Every kind is finite — the cost field encodes preference, not blocking.
        for (GroundKind k : GroundKind.values()) {
            assertTrue(Float.isFinite(TerrainCostField.costFor(k)) && TerrainCostField.costFor(k) > 0f,
                    k + " cost must be finite and positive");
        }
    }

    @Test
    public void bakedFieldReadsPerCellGroundKind() {
        CellTopology topo = new CellTopology(8, 4);
        topo.setGroundKind(2, 1, GroundKind.STREET);
        topo.setGroundKind(3, 1, GroundKind.GRASS);
        topo.setGroundKind(4, 1, GroundKind.RUBBLE);
        // (0,0) left at the implicit INDOOR default.

        TerrainCostField field = TerrainCostField.from(topo);

        assertEquals(8, field.getWidth());
        assertEquals(4, field.getHeight());
        assertEquals(TerrainCostField.costFor(GroundKind.STREET), field.costAt(2, 1), 1e-6f);
        assertEquals(TerrainCostField.costFor(GroundKind.GRASS), field.costAt(3, 1), 1e-6f);
        assertEquals(TerrainCostField.costFor(GroundKind.RUBBLE), field.costAt(4, 1), 1e-6f);
        assertEquals(TerrainCostField.costFor(GroundKind.INDOOR), field.costAt(0, 0), 1e-6f);
        // Out of bounds reads as avoid, never crashes.
        assertEquals(TerrainCostField.COST_AVOID, field.costAt(-1, 0), 1e-6f);
        assertEquals(TerrainCostField.COST_AVOID, field.costAt(0, 99), 1e-6f);
    }
}
