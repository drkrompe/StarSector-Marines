package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Box;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Cell;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnedMapRecipeTest {

    private static OwnedMap.Drop river(int width, List<Cell> crossings, int crossingWidth, List<OwnedMap.Depth> depths) {
        return new OwnedMap.Drop("river", OwnedMap.DropRole.RIVER, List.of(new Cell(0, 10), new Cell(29, 10)), width,
                crossings, crossingWidth, depths);
    }

    @Test
    void aDropIsCutAcrossItsWidthAndDeckedAcrossItAtACrossing() {
        OwnedMapRecipe.Cut cut = OwnedMapRecipe.cut(river(5, List.of(new Cell(15, 10)), 3, List.of()), 30, 20);
        for (int y = 0; y < 20; y++) assertEquals(y >= 8 && y <= 12, cut.drop()[y * 30 + 4], "row " + y);
        for (int x = 0; x < 30; x++) for (int y = 8; y <= 12; y++)
            assertEquals(x >= 14 && x <= 16, cut.decks()[y * 30 + x], x + "," + y);
    }

    /** A 40 by 40 map: a river across it with one crossing, a bank on the north edge, a works lot south of the river. */
    private static OwnedMap little() {
        OwnedMap.Settlement works = new OwnedMap.Settlement("works", "test", OwnedMap.SettlementRole.WORKS,
                new Box(8, 2, 30, 12), List.of(new Cell(19, 12)), List.of(), List.of(), null);
        OwnedMap.Drop drop = new OwnedMap.Drop("river", OwnedMap.DropRole.RIVER, List.of(new Cell(0, 20), new Cell(39, 20)),
                5, List.of(new Cell(19, 20)), 3, List.of());
        return new OwnedMap("little", 40, 40, null, null, null, new Cell(2, 6), new Cell(36, 30), List.of(), List.of(),
                List.of(new OwnedMap.Site("ford", TacticalNode.Kind.BEACHHEAD, null, new Cell(19, 26), new Box(16, 24, 22, 28),
                        Faction.MARINE, 10, 0)),
                List.of(works), List.of(), List.of(), List.of(drop),
                List.of(new OwnedMap.Rise("bank", OwnedMap.RiseRole.BANK, new Box(0, 37, 39, 39), .5f)),
                List.of(new Cell(36, 30)));
    }

    private static TargetProfile world(SurfacePalette palette, Climate climate) {
        return new TargetProfile(5, 5, 1, 1, "independent", EnumSet.of(EconomicFunction.HABITATION), palette,
                SettlementLink.ROAD).withClimate(climate);
    }

    @Test
    void theLayoutStandsStillUnderEverySkinAndOnlyASealedLotsEdgeIsWalled() {
        OwnedMap map = little();
        OwnedMapRecipe.Built open = OwnedMapRecipe.build(map, world(SurfacePalette.VERDANT, Climate.NONE), 7);
        MapResult base = OwnedMapRecipe.result(open);
        Box lot = map.settlements().get(0).lot();
        for (SurfacePalette palette : SurfacePalette.values()) {
            OwnedMapRecipe.Built built = OwnedMapRecipe.build(map, world(palette, Climate.NONE), 7);
            MapResult result = OwnedMapRecipe.result(built);
            boolean sealed = built.adaptations().get("works") == BiomeSkin.Adaptation.SEALED_PERIMETER;
            int walled = 0;
            for (int y = 0; y < 40; y++) for (int x = 0; x < 40; x++) {
                boolean edge = lot.contains(x, y) && (x == lot.x0() || x == lot.x1() || y == lot.y0() || y == lot.y1());
                boolean voidCell = result.topology.getGroundKind(x, y) == GroundKind.VOID;
                assertEquals(base.topology.getGroundKind(x, y) == GroundKind.VOID, voidCell, palette + " drop moved " + x + "," + y);
                assertFalse(voidCell && result.topology.isWall(x, y), palette + " drop is a wall at " + x + "," + y);
                // Nature may dress open country; the lot is held ground and stands as the document lays it.
                if (lot.contains(x, y) || voidCell) {
                    if (base.grid.isWalkable(x, y) == result.grid.isWalkable(x, y)) continue;
                    assertTrue(sealed && edge && result.topology.isWall(x, y), palette + " moved " + x + "," + y);
                    walled++;
                } else {
                    assertFalse(result.topology.isWall(x, y), palette + " walled open country at " + x + "," + y);
                }
            }
            assertEquals(sealed, walled > 0, palette + " perimeter");
            assertEquals(TacticalNode.Kind.BEACHHEAD, built.sites().get(0).kind);
            NaturalVoid look = result.topology.naturalVoid();
            assertNotNull(look, palette.name());
            assertEquals(built.skin().drop(OwnedMap.DropRole.RIVER).style(), look.style(), palette.name());
            assertNotNull(result.topology.naturalRise(), palette.name());
        }
    }

    @Test
    void aSealedLotOpensALockAtEveryGateAndYieldsToARoad() {
        int w = 24, h = 24;
        GenContext c = new GenContext(new NavigationGrid(w, h), new CellTopology(w, h), new Random(1), w, h, 1);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            c.grid.setWalkableFloor(x, y);
            c.topology.setGroundKind(x, y, GroundKind.STONE);
        }
        for (int x = 0; x < w; x++) c.topology.setGroundKind(x, 12, GroundKind.STREET);
        OwnedMap.Settlement lot = new OwnedMap.Settlement("lot", "test", OwnedMap.SettlementRole.TOWN,
                new Box(4, 4, 20, 20), List.of(new Cell(12, 4)), List.of(), List.of(), null);
        OwnedMapRecipe.perimeter(c, lot, Habitat.SEALED, null);
        for (int x = 10; x <= 14; x++) assertTrue(c.grid.isWalkable(x, 4), "lock " + x);
        assertFalse(c.grid.isWalkable(6, 4));
        assertTrue(c.grid.isWalkable(4, 12) && c.grid.isWalkable(20, 12), "the road runs through");
        // The north side has no gate and no road: it gets a lock at its middle.
        assertTrue(c.grid.isWalkable(12, 20));
        assertFalse(c.grid.isWalkable(6, 20));
        assertEquals("colony.ceramic-wall", c.topology.getSurfaceId(6, 4));
    }
}
