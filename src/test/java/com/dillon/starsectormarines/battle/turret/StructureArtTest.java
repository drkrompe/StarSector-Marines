package com.dillon.starsectormarines.battle.turret;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The painted-art block of a structure: what it parses, what it refuses, and that the shipped paths exist. */
class StructureArtTest {

    @Test
    void parsesABaseAndAnimatedLayers() throws Exception {
        StructureArt art = StructureArt.parse("structure.t", new JSONObject("""
                { "base": "graphics/structures/b.png", "layers": [
                  { "sprite": "graphics/structures/r.png", "cells": 2.8, "spinDegPerSec": 14.0,
                    "tintByHolder": true, "dome": true },
                  { "sprite": "graphics/structures/c.png", "cells": 1.6, "pulseHz": 0.6, "pulseDepth": 0.35 } ] }
                """));
        assertEquals("graphics/structures/b.png", art.basePath);
        assertEquals(2, art.layers().size());
        StructureArt.Layer ring = art.layers().get(0);
        assertEquals(14f, ring.spinDegPerSec());
        assertTrue(ring.dome());
        assertTrue(ring.tintByHolder());
        StructureArt.Layer core = art.layers().get(1);
        assertEquals(0f, core.spinDegPerSec());
        assertEquals(0.35f, core.pulseDepth(), 1e-6f);
        assertFalse(core.dome());
    }

    @Test
    void aBaseAloneIsEnoughAndLayersAloneAreToo() throws Exception {
        assertEquals(0, StructureArt.parse("s", new JSONObject("{ \"base\": \"a.png\" }")).layers().size());
        StructureArt layersOnly = StructureArt.parse("s", new JSONObject(
                "{ \"layers\": [ { \"sprite\": \"a.png\", \"cells\": 1.0 } ] }"));
        assertNull(layersOnly.basePath);
    }

    @Test
    void refusesAnEmptyBlockAndOutOfRangeAnimation() {
        assertThrows(JSONException.class, () -> StructureArt.parse("s", new JSONObject("{}")));
        assertThrows(JSONException.class, () -> StructureArt.parse("s", new JSONObject(
                "{ \"layers\": [ { \"sprite\": \"a.png\", \"cells\": 0.0 } ] }")));
        assertThrows(JSONException.class, () -> StructureArt.parse("s", new JSONObject(
                "{ \"layers\": [ { \"sprite\": \"a.png\", \"cells\": 1.0, \"pulseHz\": 1.0, \"pulseDepth\": 1.5 } ] }")));
        assertThrows(JSONException.class, () -> StructureArt.parse("s", new JSONObject(
                "{ \"layers\": [ { \"cells\": 1.0 } ] }")));
    }

    @Test
    void everyShippedArtPathAndMountSpriteExistsOnDisk() {
        int withArt = 0;
        for (StructureDef structure : TurretCatalogRegistry.installed().structures()) {
            if (structure.art == null) continue;
            withArt++;
            if (structure.art.basePath != null) assertShipped(structure.id, structure.art.basePath);
            for (StructureArt.Layer layer : structure.art.layers()) assertShipped(structure.id, layer.spritePath());
            // A mod-owned mount sprite is checked too; vanilla weapon sprites live in the game install.
            for (String path : new String[]{structure.mount.spritePath, structure.mount.recoilSpritePath}) {
                if (path != null && path.startsWith("graphics/structures/")) assertShipped(structure.id, path);
            }
        }
        assertTrue(withArt >= 2, "the fortress battery and the shield generator carry painted art");
        assertNotNull(TurretCatalogRegistry.requireStructure("structure.shield-generator").art);
    }

    private static void assertShipped(String structureId, String path) {
        assertTrue(Files.isRegularFile(Path.of("mod", path)), structureId + " names " + path + ", which is not shipped");
    }
}
