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
        assertTrue(layersOnly.groundPad, "a structure stands on its footprint pad unless it says otherwise");
        assertFalse(StructureArt.parse("s", new JSONObject(
                "{ \"groundPad\": false, \"layers\": [ { \"sprite\": \"a.png\", \"cells\": 1.0 } ] }")).groundPad);
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
        assertThrows(JSONException.class, () -> StructureArt.parse("s", new JSONObject(
                "{ \"layers\": [ { \"sprite\": \"a.png\", \"cells\": 1.0, \"fireFlash\": 1.5 } ] }")));
    }

    @Test
    void variantsResolveEveryLayerToTheSamePaintingPerStructure() throws Exception {
        StructureArt art = StructureArt.parse("s", new JSONObject("""
                { "variants": 3, "layers": [
                  { "sprite": "g/body-{variant}.png", "cells": 3.0 },
                  { "sprite": "g/body-{variant}-glow.png", "cells": 3.0, "pulseHz": 0.3, "pulseDepth": 0.5 } ] }
                """));
        boolean[] seen = new boolean[4];
        for (long id = 1; id <= 60; id++) {
            int v = art.variantFor(id);
            assertTrue(v >= 1 && v <= 3);
            assertEquals(v, art.variantFor(id), "the same structure always wears the same painting");
            seen[v] = true;
        }
        assertTrue(seen[1] && seen[2] && seen[3], "sixty structures wear all three paintings");
        assertEquals("g/body-2-glow.png", art.layers().get(1).spritePath(2));
        assertEquals(1, StructureArt.parse("s", new JSONObject(
                "{ \"layers\": [ { \"sprite\": \"a.png\", \"cells\": 1.0 } ] }")).variantFor(42));
        assertThrows(JSONException.class, () -> StructureArt.parse("s", new JSONObject(
                "{ \"variants\": 0, \"layers\": [ { \"sprite\": \"a.png\", \"cells\": 1.0 } ] }")));
    }

    @Test
    void aWoundedBodyChangesEveryStateLayerTogetherAndLeavesItsRemains() throws Exception {
        StructureArt art = StructureArt.parse("s", new JSONObject("""
                { "variants": 2, "woundedBelow": 0.5, "remains": "g/body-{variant}-remains.png", "layers": [
                  { "sprite": "g/body-{variant}{state}.png", "cells": 3.0 },
                  { "sprite": "g/body-{variant}{state}-glow.png", "cells": 3.0 },
                  { "sprite": "g/halo.png", "cells": 4.0 } ] }
                """));
        assertFalse(art.woundedAt(0.5f));
        assertTrue(art.woundedAt(0.49f));
        assertEquals("g/body-2.png", art.layers().get(0).spritePath(2, false));
        assertEquals("g/body-2-wounded.png", art.layers().get(0).spritePath(2, true));
        assertEquals("g/body-2-wounded-glow.png", art.layers().get(1).spritePath(2, true));
        assertEquals("g/halo.png", art.layers().get(2).spritePath(2, true), "a layer without the token is the same hurt");
        assertEquals("g/body-1-remains.png", art.remainsPath(1));
        assertEquals(4f, art.remainsCells, "remains are drawn as wide as the widest layer unless told");
        StructureArt plain = StructureArt.parse("s", new JSONObject("{ \"layers\": [ { \"sprite\": \"a.png\", \"cells\": 1.0 } ] }"));
        assertFalse(plain.woundedAt(0f), "a body with no wounded painting is never drawn wounded");
        assertNull(plain.remainsPath(1));
        assertThrows(JSONException.class, () -> StructureArt.parse("s", new JSONObject(
                "{ \"woundedBelow\": 1.0, \"layers\": [ { \"sprite\": \"a.png\", \"cells\": 1.0 } ] }")));
    }

    @Test
    void aWoundedBodyOpensItsWoundsInOrderAsItIsHurtFurther() throws Exception {
        StructureArt art = StructureArt.parse("s", new JSONObject("""
                { "variants": 2, "woundedBelow": 0.5,
                  "bleed": { "sheet": "g/ooze.png", "frames": 4, "kinds": 2, "directions": 8, "fps": 4.0, "cells": 1.0,
                             "wounds": [ [ [0.1, 0.2, 90], [-0.1, 0.0, 180] ] ] },
                  "layers": [ { "sprite": "g/body.png", "cells": 2.0 } ] }
                """));
        StructureArt.Bleed bleed = art.bleed;
        assertEquals(2, bleed.woundsOf(1).length);
        assertEquals(0, bleed.woundsOf(2).length, "a painting with no wounds listed bleeds from none");
        float below = art.woundedBelow;
        assertEquals(0, StructureArt.Bleed.openWounds(3, 0.6f, below), "a body not yet wounded does not bleed");
        assertEquals(1, StructureArt.Bleed.openWounds(3, 0.49f, below), "the first wound opens as it is drawn wounded");
        int previous = 0;
        for (float d = 0.49f; d >= 0f; d -= 0.01f) {
            int open = StructureArt.Bleed.openWounds(3, d, below);
            assertTrue(open >= previous, "a wound once open stays open as the body is hurt further");
            previous = open;
        }
        assertEquals(3, StructureArt.Bleed.openWounds(3, below * StructureArt.Bleed.LAST_OPENS_AT, below));
        assertEquals(8 + 2, bleed.row(3, 90f), "kind 3 of two wraps to the second; a run at 90 degrees is the third of eight directions");
        assertEquals(0, bleed.row(0, 359f), "a run just short of a full turn is the first direction");
        assertEquals(bleed.frame(0f, 0.5f), bleed.frame(1f, 0.5f), "the loop comes round once a second at 4 fps of 4 frames");
        assertThrows(JSONException.class, () -> StructureArt.parse("s", new JSONObject("""
                { "bleed": { "sheet": "o.png", "frames": 4, "fps": 4.0, "cells": 1.0, "wounds": [ [ [0.1, 0.2] ] ] },
                  "layers": [ { "sprite": "a.png", "cells": 1.0 } ] }
                """)), "a wound is [u, v, degrees]");
    }

    @Test
    void everyShippedArtPathAndMountSpriteExistsOnDisk() {
        int withArt = 0;
        for (StructureDef structure : TurretCatalogRegistry.installed().structures()) {
            if (structure.art == null) continue;
            withArt++;
            if (structure.art.basePath != null) assertShipped(structure.id, structure.art.basePath);
            for (StructureArt.Layer layer : structure.art.layers()) {
                for (int v = 1; v <= structure.art.variants; v++) {
                    assertShipped(structure.id, layer.spritePath(v, false));
                    if (structure.art.woundedBelow > 0f) assertShipped(structure.id, layer.spritePath(v, true));
                }
            }
            if (structure.art.bleed != null) assertShipped(structure.id, structure.art.bleed.sheet());
            for (int v = 1; v <= structure.art.variants && structure.art.remainsPath != null; v++) {
                assertShipped(structure.id, structure.art.remainsPath(v));
            }
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
