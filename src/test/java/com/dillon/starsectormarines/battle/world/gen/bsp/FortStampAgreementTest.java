package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortBuildingArt;
import com.dillon.starsectormarines.battle.fort.FortFrame;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
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

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stamp, the frame and the art placement describe one fort: a room's authored fixtures stand on the cells the frame
 * reports for them, its doors are cut where the frame says, and the cells art is registered to are the stamped floors.
 * A stamp mirrored against its frame fails here at the turn it is mirrored at.
 */
class FortStampAgreementTest {

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
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL);
        return ctx;
    }

    private static Doodad doodadAt(GenContext ctx, int x, int y, String id) {
        for (Doodad d : ctx.doodads) {
            if (d.cellX == x && d.cellY == y && id.equals(d.definitionId())) return d;
        }
        return null;
    }

    @Test
    void everyAuthoredFixtureStandsWhereTheFrameSaysAtEveryTurn() {
        for (FortPlan plan : List.of(BatteryFort.PLAN, ShieldFort.PLAN)) {
            for (int turn = 0; turn < 4; turn++) {
                GenContext ctx = field();
                CompoundLayout.Placement at = plan.layout().at(60, 40, turn);
                FortStage.stamp(ctx, plan, at, 0);
                FortFrame frame = new FortFrame(plan, 60, 40, turn);
                for (FortRoom room : plan.rooms()) {
                    for (LayoutOp op : room.ops()) {
                        if (!(op instanceof LayoutOp.Fixture f)) continue;
                        int[] cell = frame.cell(room.x() + f.x(), room.y() + f.y());
                        assertTrue(doodadAt(ctx, cell[0], cell[1], f.doodadId()) != null,
                                plan.kind() + " " + room.purpose() + " fixture " + f.doodadId() + " at local ("
                                        + f.x() + "," + f.y() + ") stands on the frame's cell at turn " + turn);
                    }
                }
            }
        }
    }

    @Test
    void everyDoorIsCutWhereTheFrameSaysAndEveryFloorCellIsOpenAtEveryTurn() {
        for (FortPlan plan : List.of(BatteryFort.PLAN, ShieldFort.PLAN)) {
            for (int turn = 0; turn < 4; turn++) {
                GenContext ctx = field();
                FortStage.stamp(ctx, plan, plan.layout().at(60, 40, turn), 0);
                FortFrame frame = new FortFrame(plan, 60, 40, turn);
                for (FortRoom room : plan.rooms()) {
                    for (int[] door : room.doorCells()) {
                        int[] cell = frame.cell(door[0], door[1]);
                        assertTrue(ctx.grid.isWalkable(cell[0], cell[1]) || ctx.grid.isDoorway(cell[0], cell[1]),
                                plan.kind() + " " + room.purpose() + " door open at turn " + turn);
                    }
                    int[] b = frame.bounds(room.x(), room.y(), room.width(), room.height());
                    // The ring one cell outside the floor is wall except where a door is cut.
                    int walled = 0;
                    for (int x = b[0] - 1; x <= b[2] + 1; x++) {
                        for (int y = b[1] - 1; y <= b[3] + 1; y++) {
                            boolean ring = x < b[0] || x > b[2] || y < b[1] || y > b[3];
                            if (ring && !ctx.grid.isWalkable(x, y)) walled++;
                        }
                    }
                    assertTrue(walled > 0, plan.kind() + " " + room.purpose() + " has its wall ring at turn " + turn);
                }
            }
        }
    }

    @Test
    void everyTurbineWellOfThePowerArtSitsOverAGeneratorSetAtEveryTurn() {
        for (FortPlan plan : List.of(BatteryFort.PLAN, ShieldFort.PLAN)) {
            StructureDef def = TurretCatalogRegistry.requireStructure(plan.coreStructureId(FortAspect.POWER));
            FortBuildingArt art = def.buildingArt;
            int[] floor = plan.floor(FortAspect.POWER);
            boolean halfTurned = art.needsHalfTurn(plan.door(FortAspect.POWER)[1] < floor[1]);
            for (int turn = 0; turn < 4; turn++) {
                GenContext ctx = field();
                FortStage.stamp(ctx, plan, plan.layout().at(60, 40, turn), 0);
                FortFrame frame = new FortFrame(plan, 60, 40, turn);
                for (FortBuildingArt.Rotor rotor : art.rotors()) {
                    float[] at = FortBuildingArt.registered(halfTurned, floor[2], floor[3], rotor.x(), rotor.y());
                    // A well is centred on the corner shared by a two-by-two block of generator cells.
                    int cornerX = floor[0] + Math.round(at[0]);
                    int cornerY = floor[1] + Math.round(at[1]);
                    float[] drawn = frame.point(floor[0] + at[0], floor[1] + at[1]);
                    float sumX = 0, sumY = 0;
                    for (int dx = -1; dx <= 0; dx++) {
                        for (int dy = -1; dy <= 0; dy++) {
                            int[] cell = frame.cell(cornerX + dx, cornerY + dy);
                            sumX += cell[0] + 0.5f;
                            sumY += cell[1] + 0.5f;
                            assertTrue(doodadAt(ctx, cell[0], cell[1], "doodad.industrial-generator") != null,
                                    plan.kind() + " turbine well at (" + rotor.x() + "," + rotor.y() + ") over a generator set at turn " + turn);
                        }
                    }
                    assertEquals(sumX / 4f, drawn[0], 0.01f, plan.kind() + " turbine drawn over its set (x) at turn " + turn);
                    assertEquals(sumY / 4f, drawn[1], 0.01f, plan.kind() + " turbine drawn over its set (y) at turn " + turn);
                }
            }
        }
    }

    @Test
    void theMainRoomsDoorAndTheArtsRegisteredFloorAgreeAtEveryTurn() {
        for (FortPlan plan : List.of(BatteryFort.PLAN, ShieldFort.PLAN)) {
            for (int turn = 0; turn < 4; turn++) {
                GenContext ctx = field();
                FortStage.stamp(ctx, plan, plan.layout().at(60, 40, turn), 0);
                FortFrame frame = new FortFrame(plan, 60, 40, turn);
                for (FortAspect aspect : plan.aspects()) {
                    int[] floor = plan.floor(aspect);
                    int[] b = frame.bounds(floor[0], floor[1], floor[2], floor[3]);
                    int[] door = frame.cell(plan.door(aspect)[0], plan.door(aspect)[1]);
                    boolean touches = (door[0] >= b[0] - 1 && door[0] <= b[2] + 1 && door[1] >= b[1] - 1 && door[1] <= b[3] + 1);
                    assertTrue(touches, plan.kind() + " " + aspect + " door is on the floor's ring at turn " + turn);
                    assertFalse(ctx.grid.isWalkable(b[0] - 1, b[1] - 1), "the ring corner is wall");
                }
            }
        }
    }
}
