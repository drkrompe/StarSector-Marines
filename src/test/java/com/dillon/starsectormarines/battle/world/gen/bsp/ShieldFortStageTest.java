package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.shield.ShieldGenerator;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortBelt;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.ShieldFort;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shield fort layout and the stage that stands it, on a bare field: layout validity at every turn, that every gate
 * leads in, that nothing is sealed off, and that the keep and the belt are what make it a different fort from the
 * battery fort.
 */
class ShieldFortStageTest {

    private static final int W = 240;
    private static final int H = 160;
    private static final FortPlan PLAN = ShieldFort.PLAN;

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

    private static TargetProfile declared(int shield, int battery) {
        return TargetProfile.NEUTRAL.withDeclaredDefences(new GroundDefences(0, false)
                .withForts(FortKind.SHIELD_FORT, shield).withForts(FortKind.BATTERY_FORT, battery));
    }

    @Test
    void aShieldGeneratorWithoutAFortDeclarationPlacesNoFort() {
        GenContext ctx = field(TargetProfile.NEUTRAL.withDeclaredDefences(new GroundDefences(0, true, 0)));
        new FortStage().run(ctx);
        assertTrue(ctx.defensePosts.isEmpty(), "a bare generator is placed beside the keep, not by this stage");
    }

    @Test
    void theFortIsWholeAtEveryTurn() {
        for (int turn = 0; turn < 4; turn++) {
            GenContext ctx = field(declared(1, 0));
            var at = PLAN.layout().at(60, 40, turn);
            FortStage.stamp(ctx, PLAN, at, 0);

            // Posts: two aspect cores, the generator, a nest in each tower and the belt's nests, all tagged to fort 0.
            assertEquals(3 + ShieldFort.NESTS.size() + FortBelt.nests(PLAN.belt()).size(), ctx.defensePosts.size(),
                    "turn " + turn);
            for (DefensePost post : ctx.defensePosts) assertNotNull(post.fort);
            assertEquals(2, ctx.defensePosts.stream()
                    .filter(p -> p.fort.aspect() != null && p.fort.aspect() != FortAspect.EMITTER).count());
            assertEquals(1, ctx.defensePosts.stream().filter(p -> p.fort.aspect() == FortAspect.EMITTER).count());
            assertTrue(ctx.defensePosts.stream().anyMatch(p -> p.turrets.get(0).structureId.equals(ShieldGenerator.STRUCTURE_ID)));

            // Each core stands wholly on the floor of its own building, in the right room.
            for (FortAspect aspect : PLAN.aspects()) {
                int[] c = at.cell(PLAN.coreCentre(aspect)[0], PLAN.coreCentre(aspect)[1]);
                RoomPurpose want = aspect == FortAspect.POWER ? RoomPurpose.ENGINE_ROOM : RoomPurpose.CONTROL_ROOM;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        assertEquals(want, ctx.topology.getRoomPurpose(c[0] + dx, c[1] + dy), aspect + " at turn " + turn);
                        assertFalse(ctx.grid.isWalkable(c[0] + dx, c[1] + dy));
                    }
                }
            }
            // The generator's footprint is sealed and stands in the open yard inside the keep, not in a building.
            int[] g = at.cell(ShieldFort.GENERATOR[0], ShieldFort.GENERATOR[1]);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) assertFalse(ctx.grid.isWalkable(g[0] + dx, g[1] + dy));
            }

            // Every wall cell, the keep's included, is breachable; every gate is open.
            int keepCells = 0;
            for (int lx = 0; lx < ShieldFort.WIDTH; lx++) {
                for (int ly = 0; ly < ShieldFort.HEIGHT; ly++) {
                    int[] cell = at.cell(lx, ly);
                    if (!ShieldFort.isWall(lx, ly)) continue;
                    assertFalse(ctx.grid.isWalkable(cell[0], cell[1]));
                    assertEquals(FortStage.FORT_WALL_HP, ctx.grid.getWallHp(cell[0], cell[1]));
                    if (ShieldFort.isKeep(lx, ly)) keepCells++;
                }
            }
            assertTrue(keepCells > 60, "an inner keep, not a token ring: " + keepCells);
            for (int[] gate : ShieldFort.GATE_CELLS) {
                int[] cell = at.cell(gate[0], gate[1]);
                assertTrue(ctx.grid.isWalkable(cell[0], cell[1]), "gate cell " + gate[0] + "," + gate[1]);
            }
        }
    }

    @Test
    void everyWalkableCellOfTheFortIsReachableFromOutsideAtEveryTurn() {
        for (int turn = 0; turn < 4; turn++) {
            GenContext ctx = field(declared(1, 0));
            var at = PLAN.layout().at(60, 40, turn);
            FortStage.stamp(ctx, PLAN, at, 0);
            boolean[][] reached = flood(ctx.grid, 5, 5);
            for (RoomPacker.Placed room : at.buildings()) {
                for (int[] cell : room.shape().filled()) {
                    int x = room.originX() + cell[0];
                    int y = room.originY() + cell[1];
                    if (ctx.grid.isWalkable(x, y)) assertTrue(reached[x][y], room.purpose() + " sealed at turn " + turn);
                }
                assertFalse(room.doors().isEmpty());
                for (var door : room.doors()) assertTrue(reached[door.x()][door.y()], "door reachable");
            }
            for (int lx = 0; lx < PLAN.width(); lx++) {
                for (int ly = 0; ly < PLAN.height(); ly++) {
                    int[] cell = at.cell(lx, ly);
                    if (ctx.grid.isWalkable(cell[0], cell[1])) {
                        assertTrue(reached[cell[0]][cell[1]], "cell " + lx + "," + ly + " enclosed at turn " + turn);
                    }
                }
            }
            // The cell beside the generator, inside the keep, is open to the outside too.
            int[] beside = at.cell(ShieldFort.GENERATOR[0] + 2, ShieldFort.GENERATOR[1] + 2);
            assertTrue(reached[beside[0]][beside[1]], "the generator can be reached");
        }
    }

    @Test
    void eachGateAloneLeadsInAndAllGatesShutSealTheFort() {
        for (int gate = 0; gate < PLAN.gates().size(); gate++) {
            GenContext ctx = field(declared(1, 0));
            var at = PLAN.layout().at(60, 40, 0);
            FortStage.stamp(ctx, PLAN, at, 0);
            // Close every gate but one: the open one must still reach the generator's side of the keep.
            for (int other = 0; other < PLAN.gates().size(); other++) {
                if (other == gate) continue;
                close(ctx, at, PLAN.gates().get(other));
            }
            boolean[][] reached = flood(ctx.grid, 5, 5);
            int[] beside = at.cell(ShieldFort.GENERATOR[0] + 2, ShieldFort.GENERATOR[1] + 2);
            assertTrue(reached[beside[0]][beside[1]], "gate " + gate + " alone reaches the generator");
        }
        GenContext ctx = field(declared(1, 0));
        var at = PLAN.layout().at(60, 40, 0);
        FortStage.stamp(ctx, PLAN, at, 0);
        for (FortPlan.Gate gate : PLAN.gates()) close(ctx, at, gate);
        boolean[][] reached = flood(ctx.grid, 5, 5);
        int[] inside = at.cell(ShieldFort.GENERATOR[0] + 2, ShieldFort.GENERATOR[1] + 2);
        assertFalse(reached[inside[0]][inside[1]], "with the gates shut the wall holds the fort");
    }

    /** Shuts a gate by walling the three cells across its mouth. */
    private static void close(GenContext ctx, CompoundLayout.Placement at, FortPlan.Gate gate) {
        int px = gate.outY() != 0 ? 1 : 0;
        int py = gate.outX() != 0 ? 1 : 0;
        for (int l = -1; l <= 1; l++) {
            int[] cell = at.cell(gate.mouthX() - gate.outX() * 2 + px * l, gate.mouthY() - gate.outY() * 2 + py * l);
            ctx.grid.setWalkable(cell[0], cell[1], false);
        }
    }

    @Test
    void theKeepForcesAWayRoundNotAStraightLineFromAnyGate() {
        GenContext ctx = field(declared(1, 0));
        var at = PLAN.layout().at(60, 40, 0);
        FortStage.stamp(ctx, PLAN, at, 0);
        int[] g = at.cell(ShieldFort.GENERATOR[0], ShieldFort.GENERATOR[1]);
        for (FortPlan.Gate gate : PLAN.gates()) {
            int[] mouth = at.cell(gate.mouthX(), gate.mouthY());
            int walk = steps(ctx.grid, mouth[0], mouth[1], g[0] + 2, g[1] + 2);
            int crow = Math.abs(mouth[0] - g[0]) + Math.abs(mouth[1] - g[1]) - 4;
            assertTrue(walk >= crow + 8, "gate path " + walk + " against " + crow + " in a straight line");
        }
    }

    @Test
    void itIsADifferentFortFromTheBatteryFort() {
        assertFalse(PLAN.gates().size() == BatteryFort.PLAN.gates().size());
        assertTrue(ShieldFort.NESTS.size() != BatteryFort.NESTS.size() || PLAN.width() != BatteryFort.WIDTH);
        // The octagon's corners are cut away: the works' corner cells are not works.
        assertFalse(ShieldFort.isWorks(ShieldFort.OX, ShieldFort.OY));
        assertTrue(ShieldFort.isWorks(ShieldFort.OX + 23, ShieldFort.OY + 23));
        int shieldWall = 0, batteryWall = 0;
        for (int x = 0; x < 82; x++) {
            for (int y = 0; y < 80; y++) {
                if (ShieldFort.isWall(x, y)) shieldWall++;
                if (BatteryFort.isWall(x, y)) batteryWall++;
            }
        }
        assertTrue(shieldWall != batteryWall);
    }

    @Test
    void theBeltStandsOffTheWorksOpensInFrontOfEachGateAndIsNotARectangle() {
        for (FortBelt.Cell cell : PLAN.belt()) {
            boolean cleared = false;
            for (int[] c : PLAN.clearCells()) cleared |= c[0] == cell.x() && c[1] == cell.y();
            assertFalse(cleared, "belt cell on the works or glacis at " + cell.x() + "," + cell.y());
            assertTrue(cell.x() >= 0 && cell.y() >= 0 && cell.x() < PLAN.width() && cell.y() < PLAN.height());
        }
        assertEquals(6, FortBelt.nests(PLAN.belt()).size());
        Set<FortBelt.Role> roles = new HashSet<>();
        for (FortBelt.Cell cell : PLAN.belt()) roles.add(cell.role());
        assertTrue(roles.containsAll(Set.of(FortBelt.Role.TRENCH, FortBelt.Role.BAG, FortBelt.Role.WIRE,
                FortBelt.Role.EARTH, FortBelt.Role.NEST)));
        for (FortBelt.Cell cell : PLAN.belt()) {
            if (cell.role() != FortBelt.Role.TRENCH && cell.role() != FortBelt.Role.WIRE) continue;
            for (FortPlan.Gate gate : PLAN.gates()) {
                boolean along = gate.outX() != 0 ? (gate.outX() < 0 ? cell.x() < ShieldFort.OX : cell.x() >= ShieldFort.OX + ShieldFort.WORKS)
                        : (gate.outY() < 0 ? cell.y() < ShieldFort.OY : cell.y() >= ShieldFort.OY + ShieldFort.WORKS);
                int across = gate.outX() != 0 ? Math.abs(cell.y() - gate.mouthY()) : Math.abs(cell.x() - gate.mouthX());
                assertFalse(along && across <= 3, cell.role() + " crosses a gate lane at " + cell.x() + "," + cell.y());
            }
        }
        Set<Integer> columns = new HashSet<>();
        for (FortBelt.Cell cell : PLAN.belt()) {
            if (cell.role() == FortBelt.Role.TRENCH && cell.x() < ShieldFort.OX - 4) columns.add(cell.x());
        }
        assertTrue(columns.size() >= 4, "the western side kinks through " + columns.size() + " columns");
    }

    @Test
    void theStageStandsBothKindsWithMapUniqueFortIds() {
        GenContext ctx = field(declared(1, 1));
        FortStage stage = new FortStage();
        stage.run(ctx);
        assertEquals(2, stage.placed().size());
        assertEquals(FortKind.BATTERY_FORT, stage.placed().get(0).plan().kind());
        assertEquals(FortKind.SHIELD_FORT, stage.placed().get(1).plan().kind());
        Set<Integer> ids = new HashSet<>();
        for (DefensePost post : ctx.defensePosts) ids.add(post.fort.fortId());
        assertEquals(Set.of(0, 1), ids);
        FortStage again = new FortStage();
        again.run(field(declared(1, 1)));
        assertEquals(stage.placed().get(1).placement(), again.placed().get(1).placement());
    }

    private static int steps(NavigationGrid grid, int sx, int sy, int tx, int ty) {
        int[][] dist = new int[grid.getWidth()][grid.getHeight()];
        for (int[] row : dist) Arrays.fill(row, -1);
        ArrayDeque<int[]> open = new ArrayDeque<>();
        open.add(new int[]{sx, sy});
        dist[sx][sy] = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty()) {
            int[] at = open.poll();
            if (at[0] == tx && at[1] == ty) return dist[tx][ty];
            for (int[] d : dirs) {
                int nx = at[0] + d[0], ny = at[1] + d[1];
                if (!grid.inBounds(nx, ny) || dist[nx][ny] >= 0 || !grid.isWalkable(nx, ny)) continue;
                dist[nx][ny] = dist[at[0]][at[1]] + 1;
                open.add(new int[]{nx, ny});
            }
        }
        return Integer.MAX_VALUE;
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
