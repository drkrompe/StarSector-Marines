package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A world's own material for a ground kind, and a palette laid out as another taking that one's fillers. */
class PaletteGroundRenderTest {

    private static GenMappingRegistry mapping() throws Exception {
        GenMappingRegistry registry = new GenMappingRegistry();
        registry.ingest(new JSONObject("""
                {"groundRender": {"STONE": "floors.stone", "SAND": "floors.sand"},
                 "paletteGroundRender": {"VOLCANIC": {"STONE": "volcanic.basalt"}},
                 "fillers": {"NATURE_GRASSLAND": {"groundPool": [{"kind": "GRASS", "w": 1}]}},
                 "surfaceFillers": {"ROCK": {"NATURE_GRASSLAND": {"groundPool": [{"kind": "STONE", "w": 1}]}}}}
                """));
        return registry;
    }

    @Test
    void aWorldDrawsItsOwnMaterialWhereNamedAndTheKindsOtherwise() throws Exception {
        GenMappingRegistry registry = mapping();
        assertEquals("volcanic.basalt", registry.groundBlockId(GroundKind.STONE, SurfacePalette.VOLCANIC));
        assertEquals("floors.sand", registry.groundBlockId(GroundKind.SAND, SurfacePalette.VOLCANIC));
        assertEquals("floors.stone", registry.groundBlockId(GroundKind.STONE, SurfacePalette.ROCK));
        assertEquals("floors.stone", registry.groundBlockId(GroundKind.STONE, null));
    }

    @Test
    void aPaletteLaidOutAsAnotherFillsAsThatOneDoes() throws Exception {
        GenMappingRegistry registry = mapping();
        assertSame(registry.fillerParams(BlockKind.NATURE_GRASSLAND, SurfacePalette.ROCK),
                registry.fillerParams(BlockKind.NATURE_GRASSLAND, SurfacePalette.VOLCANIC));
        assertSame(registry.fillerParams(BlockKind.NATURE_GRASSLAND),
                registry.fillerParams(BlockKind.NATURE_GRASSLAND, SurfacePalette.FROZEN));
    }
}
