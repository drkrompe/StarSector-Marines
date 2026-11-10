package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.NaturalVoidZoning;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsMeltStageTest {

    private static GenContext context() {
        GenContext ctx = new GenContext(new NavigationGrid(4, 3), new CellTopology(4, 3), new Random(1), 4, 3, 7L);
        for (int y = 0; y < 3; y++) for (int x = 0; x < 4; x++) ctx.grid.setWalkable(x, y, true);
        boolean[] melt = new boolean[12];
        melt[5] = melt[6] = true;
        ctx.put(RuinsKeys.MELT, melt);
        return ctx;
    }

    @Test
    void thePlannedMeltIsCutAsAFoundryDrop() {
        GenContext ctx = context();
        new RuinsMeltStage().run(ctx);
        assertEquals(GroundKind.VOID, ctx.topology.getGroundKind(1, 1));
        assertFalse(ctx.grid.isWalkable(2, 1));
        assertTrue(ctx.grid.hasTag(1, 1, NavigationGrid.CellTag.SEE_THROUGH));
        assertTrue(ctx.grid.isWalkable(0, 1), "only the planned cells are cut");
        assertEquals(NaturalVoid.Style.FOUNDRY, ctx.topology.naturalVoid().style());
    }

    @Test
    void aMapThatAlreadyDrawsADropKeepsItAndTheMeltOwnsOnlyItsOwnCells() {
        GenContext ctx = context();
        NaturalVoid rift = NaturalVoidZoning.LAVA_RIFT.look(3);
        boolean[] riftCells = new boolean[12];
        riftCells[0] = true;
        ctx.topology.addNaturalVoid(rift, riftCells);
        new RuinsMeltStage().run(ctx);
        assertSame(rift, ctx.topology.naturalVoid(0));
        assertEquals(NaturalVoid.Style.FOUNDRY, ctx.topology.naturalVoid(1).style());
        assertEquals(1, ctx.topology.dropAt(1, 1));
        assertEquals(0, ctx.topology.dropAt(0, 0), "the rift keeps its own cells");
    }
}
