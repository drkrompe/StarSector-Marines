package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
        TargetProfile fortified = new TargetProfile(8, 5, 7, 2, "hegemony"); // defenseLevel 7 → heavy tier
        int neutralTotal = 0, fortifiedTotal = 0, fortifiedHeavy = 0, neutralHeavy = 0;
        for (long seed : CONQUEST_SEEDS) {
            List<DefensePost> n = towers(gen.generate(240, 160, seed, TraversalAxis.SOUTH_TO_NORTH, TargetProfile.NEUTRAL));
            List<DefensePost> f = towers(gen.generate(240, 160, seed, TraversalAxis.SOUTH_TO_NORTH, fortified));
            assertTrue(f.size() >= n.size(),
                    () -> "fortified world fielded fewer towers (" + f.size() + ") than neutral (" + n.size() + ")");
            neutralTotal += n.size();
            fortifiedTotal += f.size();
            neutralHeavy += countKind(n, TurretKind.HEPHAESTUS);
            fortifiedHeavy += countKind(f, TurretKind.HEPHAESTUS);
        }
        System.out.println("neutral towers=" + neutralTotal + " (heavy=" + neutralHeavy + "), "
                + "fortified towers=" + fortifiedTotal + " (heavy=" + fortifiedHeavy + ")");
        assertTrue(fortifiedTotal >= neutralTotal, "fortified batch fielded fewer towers than neutral");
        assertEquals(0, neutralHeavy, "neutral world should mount no heavy turrets");
        assertTrue(fortifiedHeavy > 0, "fortified world mounted no heavy turrets — tier escalation not wired");
    }

    private static int countKind(List<DefensePost> posts, TurretKind kind) {
        int c = 0;
        for (DefensePost p : posts) if (p.turrets.get(0).kind == kind) c++;
        return c;
    }

    @Test
    void deterministicFromSeed() {
        BspCityGenerator gen = new BspCityGenerator();
        int a = towers(gen.generate(240, 160, 777L, TraversalAxis.SOUTH_TO_NORTH)).size();
        int b = towers(gen.generate(240, 160, 777L, TraversalAxis.SOUTH_TO_NORTH)).size();
        assertTrue(a == b, "tower count not reproducible from seed (" + a + " vs " + b + ")");
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
