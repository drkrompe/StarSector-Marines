package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boundary of {@link SurfacePalette}'s authority: it governs ground nobody
 * planted, and stops at ground the colonists made.
 *
 * <p>Both halves are asserted on the same barren world, because either one
 * alone is satisfied by the wrong implementation. A park that is still lawn
 * proves nothing if the wild lot beside it is lawn too — that is a palette
 * nobody wired up. A wild lot that is stone proves nothing if the park went
 * with it — that is a palette applied one layer too broadly, which is the
 * regression this test exists to catch. The contrast is the contract.
 */
class CultivatedGroundTest {

    private static final int WIDTH = 24;
    private static final int HEIGHT = 20;
    private static final BlockLeaf LOT = new BlockLeaf(2, 2, 21, 17, true);

    /** A barren rock: nothing grows here that was not put here on purpose. */
    private static GenContext barrenWorld() {
        GenContext ctx = new GenContext(new NavigationGrid(WIDTH, HEIGHT),
                new CellTopology(WIDTH, HEIGHT), new Random(42L), WIDTH, HEIGHT, 42L);
        ctx.put(BspKeys.MARKET_PROFILE, new TargetProfile(5, 5, 1, 1, "independent",
                EnumSet.noneOf(EconomicFunction.class),
                SurfacePalette.ROCK, SettlementLink.ROAD));
        return ctx;
    }

    private static int countGround(CellTopology topology, GroundKind kind) {
        int n = 0;
        for (int y = LOT.top; y <= LOT.bottom; y++) {
            for (int x = LOT.left; x <= LOT.right; x++) {
                if (topology.getGroundKind(x, y) == kind) n++;
            }
        }
        return n;
    }

    @Test
    void aParkOnABarrenWorldIsStillLawn() {
        GenContext ctx = barrenWorld();
        new ParkFiller().fill(LOT, ctx);

        // Every cell but the optional single-row stone path through it. The
        // colony irrigates this; the planet's own ground has no say.
        int paths = countGround(ctx.topology, GroundKind.STONE);
        assertTrue(paths <= Math.max(LOT.width(), LOT.height()),
                "a park should carry at most one stone path, found " + paths + " stone cells");
        assertEquals(LOT.width() * LOT.height() - paths,
                countGround(ctx.topology, GroundKind.GRASS),
                "a park is cultivated ground and keeps its lawn on any world");
    }

    @Test
    void aWildLotOnTheSameWorldIsNotLawn() {
        GenContext ctx = barrenWorld();
        new NatureZoneFiller(BlockKind.NATURE_GRASSLAND).fill(LOT, ctx);

        assertEquals(0, countGround(ctx.topology, GroundKind.GRASS),
                "wild ground follows the world's surface, and this world has no grass");
        assertTrue(countGround(ctx.topology, GroundKind.STONE) > 0,
                "a barren world's wild ground should be its own regolith");
    }
}
