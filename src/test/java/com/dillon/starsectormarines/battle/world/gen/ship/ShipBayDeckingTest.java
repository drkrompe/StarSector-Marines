package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ShipBayDeckingTest {
    @Test
    void padAddsTheStationBehindEachHeadingAndFacesTheMouthTowardTheLane() {
        for (Gantry.Facing facing : Gantry.Facing.values()) {
            boolean vertical = facing.dy != 0;
            Gantry berth = Gantry.covering(10, 10, vertical ? 3 : 6, vertical ? 6 : 3,
                    facing, Gantry.Holds.MACHINE);
            Doodad pad = ShipBayDecking.pad(berth, TileRegistry.installed().doodad("auto-shop.bay"));
            assertNotNull(pad);
            assertEquals(vertical ? 5 : 7, pad.footprintCellsX);
            assertEquals(vertical ? 7 : 5, pad.footprintCellsY);
            for (int y = berth.bottom(); y <= berth.top(); y++)
                for (int x = berth.left(); x <= berth.right(); x++) assertTrue(pad.occupiesCell(x, y));
            int headX = facing == Gantry.Facing.EAST ? berth.left() - 1
                    : facing == Gantry.Facing.WEST ? berth.right() + 1 : berth.centerX;
            int headY = facing == Gantry.Facing.NORTH ? berth.bottom() - 1
                    : facing == Gantry.Facing.SOUTH ? berth.top() + 1 : berth.centerY;
            assertTrue(pad.occupiesCell(headX, headY));
            // Source pad opens south; positive world quarter turns carry S -> E -> N -> W.
            assertEquals(facing, new Gantry.Facing[]{Gantry.Facing.SOUTH, Gantry.Facing.EAST,
                    Gantry.Facing.NORTH, Gantry.Facing.WEST}[pad.quarterTurns]);
        }
    }

    @Test
    void boatAndShortBerthsKeepTheirOwnDecking() {
        var art = TileRegistry.installed().doodad("auto-shop.bay");
        assertNull(ShipBayDecking.pad(Gantry.covering(2, 2, 3, 6, Gantry.Facing.NORTH, Gantry.Holds.BOAT), art));
        assertNull(ShipBayDecking.pad(Gantry.covering(2, 2, 3, 4, Gantry.Facing.NORTH, Gantry.Holds.MACHINE), art));
    }

    @Test
    void onlyACompleteLegacyPadIsReplacedAndFixturesKeepTheirOrder() {
        TileRegistry registry = TileRegistry.installed();
        GenContext ctx = new GenContext(new NavigationGrid(12, 12), new CellTopology(12, 12),
                new Random(1), 12, 12, 1);
        ctx.gantries.add(Gantry.covering(3, 3, 3, 6, Gantry.Facing.NORTH, Gantry.Holds.MACHINE));
        for (int y = 2; y < 9; y++) for (int x = 2; x < 7; x++)
            ctx.doodads.add(new Doodad(x, y, registry.doodad("doodad.fl-grate-1")));
        Doodad fixture = new Doodad(2, 2, registry.doodad("doodad.industrial-machine-tool"));
        ctx.doodads.add(fixture);
        Doodad missing = ctx.doodads.remove(0);
        ShipBayDecking.apply(ctx);
        assertEquals(35, ctx.doodads.size(), "a partial/custom deck is preserved");
        ctx.doodads.add(0, missing);
        ShipBayDecking.apply(ctx);
        assertEquals(2, ctx.doodads.size());
        assertEquals("graphics/tilesets/auto-shop.png", ctx.doodads.get(0).sheetPath);
        assertSame(fixture, ctx.doodads.get(1));
        assertEquals(1, ctx.gantries.size());
    }
}
