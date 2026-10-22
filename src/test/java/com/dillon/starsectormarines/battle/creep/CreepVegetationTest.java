package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreepVegetationTest {

    private static final int W = 16, H = 12;

    /** Creep grows over a tree; once its crown is held to its threshold it dies, and only its trunk keeps blocking. */
    @Test
    void aHeldTreeDiesAndItsWitheredCrownOpensAroundTheTrunk() {
        Grove g = new Grove();
        Set<Integer> opened = new HashSet<>();
        int died = g.grow(new CreepVegetation(5L), opened);
        assertTrue(died > 0, "the tree died");
        boolean[] trunk = CreepVegetation.trunkOf(g.tree, g.crown, W);
        for (int i = 0; i < g.crown.length; i++) {
            assertEquals(!trunk[i], opened.contains(g.crown[i]), "crown cell " + i + (trunk[i] ? " is trunk" : " is crown"));
        }
    }

    /** One small oak on open ground, a source beside it. */
    private static final class Grove {
        final NavigationGrid grid = new NavigationGrid(W, H);
        final Doodad tree = new Doodad(7, 4, TileRegistry.installed().doodad(TreeCanopy.OAK_SMALL.id), 0);
        final int[] crown = CreepVegetation.crownCells(tree, TreeCanopy.OAK_SMALL, W, H);
        final CreepField field;

        Grove() {
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
            CellTopology topology = new CellTopology(W, H);
            for (int cell : crown) {
                grid.setWalkable(cell % W, cell / W, false);
                grid.setTag(cell % W, cell / W, NavigationGrid.CellTag.NATURAL_SOLID, true);
                topology.setGroundKind(cell % W, cell / W, CellTopology.GroundKind.GRASS);
            }
            field = new CreepField(grid, topology, 3L);
            for (int cell : crown) field.growOver(cell);
            field.putSource(1L, 2, 6, new CreepGrowth(6f, 30f));
        }

        /** Grows the creep until the tree dies; the tick it died, asserting the crown was held to its threshold. */
        int grow(CreepVegetation rule, Set<Integer> opened) {
            for (int t = 0; t < 6000; t++) {
                field.tick(t);
                if (t % CreepVegetation.CHECK_TICKS != 0) continue;
                rule.look(t, field, grid, List.of(tree), (x, y) -> opened.add(y * W + x));
                if (rule.diedAt(tree.cellX, tree.cellY) < 0) {
                    assertTrue(opened.isEmpty(), "nothing opens while the tree lives");
                    continue;
                }
                int held = CreepVegetation.livingCoverage(new CreepVegetation.Tree(0, 0, crown, new boolean[crown.length], 0), field);
                assertTrue(held >= CreepVegetation.DEATH_LO, "died held at " + held);
                return t;
            }
            return -1;
        }
    }
}
