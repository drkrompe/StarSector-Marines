package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fit.ConsoleBankLayout;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortRoom;
import com.dillon.starsectormarines.battle.world.gen.fortress.ShieldFort;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rooms as the stage stands them: every room of every suite is fitted whole (the guard that would throw a fill away
 * did not), the garrison's seats are walkable floor inside their own room, and a room with a crew gets exactly one place
 * node for it.
 */
class FortRoomStampTest {

    private static final int W = 240;
    private static final int H = 160;

    private static GenContext field() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L), W, H, 7L);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(grid, topology, null));
        ctx.put(BspKeys.BUILDINGS, BuildingFloodFill.populate(topology, 7L));
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL.withDeclaredDefences(
                new GroundDefences(0, false).withForts(FortKind.BATTERY_FORT, 1)));
        return ctx;
    }

    private static int expectedFixtures(FortRoom room) {
        int count = 0;
        for (LayoutOp op : room.ops()) {
            if (op instanceof LayoutOp.Fixture) count++;
            if (op instanceof LayoutOp.Assembly a) count += 1 + a.layers().size();
            if (op instanceof LayoutOp.ConsoleBank bank) count += bank.cells().size();
        }
        return count;
    }

    private static int doodadsOnFloor(GenContext ctx, int[] a, int[] b) {
        int minX = Math.min(a[0], b[0]), maxX = Math.max(a[0], b[0]), minY = Math.min(a[1], b[1]), maxY = Math.max(a[1], b[1]);
        int n = 0;
        for (Doodad d : ctx.doodads) {
            if (d.cellX >= minX && d.cellX <= maxX && d.cellY >= minY && d.cellY <= maxY) n++;
        }
        return n;
    }

    private static int doodadsInRoom(GenContext ctx, CompoundLayout.Placement at, FortRoom room) {
        return doodadsOnFloor(ctx, at.cell(room.x(), room.y()), at.cell(room.x() + room.width() - 1, room.y() + room.height() - 1));
    }

    @Test
    void everyRoomIsFittedWholeAtEveryTurnWithTheAntiAirRoomToo() {
        for (FortPlan plan : List.of(BatteryFort.PLAN, ShieldFort.PLAN)) {
            for (int turn = 0; turn < 4; turn++) {
                GenContext ctx = field();
                CompoundLayout.Placement at = plan.layout().at(60, 40, turn);
                FortStage.stamp(ctx, plan, at, 0, true);
                List<FortRoom> rooms = new ArrayList<>(plan.buildings());
                rooms.add(plan.airDefence().room());
                for (FortRoom room : rooms) {
                    assertEquals(expectedFixtures(room), doodadsInRoom(ctx, at, room),
                            plan.kind() + " " + room.aspect() + " " + room.purpose() + " is furnished whole at turn " + turn);
                }
            }
        }
    }

    @Test
    void theGarrisonSeatsAreWalkableFloorInsideTheirOwnRoomAndEachCrewedRoomHasOneNode() {
        for (FortPlan plan : List.of(BatteryFort.PLAN, ShieldFort.PLAN)) {
            for (int turn = 0; turn < 4; turn++) {
                GenContext ctx = field();
                CompoundLayout.Placement at = plan.layout().at(60, 40, turn);
                FortStage.stamp(ctx, plan, at, 0, true);
                List<FortRoom> rooms = new ArrayList<>(plan.buildings());
                rooms.add(plan.airDefence().room());
                int crewed = 0;
                for (FortRoom room : rooms) {
                    if (room.stands().isEmpty()) continue;
                    crewed++;
                    int[] first = at.cell(room.standCells().get(0)[0], room.standCells().get(0)[1]);
                    TacticalNode node = ctx.tactical.stream()
                            .filter(n -> n.kind == TacticalNode.Kind.INNER_POSITION && n.anchorX == first[0] && n.anchorY == first[1])
                            .findFirst().orElse(null);
                    assertNotNull(node, plan.kind() + " " + room.purpose() + " has its crew's node at turn " + turn);
                    assertEquals(Faction.DEFENDER, node.defaultGuard);
                    assertEquals(room.stands().size(), node.standPositions().size());
                    for (TacticalNode.StandPosition p : node.standPositions()) {
                        assertTrue(ctx.grid.isWalkable(p.x(), p.y()), "a seat is floor a body can stand on");
                        assertFalse(ctx.grid.isDoorway(p.x(), p.y()));
                    }
                }
                assertTrue(crewed > 0);
                assertEquals(crewed, ctx.tactical.stream().filter(n -> n.kind == TacticalNode.Kind.INNER_POSITION).count());
            }
        }
    }

    @Test
    void aBankOfConsolesBlocksItsCells() {
        GenContext ctx = field();
        CompoundLayout.Placement at = BatteryFort.PLAN.layout().at(60, 40, 0);
        FortStage.stamp(ctx, BatteryFort.PLAN, at, 0);
        FortRoom hall = BatteryFort.PLAN.rooms().stream()
                .filter(r -> r.aspect() == FortAspect.FIRE_CONTROL && !r.main()).findFirst().orElseThrow();
        int banks = 0;
        for (LayoutOp op : hall.ops()) {
            if (!(op instanceof LayoutOp.ConsoleBank bank)) continue;
            banks++;
            for (ConsoleBankLayout.Cell c : bank.cells()) {
                int[] cell = at.cell(hall.x() + c.x(), hall.y() + c.y());
                assertFalse(ctx.grid.isWalkable(cell[0], cell[1]), "a console module is not walkable");
            }
        }
        assertTrue(banks >= 2, "the operations hall is a room of stations");
    }
}
