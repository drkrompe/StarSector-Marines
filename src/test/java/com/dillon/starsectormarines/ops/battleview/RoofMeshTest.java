package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bookkeeping half of {@link RoofMesh}: which slot a cell holds, what a
 * change re-resolves, and when a fade is a patch and when it is nothing at all.
 *
 * <p>{@code catchUp} is deliberately separable from the upload so this can be
 * asked without a GL context — every rule here is arithmetic, and a test that
 * needed a GPU to ask about arithmetic would not be run.
 * {@code RoofMeshGlEvidence} asks the other half: whether a driver draws the
 * same picture out of it.
 */
class RoofMeshTest {

    private static final int W = 20;
    private static final int H = 12;
    private static final int TILE = 32;

    @Test
    void aBakeGivesEveryRoofCellItsOwnSlot() {
        CellTopology topology = new CellTopology(W, H);
        Buildings buildings = town();
        RoofMesh mesh = new RoofMesh();

        assertTrue(mesh.catchUp(topology, buildings, SHEET, 256, 256, TILES, 1f));
        assertEquals(2 * 12, mesh.residentQuads(), "two blocks of twelve cells");
        assertEquals(2 * 12, mesh.lastResolvedCells(), "a bake resolves the lot");

        int slot = mesh.slotOf(2, 2);
        assertTrue(slot >= 0, "a roofed cell holds a slot");
        assertArrayEqualsExactly(new float[]{2, 2, 3, 2, 3, 3, 2, 3}, mesh.positionAt(slot));
        // A cell nobody roofed holds none, and nothing was written for it.
        assertEquals(-1, mesh.slotOf(0, 0));
    }

    @Test
    void aCavedRoofGivesUpItsCellAndOnlyThatCell() {
        CellTopology topology = new CellTopology(W, H);
        Buildings buildings = town();
        RoofMesh mesh = new RoofMesh();
        mesh.catchUp(topology, buildings, SHEET, 256, 256, TILES, 1f);

        int caved = mesh.slotOf(3, 3);
        int neighbour = mesh.slotOf(4, 3);
        float[] before = mesh.positionAt(neighbour);

        topology.setRoofDestroyed(3, 3, true);
        assertTrue(mesh.catchUp(topology, buildings, SHEET, 256, 256, TILES, 1f));

        assertEquals(1, mesh.lastResolvedCells(),
                "one cell changed, and a roof tile is picked by hashing its own "
                        + "coordinates — so unlike a ground autotile it re-resolves "
                        + "nothing beside it");
        assertArrayEqualsExactly(new float[]{0, 0, 0, 0, 0, 0, 0, 0}, mesh.positionAt(caved));
        assertArrayEqualsExactly(before, mesh.positionAt(neighbour));
    }

    @Test
    void aFadeIsPerBuildingAndOnlyWhenItHasMoved() {
        CellTopology topology = new CellTopology(W, H);
        Buildings buildings = town();
        RoofMesh mesh = new RoofMesh();
        mesh.catchUp(topology, buildings, SHEET, 256, 256, TILES, 1f);

        int inFirst = mesh.slotOf(2, 2);
        int inSecond = mesh.slotOf(11, 6);
        assertEquals(1f, mesh.colourAt(inFirst)[3], 1e-6f);
        assertEquals(1f, mesh.colourAt(inSecond)[3], 1e-6f);
        // The tint is the building's own, so a slot baked against the wrong
        // building would be visible here rather than only on a screen.
        assertNotEquals(mesh.colourAt(inFirst)[0], mesh.colourAt(inSecond)[0]);

        buildings.get(1).currentAlpha = 0.4f;
        mesh.catchUp(topology, buildings, SHEET, 256, 256, TILES, 1f);
        assertEquals(0.4f, mesh.colourAt(inFirst)[3], 1e-6f);
        assertEquals(1f, mesh.colourAt(inSecond)[3], 1e-6f,
                "the other building's roof did not move");
        assertEquals(0, mesh.lastResolvedCells(),
                "a fade is a colour patch, not a re-resolve of any cell's geometry");
    }

    @Test
    void aRoofFadedPastTheStreamsThresholdIsNotDrawnAtAll() {
        CellTopology topology = new CellTopology(W, H);
        Buildings buildings = town();
        RoofMesh mesh = new RoofMesh();
        mesh.catchUp(topology, buildings, SHEET, 256, 256, TILES, 1f);

        // The command stream skips a building at or under 0.01, so a resident
        // quad carrying that alpha would put a couple of levels of brick over a
        // revealed interior the streamed path leaves clear.
        buildings.get(1).currentAlpha = 0.008f;
        mesh.catchUp(topology, buildings, SHEET, 256, 256, TILES, 1f);
        assertEquals(0f, mesh.colourAt(mesh.slotOf(2, 2))[3], 1e-6f);
    }

    @Test
    void theFramesOwnFadeMultipliesIntoEveryRoof() {
        CellTopology topology = new CellTopology(W, H);
        Buildings buildings = town();
        RoofMesh mesh = new RoofMesh();
        mesh.catchUp(topology, buildings, SHEET, 256, 256, TILES, 0.5f);
        assertEquals(0.5f, mesh.colourAt(mesh.slotOf(2, 2))[3], 1e-6f);
        assertEquals(0.5f, mesh.colourAt(mesh.slotOf(11, 6))[3], 1e-6f);
    }

    // ---- the world -----------------------------------------------------------

    /** Two 4x3 blocks with different tints. */
    private static Buildings town() {
        Buildings buildings = new Buildings();
        buildings.add(block(1, 1, 1, 0.9f, 0.5f, 0.4f));
        buildings.add(block(2, 10, 5, 0.4f, 0.6f, 0.9f));
        return buildings;
    }

    private static Building block(int id, int x0, int y0, float r, float g, float b) {
        int w = 4;
        int h = 3;
        int[] cellsX = new int[w * h];
        int[] cellsY = new int[w * h];
        int at = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                cellsX[at] = x0 + x;
                cellsY[at] = y0 + y;
                at++;
            }
        }
        return new Building(id, BuildingKind.RESIDENTIAL,
                x0, x0 + w - 1, y0, y0 + h - 1, cellsX, cellsY, r, g, b);
    }

    /** One tile per cell, at a coordinate-hashed position on a 8x8 sheet of them. */
    private static final RoofMesh.RoofResolver TILES = (x, y, sink) -> {
        int col = Math.floorMod(x * 7 + y * 3, 8);
        int row = Math.floorMod(x * 3 + y * 5, 8);
        sink.quad(col * TILE, row * TILE, TILE, TILE);
    };

    /** One sheet for the whole suite: a different handle would be a different mesh. */
    private static final SpriteAPI SHEET = sheet();

    /** A sheet that answers only what a UV asks. */
    private static SpriteAPI sheet() {
        return (SpriteAPI) Proxy.newProxyInstance(
                SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTextureWidth", "getTextureHeight" -> 1f;
                    case "getWidth", "getHeight" -> 256f;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "sheet";
                    default -> null;
                });
    }

    private static void assertArrayEqualsExactly(float[] expected, float[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], actual[i], 1e-6f);
    }
}
