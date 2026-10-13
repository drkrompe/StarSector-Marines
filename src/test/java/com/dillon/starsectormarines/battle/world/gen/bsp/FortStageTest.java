package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.StructureFootprint;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortBelt;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The battery fort layout and the stage that stands it. Run on a bare field, not a generated map: the questions are
 * about the layout at each turn and about the stage's rule, and a generator would only add inputs.
 */
class FortStageTest {

    private static final int W = 240;
    private static final int H = 160;

    private static GenContext field(TargetProfile profile) {
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
        if (profile != null) ctx.put(BspKeys.MARKET_PROFILE, profile);
        return ctx;
    }

    private static TargetProfile declared(int forts) {
        return TargetProfile.NEUTRAL.withDeclaredDefences(
                new GroundDefences(0, false).withForts(FortKind.BATTERY_FORT, forts));
    }

    @Test
    void nothingIsPlacedUnlessTheMissionDeclaresForts() {
        GenContext none = field(null);
        new FortStage().run(none);
        assertTrue(none.defensePosts.isEmpty());

        GenContext batteriesOnly = field(TargetProfile.NEUTRAL.withDeclaredDefences(new GroundDefences(3, true, 5)));
        new FortStage().run(batteriesOnly);
        assertTrue(batteriesOnly.defensePosts.isEmpty(), "a bare battery is not a fort");
    }

    @Test
    void theFortIsWholeAtEveryTurn() {
        for (int turn = 0; turn < 4; turn++) {
            GenContext ctx = field(declared(1));
            var at = BatteryFort.layout().at(60, 40, turn);
            FortStage.stamp(ctx, BatteryFort.PLAN, at, 0);

            // Posts: three aspect cores, the platform, a nest in each bastion and a nest on the belt, all tagged to fort 0.
            assertEquals(4 + BatteryFort.NESTS.size() + FortBelt.nests().size(), ctx.defensePosts.size(), "turn " + turn);
            for (DefensePost post : ctx.defensePosts) assertNotNull(post.fort);
            long cores = ctx.defensePosts.stream().filter(p -> p.fort.aspect() != null).count();
            assertEquals(3, cores);

            // Each core stands wholly on the floor of its own building and the room it stands in is the right one.
            for (FortAspect aspect : BatteryFort.PLAN.aspects()) {
                int[] c = at.cell(BatteryFort.coreCentre(aspect)[0], BatteryFort.coreCentre(aspect)[1]);
                RoomPurpose want = switch (aspect) {
                    case FIRE_CONTROL -> RoomPurpose.CONTROL_ROOM;
                    case MAGAZINE -> RoomPurpose.ARMORY;
                    case POWER -> RoomPurpose.ENGINE_ROOM;
                    default -> throw new IllegalStateException();
                };
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        assertEquals(want, ctx.topology.getRoomPurpose(c[0] + dx, c[1] + dy),
                                aspect + " core cell at turn " + turn);
                        assertFalse(ctx.grid.isWalkable(c[0] + dx, c[1] + dy), "core footprint is sealed");
                    }
                }
            }

            // Every building stands inside a real wall ring: solid, tagged as wall and breachable, except at its door
            // and its firing windows.
            for (RoomPacker.Placed room : at.buildings()) {
                int solid = 0;
                for (int[] cell : room.shape().wall()) {
                    int x = room.originX() + cell[0];
                    int y = room.originY() + cell[1];
                    if (ctx.grid.isWalkable(x, y)) continue;
                    solid++;
                    assertTrue(ctx.topology.isWall(x, y), room.purpose() + " ring cell is not tagged wall at " + x + "," + y);
                    assertTrue(ctx.grid.getWallHp(x, y) > 0, "ring cell has no hit points");
                }
                assertTrue(solid > room.shape().wall().length / 2, room.purpose() + " ring is mostly open at turn " + turn);
            }

