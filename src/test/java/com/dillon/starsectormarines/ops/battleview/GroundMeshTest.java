package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The resident ground's bookkeeping, asked without a GPU.
 *
 * <p>{@link GroundMesh#catchUp} is the half of a sync that decides what has to
 * be re-resolved and where it goes; every rule worth pinning lives there and is
 * arithmetic. A four-by-four grid is enough to show all of it, and a test that
 * needed a context to ask would be a test nobody runs.
 */
class GroundMeshTest {

    /** A sheet is only ever an identity and a size to the mesh. */
    private static SpriteAPI sheet(String name, float px) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getWidth", "getHeight" -> px;
            case "getTextureWidth", "getTextureHeight" -> 1f;
            case "toString" -> name;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> defaultOf(method.getReturnType());
        };
        return (SpriteAPI) Proxy.newProxyInstance(GroundMeshTest.class.getClassLoader(),
                new Class<?>[]{SpriteAPI.class}, handler);
    }

    private static Object defaultOf(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == long.class) return 0L;
        if (type == void.class) return null;
        return 0;
    }

    private static final SpriteAPI FLOORS = sheet("floors", 64f);
    private static final SpriteAPI WALLS = sheet("walls", 64f);

    /**
     * The ground of a tiny world: walls round the edge on one sheet, floor on
     * another, and one cell in the middle that paints a colour instead of a
     * tile. Records what it was asked about, which is how the patch rules are
     * measured.
     */
    private static final class Terrain implements GroundMesh.CellResolver {
        private final CellTopology topology;
        final List<Integer> resolved = new ArrayList<>();
        int floorSrcY = 0;

        Terrain(CellTopology topology) {
            this.topology = topology;
        }

        @Override
        public void resolve(int x, int y, GroundMesh.CellSink sink) {
            resolved.add(y * topology.getWidth() + x);
            if (topology.getGroundKind(x, y) == CellTopology.GroundKind.VOID) {
                sink.fill(0x123456);
                return;
            }
            if (topology.isWall(x, y)) sink.quad(WALLS, 0, 0, 32, 32);
            else sink.quad(FLOORS, 32, floorSrcY, 32, 32);
        }
    }

    /** Floor everywhere, a wall along the top and bottom rows, one cell of nothing. */
    private static CellTopology grid() {
        CellTopology topology = new CellTopology(4, 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                topology.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
            }
        }
        for (int x = 0; x < 4; x++) {
            topology.setWall(x, 0, true);
            topology.setWall(x, 3, true);
        }
        topology.setGroundKind(1, 1, CellTopology.GroundKind.VOID);
        return topology;
    }

    private static int cell(int x, int y) {
        return y * 4 + x;
    }

    @Test
    void aBakeGivesEverySheetOneBufferAndEveryTiledCellOneSlot() {
        CellTopology topology = grid();
        Terrain terrain = new Terrain(topology);
        GroundMesh mesh = new GroundMesh();

        assertTrue(mesh.catchUp(topology, terrain));

        assertEquals(16, terrain.resolved.size(), "a bake resolves the whole grid once");
        assertEquals(2, mesh.bucketCount(), "one buffer per sheet, not per cell");
        assertEquals(15, mesh.residentQuads(), "every cell but the filled one owns a slot");
        assertEquals(1, mesh.fillCellCount());
        assertEquals(0x123456, mesh.fillRgb(cell(1, 1)));
        assertTrue(mesh.isServing(topology));
    }

    /**
     * Position is in cell units and UV is the cell's own sub-rectangle. Both
     * matter: the first is what lets the camera be a modelview transform, and
     * the second is what lets a merged-run's wrap problem not exist.
     */
    @Test
    void aSlotHoldsItsOwnSquareAndItsOwnCornerOfTheAtlas() {
        CellTopology topology = grid();
        GroundMesh mesh = new GroundMesh();
        mesh.catchUp(topology, new Terrain(topology));

        assertArrayEquals(new float[]{2f, 2f, 3f, 2f, 3f, 3f, 2f, 3f},
                mesh.cellPos(cell(2, 2)), 0f);
        // srcX 32 of 64 is half across; srcY 0 with height 32 is the top half,
        // which in GL's bottom-up V is 1.0 down to 0.5.
        assertArrayEquals(new float[]{0.5f, 0.5f, 1f, 0.5f, 1f, 1f, 0.5f, 1f},
                mesh.cellUv(cell(2, 2)), 1e-6f);
        assertNull(mesh.cellUv(cell(1, 1)), "a filled cell holds no slot");
    }

    /**
     * The patch's whole claim. One cell changed re-resolves that cell and the
     * four it touches — the neighbours because an autotile frame is chosen from
     * what is beside it — and nothing else in the map is looked at again.
     */
    @Test
    void oneChangedCellRedrawsItselfAndItsNeighbours() {
        CellTopology topology = grid();
        Terrain terrain = new Terrain(topology);
        GroundMesh mesh = new GroundMesh();
        mesh.catchUp(topology, terrain);

        terrain.resolved.clear();
        topology.setGroundKind(2, 2, CellTopology.GroundKind.RUBBLE);
        assertTrue(mesh.catchUp(topology, terrain));

        assertEquals(List.of(cell(2, 2), cell(1, 2), cell(3, 2), cell(2, 1), cell(2, 3)),
                terrain.resolved);
    }

    /** A cell that keeps its sheet keeps its slot; a patch writes over it in place. */
    @Test
    void aRedrawnCellReusesTheSlotItAlreadyHas() {
        CellTopology topology = grid();
        Terrain terrain = new Terrain(topology);
        GroundMesh mesh = new GroundMesh();
        mesh.catchUp(topology, terrain);
        int quadsBefore = mesh.residentQuads();
        float[] before = mesh.cellUv(cell(2, 2));

        terrain.floorSrcY = 32;
        topology.setGroundKind(2, 2, CellTopology.GroundKind.RUBBLE);
        mesh.catchUp(topology, terrain);

        assertEquals(quadsBefore, mesh.residentQuads(), "no slot was added for a redraw");
        assertEquals(0, mesh.freeSlotCount(), "and none was given back");
        float[] after = mesh.cellUv(cell(2, 2));
        assertNotNull(after);
        assertTrue(before[1] != after[1], "the cell now draws a different row of its sheet");
    }

    /**
     * A cell that stops drawing a tile hands its slot back, and the next cell
     * that needs one in that buffer takes it rather than growing the buffer.
     */
    @Test
    void aSlotGivenUpIsHandedToTheNextCellThatNeedsOne() {
        CellTopology topology = grid();
        Terrain terrain = new Terrain(topology);
        GroundMesh mesh = new GroundMesh();
        mesh.catchUp(topology, terrain);
        int quads = mesh.residentQuads();

        // (2,2) stops being a tile; (1,1) starts being one.
        topology.setGroundKind(2, 2, CellTopology.GroundKind.VOID);
        mesh.catchUp(topology, terrain);
        assertEquals(1, mesh.freeSlotCount());
        assertEquals(2, mesh.fillCellCount(), "the fill list follows the change");

        topology.setGroundKind(1, 1, CellTopology.GroundKind.INDOOR);
        mesh.catchUp(topology, terrain);
        assertEquals(quads, mesh.residentQuads(), "the freed slot was reused");
        assertEquals(0, mesh.freeSlotCount());
        assertEquals(1, mesh.fillCellCount());
    }

    /**
     * A reader that has fallen further behind than the topology remembers cannot
     * be patched, so it bakes again. The log is a ring: past its capacity the
     * earliest changes have been overwritten and a partial catch-up would leave
     * cells drawing something that is no longer there.
     */
    @Test
    void fallingFurtherBehindThanTheLogRemembersBakesAgain() {
        CellTopology topology = grid();
        Terrain terrain = new Terrain(topology);
        GroundMesh mesh = new GroundMesh();
        mesh.catchUp(topology, terrain);

        for (int i = 0; i <= topology.changeLogCapacity(); i++) {
            topology.setGroundKind(2, 2, i % 2 == 0
                    ? CellTopology.GroundKind.RUBBLE : CellTopology.GroundKind.INDOOR);
        }
        terrain.resolved.clear();
        mesh.catchUp(topology, terrain);

        assertEquals(16, terrain.resolved.size(), "the whole grid was resolved again");
    }

    /** A different battle is a different grid, and nothing of the last one survives. */
    @Test
    void anotherBattleIsBakedFromScratch() {
        CellTopology first = grid();
        GroundMesh mesh = new GroundMesh();
        mesh.catchUp(first, new Terrain(first));

        CellTopology second = new CellTopology(2, 2);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) second.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
        }
        Terrain terrain = new Terrain(second);
        assertTrue(mesh.catchUp(second, terrain));

        assertEquals(4, terrain.resolved.size());
        assertEquals(4, mesh.residentQuads());
        assertEquals(2, mesh.gridWidth());
        assertTrue(mesh.isServing(second));
        assertTrue(!mesh.isServing(first));
    }

    /**
     * The frame that bakes a battle's ground is not a frame the mesh can draw,
     * because the collector asks before the drain has run. That is one frame of
     * the ordinary stream per battle, and it is what keeps the collector free of
     * GL.
     */
    @Test
    void aMeshThatHasNotBakedYetIsNotServing() {
        CellTopology topology = grid();
        GroundMesh mesh = new GroundMesh();
        assertTrue(!mesh.isServing(topology));
        mesh.catchUp(topology, new Terrain(topology));
        assertTrue(mesh.isServing(topology));
    }

    /** Nothing is resident for a world that does not exist. */
    @Test
    void thereIsNoGroundWithoutATopology() {
        GroundMesh mesh = new GroundMesh();
        assertTrue(!mesh.catchUp(null, (x, y, sink) -> {}));
        assertTrue(!mesh.isServing(null));
    }

    /** The sheets are what the buckets are keyed by, and identity is the key. */
    @Test
    void twoCellsOnOneSheetShareOneBuffer() {
        CellTopology topology = new CellTopology(2, 1);
        GroundMesh mesh = new GroundMesh();
        mesh.catchUp(topology, (x, y, sink) -> sink.quad(FLOORS, 0, 0, 32, 32));
        assertEquals(1, mesh.bucketCount());
        assertEquals(2, mesh.residentQuads());
    }
}
