package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The purpose-first panel's advice text. */
public class SurfaceBrowserViewTest {

    @Test
    void theAdviceSaysWhyOpeningIsUnavailable() {
        SurfaceCatalog.Candidate shipped = new SurfaceCatalog.Candidate(
                "floors.grass", SurfaceCatalog.VARIANT_POOL, "Floors_Tiles",
                null, List.of(), true, null);
        SurfaceCatalog.Purpose grass = new SurfaceCatalog.Purpose(
                "GRASS", SurfaceCatalog.GROUND_KIND, "floors.grass",
                SurfaceCatalog.VARIANT_POOL, List.of(shipped));

        String advice = SurfaceBrowserView.adviceFor(grass, shipped);
        assertTrue(advice.contains("no authoring document"),
                "an operator must be told why Open is dead: " + advice);

        SurfaceCatalog.Candidate authored = new SurfaceCatalog.Candidate(
                "urban.wall", "wall-3x3", "urban-tileset",
                Paths.get("art-source/tilesets/urban-tileset.tileset-authoring.json"),
                List.of(new SurfaceCatalog.Slot("doodad.urban.c3r0", "nw", true)), true, null);
        assertTrue(SurfaceBrowserView.adviceFor(grass, authored).contains("1 slots selected"));
    }

    @Test
    void anUnmappedSurfaceSaysSoRatherThanLookingEmpty() {
        SurfaceCatalog.Purpose snow = new SurfaceCatalog.Purpose(
                "SNOW", SurfaceCatalog.GROUND_KIND, null, null, List.of());
        assertEquals("SNOW has nothing mapped to it.",
                SurfaceBrowserView.adviceFor(snow, null));
    }
}
