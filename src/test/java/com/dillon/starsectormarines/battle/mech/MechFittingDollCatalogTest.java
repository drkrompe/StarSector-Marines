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
    void loadsBuiltinCatalogFromDisk() throws Exception {
        MechFittingDollCatalog catalog = MechFittingDollCatalog.parse(new JSONObject(
                Files.readString(Path.of("mod", MechFittingDollCatalog.CONTENT_PATH))));
        assertNotNull(catalog);
        assertTrue(catalog.hasDoll(MechVariant.HOUND));
        assertTrue(catalog.hasDoll(MechVariant.WARDEN));
        assertTrue(catalog.hasDoll(MechVariant.BULWARK));

        // Hound: light mech with 1 Nose (2x2) and 1 Shoulder (2x2)
        DollDef hound = catalog.doll(MechVariant.HOUND);
        assertEquals(SocketType.PLATING, socket(hound, SocketId.PLATING).type(), "every doll has an armor point");
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

        DollDef warden = catalog.doll(MechVariant.WARDEN);
        assertEquals(SocketType.PLATING, socket(warden, SocketId.PLATING).type());
        assertEquals("NOSE CANNON", socket(warden, SocketId.ARMS).label());

        DollDef bulwark = catalog.doll(MechVariant.BULWARK);
        assertEquals(SocketType.PLATING, socket(bulwark, SocketId.PLATING).type());
        assertEquals("L. ARM", socket(bulwark, SocketId.LEFT_ARM).label());
        assertEquals("R. ARM", socket(bulwark, SocketId.RIGHT_ARM).label());
        assertTrue(bulwark.sockets().stream().noneMatch(socket -> socket.id() == SocketId.ARMS));
    }

    @Test
    void layoutForVariantResolvesDataAuthoredDolls() {
        MechFittingLayout houndLayout = MechFittingLayout.forVariant(MechVariant.HOUND);
        assertTrue(houndLayout.hasSocket(SocketId.PLATING));
        assertTrue(houndLayout.hasSocket(SocketId.ARMS));
        assertTrue(houndLayout.hasSocket(SocketId.LEFT_SHOULDER));
        assertFalse(houndLayout.hasSocket(SocketId.RIGHT_SHOULDER));
        assertTrue(houndLayout.hasSocket(SocketId.AUXILIARY));
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
        assertTrue(parsed.hasDoll(MechVariant.WARDEN));
        assertTrue(parsed.hasDoll(MechVariant.BULWARK));
    }

    private static SocketDef socket(DollDef doll, SocketId id) {
        for (SocketDef s : doll.sockets()) {
            if (s.id() == id) return s;
        }
        throw new AssertionError("Socket " + id + " not found");
    }
}
