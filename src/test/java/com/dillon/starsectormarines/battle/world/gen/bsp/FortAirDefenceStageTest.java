package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fortress.AirDefenceYard;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.ShieldFort;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The optional anti-air yard: declared per fort, stamped only where asked, and standing on ground the plan left free. */
class FortAirDefenceStageTest {

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

    private static long yardPosts(GenContext ctx) {
        return ctx.defensePosts.stream().filter(p -> p.fort != null && p.fort.aspect() == FortAspect.AIR_DEFENCE).count();
    }

    @Test
    void aFortWithoutTheDeclarationHasNoYard() {
        for (FortPlan plan : new FortPlan[]{BatteryFort.PLAN, ShieldFort.PLAN}) {
            GenContext ctx = field();
            FortStage.stamp(ctx, plan, plan.layout().at(60, 40, 0), 0);
            assertEquals(0, yardPosts(ctx), plan.kind() + " stamps no yard unless asked");
        }
    }

    @Test
    void theYardIsAConsoleTwoLocustBatteriesAndTwoSwarmerTowersAtEveryTurn() {
        for (FortPlan plan : new FortPlan[]{BatteryFort.PLAN, ShieldFort.PLAN}) {
            for (int turn = 0; turn < 4; turn++) {
                GenContext ctx = field();
                CompoundLayout.Placement at = plan.layout().at(60, 40, turn);
                FortStage.stamp(ctx, plan, at, 3, true);
                Set<String> structures = new HashSet<>();
                int swarmers = 0, locusts = 0;
                for (DefensePost post : ctx.defensePosts) {
                    if (post.fort == null || post.fort.aspect() != FortAspect.AIR_DEFENCE) continue;
                    assertEquals(3, post.fort.fortId());
                    String id = post.turrets.get(0).structureId;
                    structures.add(id);
                    if (id.equals(TurretCatalogRegistry.AA_SWARMER_STRUCTURE_ID)) swarmers++;
                    if (id.equals(TurretCatalogRegistry.AA_LOCUST_STRUCTURE_ID)) locusts++;
                    assertTrue(plan.isWorks(localX(at, post.anchorX, post.anchorY), localY(at, post.anchorX, post.anchorY)),
                            plan.kind() + " turn " + turn + ": " + id + " stands inside the works");
                }
                assertEquals(Set.of(TurretCatalogRegistry.FORT_AIR_CONTROL_STRUCTURE_ID,
                        TurretCatalogRegistry.AA_LOCUST_STRUCTURE_ID, TurretCatalogRegistry.AA_SWARMER_STRUCTURE_ID), structures);
                assertEquals(2, swarmers);
                assertEquals(2, locusts);
                assertEquals(5, yardPosts(ctx), "console, two Locust batteries, two Swarmer towers");
            }
        }
    }

    @Test
    void theYardIsClearOfEveryOtherStructureAndIsAllInsideTheWorks() {
        for (FortPlan plan : new FortPlan[]{BatteryFort.PLAN, ShieldFort.PLAN}) {
            AirDefenceYard yard = plan.airDefence();
            int[] f = yard.floor();
            for (int x = f[0] - 1; x < f[0] + f[2] + 1; x++) {
                for (int y = f[1] - 1; y < f[1] + f[3] + 1; y++) {
                    assertTrue(plan.isWorks(x, y), plan.kind() + " yard cell " + x + "," + y + " is inside the works");
                    assertFalse(plan.isWall(x, y), plan.kind() + " yard cell " + x + "," + y + " is not wall");
                }
            }
            for (FortAspect aspect : plan.aspects()) {
                int[] b = plan.floor(aspect);
                boolean overlaps = b[0] <= f[0] + f[2] && b[0] + b[2] >= f[0] - 1 && b[1] <= f[1] + f[3] && b[1] + b[3] >= f[1] - 1;
                assertFalse(overlaps, plan.kind() + " yard clear of " + aspect);
            }
            for (int[] lane : plan.lanes()) {
                boolean overlaps = lane[0] < f[0] + f[2] + 1 && lane[0] + lane[2] > f[0] - 1
                        && lane[1] < f[1] + f[3] + 1 && lane[1] + lane[3] > f[1] - 1;
                assertFalse(overlaps, plan.kind() + " yard clear of the lanes");
            }
            for (int[] tower : yard.closeInCells()) {
                for (int x = tower[0] - 1; x <= tower[0] + 1; x++) {
                    for (int y = tower[1] - 1; y <= tower[1] + 1; y++) {
                        assertTrue(plan.isWorks(x, y) && !plan.isWall(x, y), plan.kind() + " tower cell " + x + "," + y + " is open floor");
                        for (FortAspect aspect : plan.aspects()) {
                            int[] b = plan.floor(aspect);
                            assertFalse(x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3], plan.kind() + " tower clear of " + aspect);
                        }
                        for (int[] lane : plan.lanes()) {
                            assertFalse(x >= lane[0] && x < lane[0] + lane[2] && y >= lane[1] && y < lane[1] + lane[3], plan.kind() + " tower clear of the lanes");
                        }
                    }
                }
            }
            int[] c = plan.centrepiece();
            assertTrue(Math.abs(c[0] - yard.centreX()) > 5 || Math.abs(c[1] - yard.centreY()) > 5,
                    plan.kind() + " yard clear of the centrepiece");
        }
    }

    @Test
    void theDeclarationNamesHowManyFortsOfAKindCarryIt() {
        GroundDefences declared = new GroundDefences(0, false).withForts(FortKind.BATTERY_FORT, 2)
                .withAirDefence(FortKind.BATTERY_FORT, 5);
        assertEquals(2, declared.airDefendedCount(FortKind.BATTERY_FORT), "never more than the forts declared");
        assertEquals(0, declared.airDefendedCount(FortKind.SHIELD_FORT));
        assertEquals(1, declared.withAirDefence(FortKind.BATTERY_FORT, 1).airDefendedCount(FortKind.BATTERY_FORT));
    }

    private static int localX(CompoundLayout.Placement at, int wx, int wy) {
        return at.pose().unmap(wx - at.x(), wy - at.y(), at.layout().footprint().width(), at.layout().footprint().height())[0];
    }

    private static int localY(CompoundLayout.Placement at, int wx, int wy) {
        return at.pose().unmap(wx - at.x(), wy - at.y(), at.layout().footprint().width(), at.layout().footprint().height())[1];
    }
}
