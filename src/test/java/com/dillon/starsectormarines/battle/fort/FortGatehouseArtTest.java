package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A gatehouse is drawn in its fort's style where it has a look for it, and in its base art where not. */
class FortGatehouseArtTest {

    @Test
    void aGatehouseWearsItsStylesLookAndOnlyANamedOne() throws Exception {
        FortGatehouseArt art = FortGatehouseArt.parse("structure.fort", new JSONObject()
                .put("tower", "graphics/fort/gate-tower.png")
                .put("towerDamaged", "graphics/fort/gate-tower-damaged.png")
                .put("gate", "graphics/fort/gate-leaves.png")
                .put("looks", new JSONArray().put("derelict")));

        FortGatehouseArt derelict = art.inStyle(EmplacementStyle.DERELICT);
        assertEquals("graphics/fort/derelict/gate-leaves.png", derelict.gate());
        assertEquals("graphics/fort/derelict/gate-tower-damaged.png", derelict.towerFor(0.1f));
        assertSame(art, art.inStyle(EmplacementStyle.MODULAR));
        assertSame(art, art.inStyle(null));
        assertEquals(6, art.spritePaths().size());
    }
}
