package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.IndustrialYardFiller;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the authored industrial-yard grammars: perimeter bands, clear aisle, and generated art. */
public class IndustrialYardFillerTest {

    private static final IndustrialYardFiller FILLER = new IndustrialYardFiller();

    private static GenContext fill(BlockLeaf leaf, long seed) {
        int w = leaf.right + 3, h = leaf.bottom + 3;
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        GenContext ctx = new GenContext(grid, topology, new Random(seed), w, h, seed);
        FILLER.fill(leaf, ctx);
        return ctx;
    }

    @Test
    void propsFormPerimeterWorkZonesAndLeaveCentralAisleClear() {
        BlockLeaf[] leaves = {
                new BlockLeaf(2, 2, 13, 9, false),  // wide
                new BlockLeaf(2, 2, 9, 13, false),  // tall
                new BlockLeaf(2, 2, 7, 7, false),   // compact
        };
        for (BlockLeaf leaf : leaves) {
            for (long seed = 0; seed < 30; seed++) {
                GenContext ctx = fill(leaf, seed);
                assertTrue(ctx.doodads.size() >= 5, "yard grammar should place a readable cluster");
                Set<String> cells = new HashSet<>();
                for (Doodad d : ctx.doodads) {
                    assertTrue(leaf.contains(d.cellX, d.cellY));
                    assertEquals(TileManifest.DOODAD_SHEET, d.sheetPath);
                    assertTrue(cells.add(d.cellX + "," + d.cellY), "doodads must not stack");
                    if (leaf.width() >= leaf.height()) {
                        assertTrue(d.cellY == leaf.top || d.cellY == leaf.bottom,
                                "wide-yard props must stay in north/south storage bands");
                    } else {
                        assertTrue(d.cellX == leaf.left || d.cellX == leaf.right,
                                "tall-yard props must stay in west/east storage bands");
                    }
                }
            }
        }
    }
}
