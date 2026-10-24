package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the battle reads a redoubt or base built from what the country had standing: its wall teams' posts
 * ({@link CordonGarrisons#worksPosts}), its berths and work points ({@link CordonConstructions#rehouse}) and the
 * shed's capture beacon taken off the base ({@link CordonConstructions#withoutCompounds}).
 */
class CordonWorksReadingTest {

    private static NavigationGrid open(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    private static DoodadDef def(String id) {
        return new DoodadDef(id, "sheet", 0, 0, DoodadCover.MED, 0.3f, 1, 1, null, 0, null, FightingPositionRole.NONE);
    }

    @Test
    void postsAreLoopholesAndSandbagsTheFrontFirstThenTheYard() {
        NavigationGrid grid = open(20, 20);
        // A window in the north face at (5, 12), a sandbag at (8, 12) and one on the gun's own cell (10, 12).
        assertTrue(grid.tryPlaceEdgeBarrier(5, 12, Direction.N, SharedEdgeBarrier.Kind.WINDOW) != null);
        List<Doodad> props = new ArrayList<>(List.of(
                new Doodad(8, 12, def(CordonGarrisons.SANDBAG + "n")),
                new Doodad(10, 12, def(CordonGarrisons.SANDBAG + "n")),
                new Doodad(6, 8, def("doodad.industrial-crate-stack"))));
        List<int[]> guns = List.of(new int[]{10, 12});
        List<int[]> posts = CordonGarrisons.worksPosts(grid, props, guns, 4, 4, 12, 12, 1, 5);
        assertEquals(5, posts.size());
        // Front (north, high y) row first, nearest the middle first: the sandbag at x 8, then the window at x 5.
        assertArrayEquals(new int[]{8, 12}, posts.get(0));
        assertArrayEquals(new int[]{5, 12}, posts.get(1));
        for (int[] p : posts) assertFalse(p[0] == 10 && p[1] == 12, "a gun's own cell is no post");
        // The rest of the seats fill from the front row back, inside the bounds.
        for (int[] p : posts.subList(2, 5)) assertEquals(12, p[1]);
    }

    @Test
    void aFrontToTheSouthReadsFromTheBottomRow() {
        NavigationGrid grid = open(20, 20);
        List<int[]> posts = CordonGarrisons.worksPosts(grid, List.of(), List.of(), 4, 4, 12, 12, -1, 3);
        for (int[] p : posts) assertEquals(4, p[1]);
    }

    @Test
    void theBaseKeepsOtherBerthsAndRepointsTheirWork() {
        int[] fence = {50, 50, 90, 90};
        Gantry outside = new Gantry(10, 10, 2, 3, Gantry.Facing.NORTH);
        Gantry shed = new Gantry(70, 70, 2, 3, Gantry.Facing.NORTH);
        Gantry far = new Gantry(120, 10, 2, 3, Gantry.Facing.NORTH);
        List<FixtureTask> tasks = List.of(
                FixtureTask.servingBerth(13, 10, 0, 12, 10),
                FixtureTask.servingBerth(73, 70, 1, 72, 70),
                FixtureTask.servingBerth(123, 10, 2, 122, 10),
                FixtureTask.at(60, 60, Affordance.SERVICE, 61, 60),
                FixtureTask.at(5, 5, Affordance.SERVICE, 6, 5));
        CordonBuilders.Built built = CordonBuilders.Built.at(0f, 0f);
        Gantry gantry = Gantry.covering(60, 60, 5, 7, Gantry.Facing.NORTH, Gantry.Holds.MACHINE);
        built.berths().add(new CordonBuilders.Berth(gantry, List.of(new int[]{59, 63, 60, 63}), List.of()));
        built.bayCells().add(new int[]{59, 63});
        built.bayCells().add(new int[]{60, 63});
        CellTopology topology = new CellTopology(200, 200);
        List<Gantry> outGantries = new ArrayList<>();
        List<FixtureTask> outTasks = new ArrayList<>();
        CordonConstructions.rehouse(List.of(outside, shed, far), tasks, fence, built, topology, outGantries, outTasks);
        assertEquals(List.of(outside, far, gantry), outGantries);
        // The far berth's work follows it to its new index; the shed's and the work inside the fence are gone.
        assertEquals(4, outTasks.size());
        assertEquals(0, outTasks.get(0).berth());
        assertEquals(1, outTasks.get(1).berth());
        assertEquals(FixtureTask.NO_BERTH, outTasks.get(2).berth());
        assertEquals(2, outTasks.get(3).berth());
        assertEquals(Affordance.SERVICE, outTasks.get(3).affordance());
        assertEquals(RoomPurpose.VEHICLE_BAY, topology.getRoomPurpose(60, 63));
        assertEquals(RoomPurpose.VEHICLE_BAY, topology.getRoomPurpose(59, 63));
    }

    @Test
    void theBaseLosesItsCaptureBeaconAndNothingElse() {
        TacticalNode armoury = new TacticalNode(TacticalNode.Kind.ARMORY, 70, 70, 65, 65, 75, 75, Faction.DEFENDER, 70, 3, false);
        TacticalNode elsewhere = new TacticalNode(TacticalNode.Kind.ARMORY, 10, 10, 5, 5, 15, 15, Faction.DEFENDER, 70, 3, false);
        TacticalNode gate = new TacticalNode(TacticalNode.Kind.GATE, 60, 60, 60, 60, 61, 61, Faction.DEFENDER, 40, 3, false);
        TacticalMap map = CordonConstructions.withoutCompounds(new TacticalMap(List.of(armoury, elsewhere, gate)),
                new int[]{50, 50, 90, 90});
        assertEquals(List.of(elsewhere, gate), map.all());
    }
}
