package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A palette draws a ground kind from its own block only where it names one; everything else, and every other world,
 * keeps the kind's ordinary art. That fallback is what lets a palette restyle stone without restyling streets.
 */
class PaletteGroundRenderTest {

    private static GenMappingRegistry mapping(String json) throws Exception {
        GenMappingRegistry registry = new GenMappingRegistry();
        registry.ingest(new JSONObject(json));
        return registry;
    }

    @Test
    void aPaletteDrawsOnlyTheKindsItNames() throws Exception {
        GenMappingRegistry m = mapping("{\"groundRender\":{\"STONE\":\"floors.stone\",\"STREET\":\"road\"},"
                + "\"paletteGroundRender\":{\"TOXIC\":{\"STONE\":\"toxic.crust\"}}}");
        assertEquals("toxic.crust", m.groundBlockId(GroundKind.STONE, SurfacePalette.TOXIC));
        assertEquals("road", m.groundBlockId(GroundKind.STREET, SurfacePalette.TOXIC));
        assertEquals("floors.stone", m.groundBlockId(GroundKind.STONE, SurfacePalette.ROCK));
        assertEquals("floors.stone", m.groundBlockId(GroundKind.STONE, null));
    }

    @Test
    void aPaletteKindIsDeclaredOnce() throws Exception {
        GenMappingRegistry m = mapping("{\"paletteGroundRender\":{\"TOXIC\":{\"STONE\":\"a\"}}}");
        assertThrows(Exception.class,
                () -> m.ingest(new JSONObject("{\"paletteGroundRender\":{\"TOXIC\":{\"STONE\":\"b\"}}}")));
    }
}
