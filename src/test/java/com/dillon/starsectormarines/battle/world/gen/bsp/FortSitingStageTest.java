package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.FortSitingPolicy;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.ShieldFort;
import com.dillon.starsectormarines.battle.world.gen.precinct.LandingKind;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Standoff;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mission's fort siting on a bare field with the two ends of the battle stated: the landing in the west, the keep in
 * the east. The questions are about where the stage stands each declared fort, so the field has no city; one test adds a
 * claimed block of ground to prove the prior-claim rule still binds a siting.
 */
class FortSitingStageTest {

    private static final int W = 400;
    private static final int H = 240;
    private static final int LANDING_X = 40;
    private static final int KEEP_X = 350;
    private static final int MID_Y = 120;

    private static GenContext field(GroundDefences declared, boolean withLanding) {
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
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL.withDeclaredDefences(declared));
        ctx.tactical.add(new TacticalNode(TacticalNode.Kind.COMMAND_POST, KEEP_X, MID_Y,
                KEEP_X - 8, MID_Y - 8, KEEP_X + 8, MID_Y + 8, Faction.DEFENDER, 100, 6));
        if (withLanding) {
            ctx.put(BspKeys.PRECINCTS, new PrecinctPlan(List.of(
                    Precinct.garrison("keep", KEEP_X, MID_Y, GrownTrunkPlan.Profile.hamlet(),
                            FortressProgram.garrison()),
                    Precinct.landing("landing", LANDING_X, MID_Y, GrownTrunkPlan.Profile.hamlet(),
                            LandingKind.FIELD, LandingKind.FIELD.program())),
                    MapPlacement.WEST, Standoff.FAR));
        }
        return ctx;
    }

    private static GroundDefences declared(FortSitingPolicy policy) {
        return new GroundDefences(0, false)
                .withForts(FortKind.BATTERY_FORT, 2)
                .withForts(FortKind.SHIELD_FORT, 1)
                .withSiting(policy);
    }

    private static int[] centreOf(FortStage.Placed p) {
        return p.placement().cell(p.plan().width() / 2, p.plan().height() / 2);
    }

    private static List<FortStage.Placed> run(GenContext ctx) {
        FortStage stage = new FortStage();
        stage.run(ctx);
        return List.copyOf(stage.placed());
    }

    @Test
    void theShieldFortStandsBesideTheKeepAndBatteryFortsStandOnTheApproach() {
        List<FortStage.Placed> placed = run(field(declared(FortSitingPolicy.COVER_THE_APPROACH), true));
        assertEquals(3, placed.size(), "the declared count stands");
        for (FortStage.Placed p : placed) {
            int[] c = centreOf(p);
            if (p.plan().kind() == FortKind.SHIELD_FORT) {
                // The keep's own ground is prior and the works need clear ground round them, so the fort stands beside the
                // keep, as near as that lets it, and not out in the middle of the map as leftover ground would put it.
                int[] g = p.placement().cell(ShieldFort.GENERATOR[0], ShieldFort.GENERATOR[1]);
                double reach = Math.hypot(g[0] - KEEP_X, g[1] - MID_Y);
                assertTrue(reach <= 50, "shield fort " + reach + " cells from the keep");
            } else {
                assertTrue(c[0] > LANDING_X + 60 && c[0] < KEEP_X - 60, "battery fort off the approach at " + c[0]);
                assertTrue(Math.abs(c[1] - MID_Y) <= 90, "battery fort off the line of advance at y " + c[1]);
                assertTrue(p.target() != null, "a sited fort records the ground it was aimed at");
                assertTrue(Math.hypot(c[0] - p.target().x(), c[1] - p.target().y()) < 30,
                        "battery fort " + p.fortId() + " not on its target");
            }
        }
    }

    @Test
    void theBatteryFortsCoverDifferentLanes() {
        List<FortStage.Placed> placed = run(field(declared(FortSitingPolicy.COVER_THE_APPROACH), true));
        int[] first = null;
        for (FortStage.Placed p : placed) {
            if (p.plan().kind() != FortKind.BATTERY_FORT) continue;
            int[] c = centreOf(p);
            if (first == null) {
                first = c;
                continue;
            }
            assertTrue(Math.hypot(c[0] - first[0], c[1] - first[1]) >= 66, "forts stand flush");
            assertTrue(Math.abs(c[1] - first[1]) > 40, "both forts on one lane");
        }
        assertTrue(first != null);
    }

    @Test
    void aMissionThatDoesNotOptInKeepsLeftoverGround() {
        List<FortStage.Placed> placed = run(field(declared(FortSitingPolicy.LEFTOVER_GROUND), true));
        for (FortStage.Placed p : placed) assertNull(p.target());
        // Nearest the map centre first, as ever: the first battery fort is at the middle, not out on the approach.
        FortStage.Placed battery = placed.stream().filter(p -> p.plan().kind() == FortKind.BATTERY_FORT)
                .findFirst().orElseThrow();
        int[] c = centreOf(battery);
        assertTrue(Math.hypot(c[0] - W / 2.0, c[1] - H / 2.0) < 60, "leftover order lost: " + c[0] + "," + c[1]);
    }

    @Test
    void aMapThatStatesNoLandingFallsBackToLeftoverGroundExactly() {
        List<FortStage.Placed> sited = run(field(declared(FortSitingPolicy.COVER_THE_APPROACH), false));
        List<FortStage.Placed> leftover = run(field(declared(FortSitingPolicy.LEFTOVER_GROUND), false));
        assertEquals(leftover.size(), sited.size());
        for (int i = 0; i < sited.size(); i++) {
            assertEquals(centreOf(leftover.get(i))[0], centreOf(sited.get(i))[0]);
            assertEquals(centreOf(leftover.get(i))[1], centreOf(sited.get(i))[1]);
            assertNull(sited.get(i).target());
        }
    }

    @Test
    void aClaimedTargetIsStillPriorAndTheFortStandsBesideIt() {
        GenContext ctx = field(declared(FortSitingPolicy.COVER_THE_APPROACH), true);
        // A claimed block (a lane compound) lies across the middle of the approach, where a battery fort is aimed.
        int[][] claim = new int[W][H];
        for (int[] column : claim) Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        for (int x = 150; x < 250; x++) {
            for (int y = 80; y < 160; y++) claim[x][y] = 1;
        }
        ctx.put(BspKeys.PRECINCT_CLAIM, claim);
        List<FortStage.Placed> placed = run(ctx);
        assertEquals(3, placed.size());
        for (FortStage.Placed p : placed) {
            for (int[] local : p.plan().clearCells()) {
                int[] cell = p.placement().cell(local[0], local[1]);
                assertEquals(GrownTrunkPlan.UNOWNED, claim[cell[0]][cell[1]], "fort stands on claimed ground");
            }
        }
    }

    @Test
    void fortsOnTheApproachDoNotSealTheWayFromTheLandingToTheKeep() {
        GenContext ctx = field(declared(FortSitingPolicy.COVER_THE_APPROACH), true);
        run(ctx);
        NavigationGrid grid = ctx.grid;
        boolean[][] seen = new boolean[W][H];
        ArrayDeque<int[]> open = new ArrayDeque<>();
        open.add(new int[]{LANDING_X, MID_Y});
        seen[LANDING_X][MID_Y] = true;
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
        assertTrue(seen[KEEP_X][MID_Y], "the approach is cut");
    }
}
