package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CoverPropDamage;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A lot's works wear its style's art and nothing else of it (`fort-works.md`). */
class FortWorksTest {

    private static final String BAG = "doodad.fort-sandbag-n";

    /** The base piece and its damage states on one sheet, and the derelict look of each on another. */
    private static TileRegistry registry() throws Exception {
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(sheet("base.png", BAG, "doodad.fort-sandbag-damaged-n", "doodad.fort-sandbag-destroyed-n"));
        registry.ingestSheet(sheet("derelict.png", BAG + "@derelict", "doodad.fort-sandbag-damaged-n@derelict"));
        return registry;
    }

    private static JSONObject sheet(String path, String... ids) throws Exception {
        JSONArray doodads = new JSONArray();
        for (int i = 0; i < ids.length; i++) {
            // A look's own cover fields are deliberately unlike the base's: nothing may read them.
            boolean look = ids[i].contains("@");
            doodads.put(new JSONObject().put("id", ids[i]).put("col", i).put("row", 0)
                    .put("cover", look ? "none" : "heavy").put("ballisticHalfHeight", look ? 0f : 0.4f));
        }
        return new JSONObject().put("sheet", path).put("cellPx", 128).put("doodads", doodads);
    }

    @Test
    void aPieceOnALotWearsTheLookAndKeepsItsOwnPhysics() throws Exception {
        TileRegistry registry = registry();
        CellTopology topology = new CellTopology(4, 4);
        FortWorks.mark(topology, 1, 1, EmplacementStyle.DERELICT);
        Doodad bag = new Doodad(1, 1, registry.doodad(BAG));

        Doodad dressed = FortWorks.dress(bag, topology, registry);

        assertEquals(registry.doodad(BAG + "@derelict").sheetPath, dressed.sheetPath);
        assertEquals(BAG, dressed.definitionId());
        assertEquals(bag.cover, dressed.cover);
        assertEquals(bag.ballisticHalfHeight, dressed.ballisticHalfHeight);
        assertEquals(bag.footprintCellsX, dressed.footprintCellsX);
        assertEquals("derelict", dressed.look());
        // Off the lot, or in a style with no look for it, the piece draws its own art.
        assertSame(bag, FortWorks.dress(bag, new CellTopology(4, 4), registry));
        FortWorks.mark(topology, 2, 2, EmplacementStyle.MODULAR);
        Doodad other = new Doodad(2, 2, registry.doodad(BAG));
        assertSame(other, FortWorks.dress(other, topology, registry));
    }

    @Test
    void theVentRingWearsItsLookThoughItsCellsHaveNoIdOfTheirOwn() throws Exception {
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(sheet("kit.png", FortWorks.VENT + "@modular"));
        CellTopology topology = new CellTopology(4, 4);
        FortWorks.mark(topology, 1, 1, EmplacementStyle.MODULAR);
        Doodad vent = new Doodad(1, 1, TileManifest.lightPostVent(), true, Doodad.COVER_HEAVY);

        Doodad dressed = FortWorks.dress(vent, topology, registry);

        assertEquals("kit.png", dressed.sheetPath);
        assertEquals(vent.cover, dressed.cover);
        assertEquals(vent.ballisticHalfHeight, dressed.ballisticHalfHeight);
        // Any other road-sheet frame is not the vent ring and keeps its own art.
        TileManifest.TileFrame vented = TileManifest.lightPostVent();
        Doodad other = new Doodad(1, 1, new TileManifest.TileFrame(vented.col + 1, vented.row), true, Doodad.COVER_HEAVY);
        assertSame(other, FortWorks.dress(other, topology, registry));
    }

    @Test
    void aRestyledPostTakesItsWholeLotAndNoOther() {
        CellTopology topology = new CellTopology(8, 1);
        for (int x : new int[]{0, 1, 2, 5, 6}) FortWorks.mark(topology, x, 0, EmplacementStyle.BRUTALIST);
        DefensePost post = new DefensePost(DefensePostKind.LIGHT, 1, 0,
                List.of(new DefensePost.TurretSpec("structure.any", 1, 0))).withStyle(EmplacementStyle.DERELICT);

        FortWorks.settle(topology, List.of(post));

        for (int x : new int[]{0, 1, 2}) assertEquals(EmplacementStyle.DERELICT, FortWorks.styleAt(topology, x, 0));
        assertNull(FortWorks.styleAt(topology, 3, 0));
        for (int x : new int[]{5, 6}) assertEquals(EmplacementStyle.BRUTALIST, FortWorks.styleAt(topology, x, 0));
    }

    @Test
    void aDamagedPieceKeepsItsLook() throws Exception {
        TileRegistry previous = TileRegistry.installed();
        TileRegistry registry = registry();
        TileRegistry.install(registry);
        try {
            NavigationGrid grid = new NavigationGrid(3, 3);
            for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) grid.setWalkableFloor(x, y);
            CellTopology topology = new CellTopology(3, 3);
            FortWorks.mark(topology, 1, 1, EmplacementStyle.DERELICT);
            List<Doodad> props = new ArrayList<>();
            props.add(FortWorks.dress(new Doodad(1, 1, registry.doodad(BAG)), topology, registry));
            CoverPropDamage damage = new CoverPropDamage(grid, props);
            damage.register(0, props.get(0), registry);

            damage.damage(1, 1, CoverPropDamage.STARTING_HP / 2);
            Doodad shown = props.get(0);
            assertEquals("doodad.fort-sandbag-damaged-n", shown.definitionId());
            assertEquals("derelict", shown.look());
            // The style has no destroyed look: the remains draw the base art rather than nothing.
            damage.damage(1, 1, CoverPropDamage.STARTING_HP);
            assertNull(props.get(0).look());
        } finally {
            TileRegistry.install(previous);
        }
    }
}
