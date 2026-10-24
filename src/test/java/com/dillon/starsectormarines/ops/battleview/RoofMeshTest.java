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
 * The bookkeeping half of {@link RoofMesh}: which slots a cell holds, what a
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
    private static final int SLOTS = RoofTiles.MAX_QUADS;

    @Test
    void aBakeGivesEveryRoofCellItsOwnSlots() {
        CellTopology topology = new CellTopology(W, H);
        Buildings buildings = town(topology);
        RoofMesh mesh = new RoofMesh();

        assertTrue(mesh.catchUp(topology, buildings, SHEET, 256, 256, tiles(topology), 1f));
        assertEquals(2 * 12 * SLOTS, mesh.residentQuads(), "two blocks of twelve cells");
        assertEquals(2 * 12, mesh.lastResolvedCells(), "a bake resolves the lot");

        int slot = mesh.slotOf(2, 2);
        assertTrue(slot >= 0, "a roofed cell holds slots");
        assertArrayEqualsExactly(new float[]{2, 2, 3, 2, 3, 3, 2, 3}, mesh.positionAt(slot));
        assertArrayEqualsExactly(new float[8], mesh.positionAt(slot + 1),
                "a slot the cell does not draw is degenerate");
        // A cell nobody roofed holds none, and nothing was written for it.
        assertEquals(-1, mesh.slotOf(0, 0));
    }

    @Test
    void aWallTheRoofIsCarriedOnJoinsItsBuildingsRun() {
        CellTopology topology = new CellTopology(W, H);
        Buildings buildings = town(topology);
        topology.setWall(5, 2, true);
        RoofMesh mesh = new RoofMesh();
        mesh.catchUp(topology, buildings, SHEET, 256, 256, tiles(topology), 1f);

        assertEquals((2 * 12 + 1) * SLOTS, mesh.residentQuads());
        int wall = mesh.slotOf(5, 2);
        assertTrue(wall > mesh.slotOf(4, 3) && wall < mesh.slotOf(10, 5),
                "the wall is baked after its building's floor and before the next building");
        buildings.get(1).currentAlpha = 0.5f;
        mesh.catchUp(topology, buildings, SHEET, 256, 256, tiles(topology), 1f);
        assertEquals(0.5f, mesh.colourAt(wall)[3], 1e-6f, "and fades with it");
    }

    @Test
    void aPieceIsCutFromItsFrameTurnedOntoTheCell() {
        CellTopology topology = new CellTopology(W, H);
        Buildings buildings = town(topology);
        RoofMesh mesh = new RoofMesh();
        // Frame 1 of row 2, a quarter turn, over the south half of the cell.
        mesh.catchUp(topology, buildings, SHEET, 250, 200, (b, x, y, sink) ->
                sink.quad(1, 2, 1, 0f, 0f, 1f, 0.5f), 1f);
        float[] uv = mesh.uvAt(mesh.slotOf(2, 2));
        // Frames are 25x50 px, row 2 spans sheet rows 100..150. A clockwise turn takes the frame's south-east
        // corner to the cell's south-west, and its north-east to the cell's south-east; the north-east corner of
        // the cut is half way along the frame's north edge. Each is held half a texel inside the frame.
        assertEquals(49.5f / 250f, uv[0], 1e-5f);
        assertEquals(1f - 149.5f / 200f, uv[1], 1e-5f);
        assertEquals(49.5f / 250f, uv[2], 1e-5f);
        assertEquals(1f - 100.5f / 200f, uv[3], 1e-5f);
        assertEquals(37.5f / 250f, uv[4], 1e-5f);
        assertEquals(1f - 100.5f / 200f, uv[5], 1e-5f);
    }

    @Test
    void aBrokenRoofReResolvesItsCellAndTheCellsBesideItOnly() {
        CellTopology topology = new CellTopology(W, H);
        Buildings buildings = town(topology);
        RoofMesh mesh = new RoofMesh();
        mesh.catchUp(topology, buildings, SHEET, 256, 256, tiles(topology), 1f);

        int caved = mesh.slotOf(2, 2);
        int farther = mesh.slotOf(4, 3);
        float[] before = mesh.positionAt(farther);

        topology.setRoofDestroyed(2, 2, true);
        assertTrue(mesh.catchUp(topology, buildings, SHEET, 256, 256, tiles(topology), 1f));

        assertEquals(9, mesh.lastResolvedCells(),
                "the cell and its eight neighbours, all roofed here: a fallen wall "
                        + "changes the cover beside it, and nothing further");
        assertArrayEqualsExactly(new float[8], mesh.positionAt(caved),
                "the resolver's answer for the broken cell is what the slot holds");
        assertArrayEqualsExactly(before, mesh.positionAt(farther));
    }

    @Test
    void aFadeIsPerBuildingAndOnlyWhenItHasMoved() {
        CellTopology topology = new CellTopology(W, H);
        Buildings buildings = town(topology);
        RoofMesh mesh = new RoofMesh();
        mesh.catchUp(topology, buildings, SHEET, 256, 256, tiles(topology), 1f);

        int inFirst = mesh.slotOf(2, 2);
        int inSecond = mesh.slotOf(11, 6);
        assertEquals(1f, mesh.colourAt(inFirst)[3], 1e-6f);
        assertEquals(1f, mesh.colourAt(inSecond)[3], 1e-6f);
        // The tint is the building's own, so a slot baked against the wrong
        // building would be visible here rather than only on a screen.
        assertNotEquals(mesh.colourAt(inFirst)[0], mesh.colourAt(inSecond)[0]);

        buildings.get(1).currentAlpha = 0.4f;
        mesh.catchUp(topology, buildings, SHEET, 256, 256, tiles(topology), 1f);
        assertEquals(0.4f, mesh.colourAt(inFirst)[3], 1e-6f);
        assertEquals(0.4f, mesh.colourAt(inFirst + SLOTS - 1)[3], 1e-6f, "every slot of the cell");
        assertEquals(1f, mesh.colourAt(inSecond)[3], 1e-6f,
                "the other building's roof did not move");
        assertEquals(0, mesh.lastResolvedCells(),
                "a fade is a colour patch, not a re-resolve of any cell's geometry");
    }

    @Test
    void aRoofFadedPastTheStreamsThresholdIsNotDrawnAtAll() {
        CellTopology topology = new CellTopology(W, H);
        Buildings buildings = town(topology);
        RoofMesh mesh = new RoofMesh();
        mesh.catchUp(topology, buildings, SHEET, 256, 256, tiles(topology), 1f);

        // The command stream skips a building at or under 0.01, so a resident
        // quad carrying that alpha would put a couple of levels of roof over a
        // revealed interior the streamed path leaves clear.
        buildings.get(1).currentAlpha = 0.008f;
        mesh.catchUp(topology, buildings, SHEET, 256, 256, tiles(topology), 1f);
        assertEquals(0f, mesh.colourAt(mesh.slotOf(2, 2))[3], 1e-6f);
    }

    @Test
    void theFramesOwnFadeMultipliesIntoEveryRoof() {
        CellTopology topology = new CellTopology(W, H);
        Buildings buildings = town(topology);
        RoofMesh mesh = new RoofMesh();
        mesh.catchUp(topology, buildings, SHEET, 256, 256, tiles(topology), 0.5f);
        assertEquals(0.5f, mesh.colourAt(mesh.slotOf(2, 2))[3], 1e-6f);
        assertEquals(0.5f, mesh.colourAt(mesh.slotOf(11, 6))[3], 1e-6f);
    }

    // ---- the world -----------------------------------------------------------

    /** Two 4x3 blocks with different tints, their cells marked in the topology. */
    private static Buildings town(CellTopology topology) {
        Buildings buildings = new Buildings();
        buildings.add(block(topology, 1, 1, 1, 0.9f, 0.5f, 0.4f));
        buildings.add(block(topology, 2, 10, 5, 0.4f, 0.6f, 0.9f));
        return buildings;
    }

    private static Building block(CellTopology topology, int id, int x0, int y0, float r, float g, float b) {
        int w = 4;
        int h = 3;
        int[] cellsX = new int[w * h];
        int[] cellsY = new int[w * h];
        int at = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                cellsX[at] = x0 + x;
                cellsY[at] = y0 + y;
                topology.setBuildingId(x0 + x, y0 + y, id);
                at++;
            }
        }
        return new Building(id, BuildingKind.RESIDENTIAL,
                x0, x0 + w - 1, y0, y0 + h - 1, cellsX, cellsY, r, g, b);
    }

    /** One whole-cell piece per cell from a coordinate-hashed frame, and nothing over a broken roof. */
    private static RoofMesh.RoofResolver tiles(CellTopology topology) {
        return (building, x, y, sink) -> {
            if (topology.isRoofDestroyed(x, y)) return;
            int column = Math.floorMod(x * 7 + y * 3, RoofTiles.COLUMNS);
            int row = Math.floorMod(x * 3 + y * 5, RoofStyle.COUNT);
            sink.quad(column, row, 0, 0f, 0f, 1f, 1f);
        };
    }

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
        assertArrayEqualsExactly(expected, actual, null);
    }

    private static void assertArrayEqualsExactly(float[] expected, float[] actual, String message) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], actual[i], 1e-6f, message);
    }
}
