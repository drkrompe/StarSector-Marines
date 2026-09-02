package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.DollDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechFittingDollCatalogTest {

    @Test
    void loadsBuiltinCatalogFromDisk() {
        MechFittingDollCatalog catalog = MechFittingDollCatalog.installed();
        assertNotNull(catalog);
        assertTrue(catalog.hasDoll(MechVariant.HOUND));
        assertTrue(catalog.hasDoll(MechVariant.SIROCCO));
        assertTrue(catalog.hasDoll(MechVariant.BULWARK));

        // Hound: light mech with 1 Nose (2x2) and 1 Shoulder (2x2)
        DollDef hound = catalog.doll(MechVariant.HOUND);
        assertEquals(4, hound.sockets().size());
        SocketDef houndNose = socket(hound, SocketId.ARMS);
        assertEquals("NOSE MOUNT", houndNose.label());
        assertEquals(2, houndNose.gridColumns());
        assertEquals(2, houndNose.gridRows());
        assertEquals(SocketType.BALLISTIC, houndNose.type());

        SocketDef houndShoulder = socket(hound, SocketId.LEFT_SHOULDER);
        assertEquals("SHOULDER", houndShoulder.label());
        assertEquals(2, houndShoulder.gridColumns());
        assertEquals(2, houndShoulder.gridRows());
        assertEquals(SocketType.MISSILE, houndShoulder.type());

        // Sirocco: 1 Nose (3x2) and 2 Shoulders (3x2)
        DollDef sirocco = catalog.doll(MechVariant.SIROCCO);
        assertEquals(6, sirocco.sockets().size());
        SocketDef siroccoNose = socket(sirocco, SocketId.ARMS);
        assertEquals("NOSE CANNON", siroccoNose.label());
        assertEquals(3, siroccoNose.gridColumns());
        assertEquals(2, siroccoNose.gridRows());

        SocketDef siroccoL = socket(sirocco, SocketId.LEFT_SHOULDER);
        assertEquals(3, siroccoL.gridColumns());
        assertEquals(2, siroccoL.gridRows());

        SocketDef siroccoR = socket(sirocco, SocketId.RIGHT_SHOULDER);
        assertEquals(3, siroccoR.gridColumns());
        assertEquals(2, siroccoR.gridRows());

        // Bulwark: Arm Assembly (3x2) and 2 Shoulders (3x2)
        DollDef bulwark = catalog.doll(MechVariant.BULWARK);
        assertEquals(6, bulwark.sockets().size());
        SocketDef bulwarkArms = socket(bulwark, SocketId.ARMS);
        assertEquals("ARM ASSEMBLY", bulwarkArms.label());
        assertEquals(3, bulwarkArms.gridColumns());
        assertEquals(2, bulwarkArms.gridRows());
    }

    @Test
    void layoutForVariantResolvesDataAuthoredDolls() {
        MechFittingLayout houndLayout = MechFittingLayout.forVariant(MechVariant.HOUND);
        assertEquals(4, houndLayout.sockets().size());
        assertTrue(houndLayout.hasSocket(SocketId.ARMS));
        assertTrue(houndLayout.hasSocket(SocketId.LEFT_SHOULDER));
        assertFalse(houndLayout.hasSocket(SocketId.RIGHT_SHOULDER));
        assertFalse(houndLayout.hasSocket(SocketId.MINI_FAB));
    }

    @Test
    void rejectsDuplicateSockets() {
        String json = """
                {
                  "schemaVersion": 1,
                  "dolls": [
                    {
                      "variant": "hound",
                      "facingDegrees": 180.0,
                      "sockets": [
                        { "id": "ARMS", "type": "BALLISTIC", "gridColumns": 2, "gridRows": 2, "anchor": [0, 0] },
                        { "id": "ARMS", "type": "BALLISTIC", "gridColumns": 2, "gridRows": 2, "anchor": [0, 0] }
                      ]
                    }
                  ]
                }
                """;
        assertThrows(IllegalArgumentException.class, () -> MechFittingDollCatalog.parse(new JSONObject(json)));
    }

    @Test
    void rejectsInvalidGridDimensions() {
        String json = """
                {
                  "schemaVersion": 1,
                  "dolls": [
                    {
                      "variant": "hound",
                      "facingDegrees": 180.0,
                      "sockets": [
                        { "id": "ARMS", "type": "BALLISTIC", "gridColumns": 4, "gridRows": 2, "anchor": [0, 0] }
                      ]
                    }
                  ]
                }
                """;
        assertThrows(IllegalArgumentException.class, () -> MechFittingDollCatalog.parse(new JSONObject(json)));
    }

    @Test
    void parsesShippedFittingDollsJsonFile() throws Exception {
        Path path = Path.of("mod", "data", "mechs", "mech-fitting-dolls.fitting.json");
        assertTrue(Files.exists(path));
        String raw = Files.readString(path);
        MechFittingDollCatalog parsed = MechFittingDollCatalog.parse(new JSONObject(raw));
        assertNotNull(parsed);
        assertTrue(parsed.hasDoll(MechVariant.HOUND));
        assertTrue(parsed.hasDoll(MechVariant.SIROCCO));
        assertTrue(parsed.hasDoll(MechVariant.BULWARK));
    }

    private static SocketDef socket(DollDef doll, SocketId id) {
        for (SocketDef s : doll.sockets()) {
            if (s.id() == id) return s;
        }
        throw new AssertionError("Socket " + id + " not found");
    }
}
