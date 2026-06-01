package com.dillon.starsectormarines.battle.decision.goap.world;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link GarrisonArea}'s size + containment gate — the shared home
 * of story 17's AABB filter. Uses the same 3-zone corridor as
 * {@code SecureCompoundGoalTest}: a small start room, a large outdoor flood,
 * and a small compound room. Against a footprint covering only the compound
 * room, the gate must pick that room and reject both the outdoor flood (size
 * gate) and the off-footprint start room (containment gate).
 */
public class GarrisonAreaTest {

    private static final int W = 30;
    private static final int H = 10;
    private static final int WALL_A = 4;
    private static final int WALL_B = 24;

    private static BattleSimulation threeZoneSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x == WALL_A || x == WALL_B) continue;
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(WALL_A, 5);
        grid.setDoorway(WALL_A, 5, true);
        grid.setWalkableFloor(WALL_B, 5);
        grid.setDoorway(WALL_B, 5, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Compound node whose footprint covers only the far room (x 25..29). */
    private static TacticalNode compoundNode() {
        return new TacticalNode(TacticalNode.Kind.ARMORY, 27, 5,
                25, 0, 29, 9, Faction.DEFENDER, 80, 4);
    }

    @Test
    public void isGarrisonZoneAcceptsInBoxRoomRejectsOutdoorAndOffBox() {
        BattleSimulation sim = threeZoneSim();
        NavigationGrid grid = sim.getGrid();
        var graph = sim.getZoneGraph();
        NavigationZone compound = graph.zoneById(graph.zoneIdAt(27, 5));
        NavigationZone outdoor = graph.zoneById(graph.zoneIdAt(12, 5));
        NavigationZone startRoom = graph.zoneById(graph.zoneIdAt(2, 5));

        assertTrue(GarrisonArea.isGarrisonZone(compound, 25, 0, 29, 9, grid),
                "the small in-box compound room is a garrison zone");
        assertFalse(GarrisonArea.isGarrisonZone(outdoor, 25, 0, 29, 9, grid),
                "the outdoor flood fails the size gate");
        assertFalse(GarrisonArea.isGarrisonZone(startRoom, 25, 0, 29, 9, grid),
                "a small room outside the footprint fails the containment gate");
    }

    @Test
    public void garrisonZonesReturnsOnlyTheCompoundRoom() {
        BattleSimulation sim = threeZoneSim();
        TacticalNode node = compoundNode();
        int compoundZone = sim.getZoneGraph().zoneIdAt(27, 5);
        int outdoorZone = sim.getZoneGraph().zoneIdAt(12, 5);
        int startZone = sim.getZoneGraph().zoneIdAt(2, 5);

        List<Integer> zones = GarrisonArea.garrisonZones(node, 0, sim);

        assertTrue(zones.contains(compoundZone), "the compound room is in the garrison area");
        assertFalse(zones.contains(outdoorZone), "the outdoor flood is excluded");
        assertFalse(zones.contains(startZone), "the off-footprint start room is excluded");
    }
}
