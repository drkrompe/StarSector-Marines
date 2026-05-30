package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins {@link DefensePostStamper#blockedFootprint} to the actual per-tier stamp
 * geometry. The partition guard ({@link com.dillon.starsectormarines.battle.world.gen.PlacementGuards#wouldPartitionWalkable})
 * trusts {@code blockedFootprint} to enumerate exactly the cells a post turns
 * non-walkable — if a stamp method's silhouette ever drifts from the enumerator,
 * the guard would either miss a stranded cell or over-reject, so this test
 * stamps each tier/shape on an open grid and asserts the resulting non-walkable
 * cells match the enumerator cell-for-cell.
 */
public class DefensePostFootprintTest {

    private static final int W = 24, H = 24, CX = 12, CY = 12;

    private static NavigationGrid openGrid() {
        NavigationGrid g = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) g.setWalkableFloor(x, y);
        return g;
    }

    private static Set<String> stampedNonWalkable(DefensePostKind tier, DefensePostShape shape) {
        NavigationGrid grid = openGrid();
        CellTopology topo = new CellTopology(W, H);
        List<Doodad> doodads = new ArrayList<>();
        DefensePostStamper.stampPost(grid, topo, doodads, tier, shape, CX, CY, new Random(1));
        Set<String> out = new TreeSet<>();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (!grid.isWalkable(x, y)) out.add(x + "," + y);
            }
        }
        return out;
    }

    private static Set<String> enumerated(DefensePostKind tier, DefensePostShape shape) {
        Set<String> out = new TreeSet<>();
        for (int[] c : DefensePostStamper.blockedFootprint(tier, shape, CX, CY)) {
            out.add(c[0] + "," + c[1]);
        }
        return out;
    }

    private static void assertMatch(DefensePostKind tier, DefensePostShape shape) {
        assertEquals(enumerated(tier, shape), stampedNonWalkable(tier, shape),
                "blockedFootprint must match the actual stamp for " + tier
                        + (shape != null ? "/" + shape : ""));
    }

    @Test
    public void lightMatches() { assertMatch(DefensePostKind.LIGHT, null); }

    @Test
    public void mediumMatches() { assertMatch(DefensePostKind.MEDIUM, null); }

    @Test
    public void artilleryMatches() { assertMatch(DefensePostKind.ARTILLERY, null); }

    @Test
    public void droneHubMatches() { assertMatch(DefensePostKind.DRONE_HUB, null); }

    @Test
    public void largeShapesMatch() {
        for (DefensePostShape shape : DefensePostShape.values()) {
            assertMatch(DefensePostKind.LARGE, shape);
        }
    }
}