            // The wall: every cell of the trace that is wall is breachable; the gates are open.
            int wallCells = 0;
            for (int lx = 0; lx < BatteryFort.WIDTH; lx++) {
                for (int ly = 0; ly < BatteryFort.HEIGHT; ly++) {
                    int[] cell = at.cell(lx, ly);
                    if (BatteryFort.isWall(lx, ly)) {
                        wallCells++;
                        assertFalse(ctx.grid.isWalkable(cell[0], cell[1]));
                        assertEquals(FortStage.FORT_WALL_HP, ctx.grid.getWallHp(cell[0], cell[1]));
                    }
                }
            }
            assertTrue(wallCells > 300, "a real trace, not a token wall");
            for (int[] gate : BatteryFort.GATE_CELLS) {
                int[] cell = at.cell(gate[0], gate[1]);
                assertTrue(ctx.grid.isWalkable(cell[0], cell[1]), "gate cell " + gate[0] + "," + gate[1]);
            }
        }
    }

    @Test
    void fromOutsideTheGatesReachEveryBuildingAndThePlatformAtEveryTurn() {
        for (int turn = 0; turn < 4; turn++) {
            GenContext ctx = field(declared(1));
            var at = BatteryFort.layout().at(60, 40, turn);
            FortStage.stamp(ctx, BatteryFort.PLAN, at, 0);
            boolean[][] reached = flood(ctx.grid, 5, 5);

            for (RoomPacker.Placed room : at.buildings()) {
                for (int[] cell : room.shape().filled()) {
                    int x = room.originX() + cell[0];
                    int y = room.originY() + cell[1];
                    if (!ctx.grid.isWalkable(x, y)) continue; // the core stands here
                    assertTrue(reached[x][y], room.purpose() + " floor " + x + "," + y + " sealed off at turn " + turn);
                }
                assertFalse(room.doors().isEmpty());
                for (var door : room.doors()) assertTrue(reached[door.x()][door.y()], "door reachable");
            }
            // Every walkable cell of the footprint, works, pit and belt alike, is open to the outside.
            for (int lx = 0; lx < BatteryFort.WIDTH; lx++) {
                for (int ly = 0; ly < BatteryFort.HEIGHT; ly++) {
                    int[] cell = at.cell(lx, ly);
                    if (ctx.grid.isWalkable(cell[0], cell[1])) {
                        assertTrue(reached[cell[0]][cell[1]], "cell " + lx + "," + ly + " enclosed at turn " + turn);
                    }
                }
            }
        }
    }

    @Test
    void closingTheGatesSealsTheFort() {
        GenContext ctx = field(declared(1));
        var at = BatteryFort.layout().at(60, 40, 0);
        FortStage.stamp(ctx, BatteryFort.PLAN, at, 0);
        for (int[] gate : BatteryFort.GATE_CELLS) {
            int[] cell = at.cell(gate[0], gate[1]);
            ctx.grid.setWalkable(cell[0], cell[1], false);
        }
        boolean[][] reached = flood(ctx.grid, 5, 5);
        int[] inside = at.cell(BatteryFort.OX + 23, BatteryFort.OY + 17);
        assertFalse(reached[inside[0]][inside[1]], "with the gates shut the wall holds the fort");
    }

    @Test
    void theStageStandsTheDeclaredForts() {
        GenContext ctx = field(declared(2));
        FortStage stage = new FortStage();
        stage.run(ctx);
        assertEquals(2, stage.placed().size());
        int core = 4 + BatteryFort.NESTS.size();
        int most = core + FortBelt.nests().size();
        // The belt follows the ground it is given, so a neighbour or the map edge may take a belt nest but never a core post.
        assertTrue(ctx.defensePosts.size() >= 2 * core && ctx.defensePosts.size() <= 2 * most,
                "posts " + ctx.defensePosts.size());
        StructureDef battery = TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.FORTRESS_BATTERY_STRUCTURE_ID);
        long platforms = ctx.defensePosts.stream()
                .filter(p -> p.turrets.get(0).structureId.equals(battery.id)).count();
        assertEquals(2, platforms);
        for (DefensePost post : ctx.defensePosts) {
            StructureDef def = post.turrets.get(0).structure();
            for (int dy = 0; dy < def.footprintCellsY; dy++) {
                for (int dx = 0; dx < def.footprintCellsX; dx++) {
                    assertFalse(ctx.grid.isWalkable(StructureFootprint.minX(def, post.anchorX) + dx,
                            StructureFootprint.minY(def, post.anchorY) + dy));
                }
            }
        }
        // The roofs and fog read the building list, which now knows the fort's buildings.
        assertNotNull(ctx.get(BspKeys.BUILDINGS));
    }

    @Test
    void theMainGateFacesTheMapCentre() {
        GenContext ctx = field(declared(1));
        // Keep the middle of the field for a road so the fort stands to one side of it.
        boolean[][] road = new boolean[W][H];
        for (int x = 100; x < 140; x++) for (int y = 0; y < H; y++) road[x][y] = true;
        ctx.put(BspKeys.ROAD_RESERVATION, road);
        FortStage stage = new FortStage();
        stage.run(ctx);
        var at = stage.placed().get(0).placement();
        int[] centre = at.cell(BatteryFort.WIDTH / 2, BatteryFort.HEIGHT / 2);
        int[] inside = at.cell(BatteryFort.OX + 2, BatteryFort.OY + 17);
        int[] outside = at.cell(BatteryFort.OX - 3, BatteryFort.OY + 17);
        double toCentreX = W / 2.0 - centre[0];
        double toCentreY = H / 2.0 - centre[1];
        double dot = (outside[0] - inside[0]) * toCentreX + (outside[1] - inside[1]) * toCentreY;
        // A turn that would face the gate at the centre may not fit beside the road; one that faces away never wins.
        assertTrue(dot >= 0, "the main gate's outward direction does not point away from the centre of the map");
    }

    @Test
    void placementIsDeterministic() {
        FortStage a = new FortStage();
        FortStage b = new FortStage();
        a.run(field(declared(2)));
        b.run(field(declared(2)));
        assertEquals(a.placed().size(), b.placed().size());
        for (int i = 0; i < a.placed().size(); i++) {
            assertEquals(a.placed().get(i).placement(), b.placed().get(i).placement());
        }
    }

    @Test
    void fewerAreStoodWhenTheGroundCannotHoldThem() {
        GenContext ctx = field(declared(40));
        FortStage stage = new FortStage();
        stage.run(ctx);
        assertTrue(stage.placed().size() < 40 && !stage.placed().isEmpty());
    }

    @Test
    void roadAndBuildingGroundIsPriorToTheFort() {
        GenContext ctx = field(declared(1));
        boolean[][] road = new boolean[W][H];
        // A wide road across the whole middle of the field: no fort may stand across it.
        for (int x = 0; x < W; x++) for (int y = 70; y < 90; y++) road[x][y] = true;
        ctx.put(BspKeys.ROAD_RESERVATION, road);
        FortStage stage = new FortStage();
        stage.run(ctx);
        assertEquals(1, stage.placed().size());
        var at = stage.placed().get(0).placement();
        for (int[] local : BatteryFort.clearCells()) {
            int[] cell = at.cell(local[0], local[1]);
            assertFalse(road[cell[0]][cell[1]], "works or glacis cell on the road");
        }
        assertNull(ctx.get(BspKeys.PRECINCT_CLAIM));
    }

    private static boolean[][] flood(NavigationGrid grid, int sx, int sy) {
        boolean[][] seen = new boolean[grid.getWidth()][grid.getHeight()];
        ArrayDeque<int[]> open = new ArrayDeque<>();
        open.add(new int[]{sx, sy});
        seen[sx][sy] = true;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty()) {
            int[] at = open.poll();
            for (int[] step : steps) {
                int nx = at[0] + step[0];
                int ny = at[1] + step[1];
                if (!grid.inBounds(nx, ny) || seen[nx][ny] || !grid.isWalkable(nx, ny)) continue;
                seen[nx][ny] = true;
                open.add(new int[]{nx, ny});
            }
        }
        return seen;
    }
}
