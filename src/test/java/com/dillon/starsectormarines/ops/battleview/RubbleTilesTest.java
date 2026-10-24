package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;


import static org.junit.jupiter.api.Assertions.*;

class RubbleTilesTest {
    @Test void rubblePairsMustResolveAfterAllCatalogSheetsLoad() throws Exception {
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(new JSONObject("{sheet:'wall.png',cellPx:64,blocks:["
                + "{id:'wall',layout:'wall-faces-4x4',origin:[0,0],rubbleBlock:'broken'}]}"));
        assertThrows(IllegalStateException.class, registry::validateReferences);
        registry.ingestSheet(new JSONObject("{sheet:'debris.png',cellPx:64,blocks:["
                + "{id:'broken',layout:'wall-faces-4x4',origin:[0,0]}]}"));
        assertDoesNotThrow(registry::validateReferences);
    }

    @Test void unpairedWallsAndNonWallRubbleKeepTheExistingFallback() {
        TileRegistry registry = TileRegistry.installed();
        assertNull(RubbleTiles.skin(registry, null, "urban.wall", null));
        assertNull(RubbleTiles.skin(registry, new CellTopology.WallRemnant("urban.wall",
                CellTopology.GroundKind.INDOOR, null, 3), "urban.wall", null));
    }

    @Test void aBreachInAFortificationLotDrawsItsStylesRubbleWhereTheStyleHasIt() throws Exception {
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(new JSONObject("{sheet:'kit.png',cellPx:64,blocks:["
                + "{id:'wall',layout:'wall-faces-4x4',origin:[0,0],rubbleBlock:'broken'},"
                + "{id:'broken',layout:'wall-faces-4x4',origin:[4,0]},"
                + "{id:'broken@modular',layout:'wall-faces-4x4',origin:[8,0]}]}"));
        CellTopology.WallRemnant remnant = new CellTopology.WallRemnant("wall", CellTopology.GroundKind.INDOOR, null, 3);
        assertEquals("broken@modular", RubbleTiles.skin(registry, remnant, null, "modular").id);
        assertEquals("broken", RubbleTiles.skin(registry, remnant, null, "earthwork").id);
        assertEquals("broken", RubbleTiles.skin(registry, remnant, null, null).id);
    }
}
