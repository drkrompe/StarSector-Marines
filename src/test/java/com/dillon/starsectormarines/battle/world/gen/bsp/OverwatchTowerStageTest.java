package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance for {@link OverwatchTowerStage} — the first taxonomy consumer.
 * Drives the real conquest recipe and checks the map gains corner-tower guns.
 *
 * <p>Overwatch towers are distinguishable from {@link DefensePostStamper}'s
 * beach {@link DefensePostKind#LIGHT} posts without a marker: a beach LIGHT post
 * rings its single turret with vents on all four cardinals, so the turret cell
 * has no walkable cardinal neighbor. An overwatch tower mounts its turret
 * against a wall with the field of fire left open, so the turret cell has at
 * least one walkable cardinal (the arc) <em>and</em> at least one non-walkable
 * cardinal (the back wall). That signature isolates the stage's output.
 */
public class OverwatchTowerStageTest {

    private static final long[] CONQUEST_SEEDS = { 1L, 42L, 100L, 777L };

    @Test
    void conquestMapsGainCornerTowers() {
        BspCityGenerator gen = new BspCityGenerator();
        int total = 0;
        for (long seed : CONQUEST_SEEDS) {
            MapResult map = gen.generate(240, 160, seed, TraversalAxis.SOUTH_TO_NORTH);
            List<DefensePost> towers = towers(map);
            System.out.println("seed=" + seed + " overwatch towers=" + towers.size()
                    + " (of " + map.defensePosts.size() + " total posts)");
            for (DefensePost t : towers) {
                int x = t.anchorX, y = t.anchorY;
                assertFalse(map.grid.isWalkable(x, y),
                        () -> "tower mount should be non-walkable at " + x + "," + y);
                assertTrue(map.topology.getGroundKind(x, y) == GroundKind.STONE,
                        () -> "tower mount should be a STONE pad at " + x + "," + y);
            }
            total += towers.size();
        }
        assertTrue(total > 0, "conquest batch produced no overwatch towers");
    }

    /**
     * The campaign → battle bridge: a fortified target world fields a longer,
     * heavier overwatch line than an undefended one. Drives the 5-arg
     * {@code generate} directly (no campaign) with a constructed high-defense
     * {@link TargetProfile} vs {@link TargetProfile#NEUTRAL} on the same seed.
     *
     * <p>Two signals, one supply-independent: (a) count — fortified ≥ neutral
     * (dense-map site supply can cap the count, hence ≥ not strict); (b) turret
     * tier — fortified towers escalate to {@code HEPHAESTUS} while neutral towers
     * stay {@code VULCAN}. (b) proves the bridge is wired even when (a) saturates.
     */
    @Test
    void fortifiedWorldsFieldMoreAndHeavierGuns() {
        BspCityGenerator gen = new BspCityGenerator();
        TargetProfile fortified = new TargetProfile(8, 5, 7, 2, "hegemony",
                EnumSet.of(EconomicFunction.HEAVY_INDUSTRY), // defenseLevel 7 → heavy tier
                SurfacePalette.ROCK, SettlementLink.ROAD);
        int neutralTotal = 0, fortifiedTotal = 0, fortifiedHeavy = 0, neutralHeavy = 0;
        for (long seed : CONQUEST_SEEDS) {
            List<DefensePost> n = towers(gen.generate(240, 160, seed, TraversalAxis.SOUTH_TO_NORTH, TargetProfile.NEUTRAL));
            List<DefensePost> f = towers(gen.generate(240, 160, seed, TraversalAxis.SOUTH_TO_NORTH, fortified));
            assertTrue(f.size() >= n.size(),
                    () -> "fortified world fielded fewer towers (" + f.size() + ") than neutral (" + n.size() + ")");
            neutralTotal += n.size();
            fortifiedTotal += f.size();
            neutralHeavy += countStructure(n, TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID);
            fortifiedHeavy += countStructure(f, TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID);
        }
        System.out.println("neutral towers=" + neutralTotal + " (heavy=" + neutralHeavy + "), "
                + "fortified towers=" + fortifiedTotal + " (heavy=" + fortifiedHeavy + ")");
        assertTrue(fortifiedTotal >= neutralTotal, "fortified batch fielded fewer towers than neutral");
        assertEquals(0, neutralHeavy, "neutral world should mount no heavy turrets");
        assertTrue(fortifiedHeavy > 0, "fortified world mounted no heavy turrets — tier escalation not wired");
    }

    private static int countStructure(List<DefensePost> posts, String structureId) {
        int c = 0;
        for (DefensePost p : posts) {
            if (p.turrets.get(0).structureId.equals(structureId)) c++;
        }
        return c;
    }

    @Test
    void deterministicFromSeed() {
        BspCityGenerator gen = new BspCityGenerator();
        int a = towers(gen.generate(240, 160, 777L, TraversalAxis.SOUTH_TO_NORTH)).size();
        int b = towers(gen.generate(240, 160, 777L, TraversalAxis.SOUTH_TO_NORTH)).size();
        assertTrue(a == b, "tower count not reproducible from seed (" + a + " vs " + b + ")");
    }

    /**
     * Ground stranded somewhere else does not disarm the line.
     *
     * <p>The mount guard has to ask whether <em>this cell</em> strands ground,
     * not whether any ground anywhere is stranded. Those are the same question
     * only on a map that is whole to begin with, and no generated one is. Under
     * the map-wide form a single orphan pocket left by an earlier stage refuses
     * every candidate on the map, so the whole overwatch line goes unmounted
     * without an exception or a log line.
     *
     * <p>Asked of a bare fixture rather than a generated map: a field, one wall
     * across it, and a blockhouse in the corner. The blockhouse stands in both
     * runs — so the scorer sees the same geometry and picks the same sites —
     * and the only difference is the cell at its centre, walled in the control
     * and walkable-but-unreachable here.
     */
    @Test
    void anOrphanPocketElsewhereDoesNotDisarmTheLine() {
        int whole = mountedOnFixture(false);
        int orphaned = mountedOnFixture(true);

        assertTrue(whole > 0, "the control mounted no towers, so this measures nothing");
        assertEquals(whole, orphaned, "the line mounted " + whole + " towers on an unbroken map "
                + "and " + orphaned + " on the same map with one cell sealed off in a far corner, "
                + "so the guard is answering a question about the map rather than about the stamp");
    }

    private static int mountedOnFixture(boolean orphan) {
        int w = 200;
        int h = 100;
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.STREET);
            }
        }
        // The wall the towers back onto — a long one, so site supply rather than
        // the guard is what the budget runs out against.
        for (int x = 10; x <= w - 11; x++) {
            grid.setWalkable(x, 60, false);
            topology.setWall(x, 60, true);
        }
        for (int y = 1; y <= 3; y++) {
            for (int x = 1; x <= 3; x++) {
                grid.setWalkable(x, y, false);
                topology.setWall(x, y, true);
            }
        }
        if (orphan) {
            grid.setWalkableFloor(2, 2);
            topology.setWall(2, 2, false);
        }

        GenContext ctx = new GenContext(grid, topology, new Random(7L), w, h, 7L);
        // No axis: the fixture's depth bands read UNSET either way, so the stage's
        // depth gate is skipped and the scorer keeps all four firing directions.
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(grid, topology, null));
        new OverwatchTowerStage().run(ctx);
        return ctx.defensePosts.size();
    }

    /** LIGHT single-turret posts mounted against a wall with an open arc — the stage's signature (see class doc). */
    private static List<DefensePost> towers(MapResult map) {
        NavigationGrid grid = map.grid;
        List<DefensePost> out = new ArrayList<>();
        for (DefensePost p : map.defensePosts) {
            if (p.tier != DefensePostKind.LIGHT || p.turrets.size() != 1) continue;
            int x = p.anchorX, y = p.anchorY;
            boolean openArc = walkable(grid, x + 1, y) || walkable(grid, x - 1, y)
                    || walkable(grid, x, y + 1) || walkable(grid, x, y - 1);
            boolean backWall = nonWalkable(grid, x + 1, y) || nonWalkable(grid, x - 1, y)
                    || nonWalkable(grid, x, y + 1) || nonWalkable(grid, x, y - 1);
            if (openArc && backWall) out.add(p);
        }
        return out;
    }

    private static boolean walkable(NavigationGrid grid, int x, int y) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y);
    }

    private static boolean nonWalkable(NavigationGrid grid, int x, int y) {
        return grid.inBounds(x, y) && !grid.isWalkable(x, y);
    }
}
