package com.dillon.starsectormarines.battle.world.tiles;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The {@code fightingPosition} doodad property: parsed from tileset JSON onto {@link DoodadDef}. */
class FightingPositionRoleTest {

    private static final String SHEET = "{\"sheet\":\"graphics/test.png\",\"cellPx\":32,\"doodads\":["
            + "{\"id\":\"doodad.t.trench\",\"col\":0,\"row\":0,\"cover\":\"heavy\",\"fightingPosition\":\"trench\"},"
            + "{\"id\":\"doodad.t.bags\",\"col\":1,\"row\":0,\"cover\":\"heavy\",\"fightingPosition\":\"Parapet\"},"
            + "{\"id\":\"doodad.t.crate\",\"col\":2,\"row\":0,\"cover\":\"med\"}]}";

    @Test
    void theDeclaredRoleReachesTheLoadedDef() throws Exception {
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(new JSONObject(SHEET));

        assertEquals(FightingPositionRole.TRENCH, registry.doodad("doodad.t.trench").fightingPosition);
        assertEquals(FightingPositionRole.PARAPET, registry.doodad("doodad.t.bags").fightingPosition);
        assertEquals(FightingPositionRole.NONE, registry.doodad("doodad.t.crate").fightingPosition);
    }

    @Test
    void anUnknownRoleFailsLoud() {
        String bad = "{\"sheet\":\"s.png\",\"doodads\":[{\"id\":\"d.x\",\"col\":0,\"row\":0,"
                + "\"fightingPosition\":\"bunker\"}]}";
        assertThrows(IllegalStateException.class, () -> new TileRegistry().ingestSheet(new JSONObject(bad)));
    }

    @Test
    void jsonSpellingRoundTrips() {
        assertNull(FightingPositionRole.NONE.toJson());
        assertEquals("trench", FightingPositionRole.TRENCH.toJson());
        assertEquals(FightingPositionRole.PARAPET,
                FightingPositionRole.fromJson(FightingPositionRole.PARAPET.toJson()));
        assertEquals(FightingPositionRole.NONE, FightingPositionRole.fromJson(null));
        assertEquals(FightingPositionRole.NONE, FightingPositionRole.fromJson("  "));
    }
}
