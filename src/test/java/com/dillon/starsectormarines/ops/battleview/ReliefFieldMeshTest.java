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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The resident relief field's bookkeeping, asked without a GPU.
 *
 * <p>{@link ReliefFieldMesh#catchUp} is the half of a sync that decides what has
 * to be re-resolved and where it goes. Every rule worth pinning there is
 * arithmetic — which cells a change re-resolves, whether a slot is reused, when
 * the change log has been outrun — and a test that needed a driver to ask about
 * arithmetic would be a test nobody runs. What a real driver actually draws from
 * these buffers is {@code ReliefFieldGlEvidence}'s question.
 */
class ReliefFieldMeshTest {

    /** A derived atlas is only ever an identity and a size to the field. */
    private static SpriteAPI sheet(String name, float px) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getWidth", "getHeight" -> px;
            case "getTextureWidth", "getTextureHeight" -> 1f;
            case "toString" -> name;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> defaultOf(method.getReturnType());
        };
        return (SpriteAPI) Proxy.newProxyInstance(ReliefFieldMeshTest.class.getClassLoader(),
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

    private static final SpriteAPI ROADS = sheet("road_height", 64f);
    private static final SpriteAPI FLOORS = sheet("floors_height", 64f);

    /**
     * A tiny field: walls stand tall and carry no derived art, floors sample one
     * atlas, and a street samples another. Records what it was asked about, which
     * is how the patch rules are measured.
     */
    private static final class Field implements ReliefFieldMesh.CellResolver {
        private final CellTopology topology;
        final List<Integer> resolved = new ArrayList<>();
        float wallMeters = 1f;
        int floorSrcY;

        Field(CellTopology topology) {
            this.topology = topology;
        }

        @Override
        public void resolve(int x, int y, ReliefFieldMesh.CellSink sink) {
            resolved.add(y * topology.getWidth() + x);
            if (topology.isWall(x, y)) {
                sink.solid(wallMeters, 0.5f, 0f, 0f);
                return;
            }
            if (topology.getGroundKind(x, y) == CellTopology.GroundKind.STREET) {
                sink.quad(ROADS, 64, 64, 0, 0, 32, 32, 0.125f, 0f, 0f, 1f);
                return;
            }
            sink.quad(FLOORS, 64, 64, 32, floorSrcY, 32, 32, 0.125f, 0f, 0f, 1f);
        }
    }

    /** Floor everywhere, a wall along the top and bottom rows, one street cell. */
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
        topology.setGroundKind(1, 1, CellTopology.GroundKind.STREET);
        return topology;
    }

    private static int cell(int x, int y) {
        return y * 4 + x;
    }

    /**
     * Every cell of a relief field owns a slot — including the ones with no
     * derived art, which is the difference from the colour mesh. A field with a
     * hole in it would read as the clear colour, and the clear is the ground
     * datum rather than that cell's own height.
     */
    @Test
    void aBakeSeatsEveryCellAndGivesEachAtlasOneBuffer() {
        CellTopology topology = grid();
        Field field = new Field(topology);
        ReliefFieldMesh mesh = new ReliefFieldMesh("height");

        assertTrue(mesh.catchUp(topology, field));

        assertEquals(16, field.resolved.size(), "a bake resolves the whole grid once");
        assertEquals(16, mesh.residentQuads(), "every cell of a field is written, art or not");
        assertEquals(3, mesh.bucketCount(), "the untextured one, plus one per atlas");
        assertTrue(mesh.cellIsTextured(cell(2, 2)));
        assertFalse(mesh.cellIsTextured(cell(2, 0)), "a wall carries macro height and no art");
        assertNull(mesh.cellUv(cell(2, 0)));
        assertTrue(mesh.isServing(topology));
        assertFalse(mesh.isBehind(topology));
    }

    /**
     * Position is in cell units and UV is the cell's own sub-rectangle. The first
     * is what lets the camera be a modelview transform; the second is what keeps
     * a cell reading its own texels of a sliced atlas.
     */
    @Test
    void aSlotHoldsItsOwnSquareAndItsOwnCornerOfTheAtlas() {
        CellTopology topology = grid();
        ReliefFieldMesh mesh = new ReliefFieldMesh("height");
        mesh.catchUp(topology, new Field(topology));

        assertArrayEquals(new float[]{2f, 2f, 3f, 2f, 3f, 3f, 2f, 3f},
                mesh.cellPos(cell(2, 2)), 0f);
        // srcX 32 of 64 is half across; srcY 0 with height 32 is the top half,
        // which in GL's bottom-up V is 1.0 down to 0.5.
        assertArrayEquals(new float[]{0.5f, 0.5f, 1f, 0.5f, 1f, 1f, 0.5f, 1f},
                mesh.cellUv(cell(2, 2)), 1e-6f);
    }

    /**
     * The material signal is carried as the 0..255 channels the RGBA8 field
     * actually holds, so what is stored is what the composite will read back.
     */
    @Test
    void aCellCarriesItsMaterialSignalAtTheTargetsOwnPrecision() {
        CellTopology topology = grid();
        ReliefFieldMesh mesh = new ReliefFieldMesh("height");
        Field field = new Field(topology);
        field.wallMeters = 1f;
        mesh.catchUp(topology, field);

        assertArrayEquals(new int[]{255, 128, 0, 0}, mesh.cellColor(cell(2, 0)));
        assertArrayEquals(new int[]{32, 0, 0, 255}, mesh.cellColor(cell(2, 2)));
    }

    /**
     * The patch's whole claim. One cell changed re-resolves that cell and the
     * four it touches — the neighbours because a derived atlas rectangle is
     * chosen from what stands beside it — and nothing else is looked at again.
     */
    @Test
    void oneChangedCellRedrawsItselfAndItsNeighbours() {
        CellTopology topology = grid();
        Field field = new Field(topology);
        ReliefFieldMesh mesh = new ReliefFieldMesh("height");
        mesh.catchUp(topology, field);

        field.resolved.clear();
        topology.setGroundKind(2, 2, CellTopology.GroundKind.RUBBLE);
        assertTrue(mesh.isBehind(topology));
        assertTrue(mesh.catchUp(topology, field));

        assertEquals(List.of(cell(2, 2), cell(1, 2), cell(3, 2), cell(2, 1), cell(2, 3)),
                field.resolved);
    }

    /**
     * A roof caving in reaches the field, which is the invalidation the per-frame
     * rebuild existed for. Left out of the change log it would be a building
     * shadowing ground it no longer covers for the rest of the battle.
     */
    @Test
    void aCavedRoofIsACellTheFieldRedraws() {
        CellTopology topology = grid();
        Field field = new Field(topology);
        ReliefFieldMesh mesh = new ReliefFieldMesh("height");
        mesh.catchUp(topology, field);

        field.resolved.clear();
        topology.setRoofDestroyed(2, 2, true);
        assertTrue(mesh.isBehind(topology));
        mesh.catchUp(topology, field);

        assertTrue(field.resolved.contains(cell(2, 2)),
                "the cell whose roof went is redrawn");
        assertEquals(5, field.resolved.size(), "and its four neighbours, and nothing else");
    }

    /** A cell that keeps its atlas keeps its slot; a patch writes over it in place. */
    @Test
    void aRedrawnCellReusesTheSlotItAlreadyHas() {
        CellTopology topology = grid();
        Field field = new Field(topology);
        ReliefFieldMesh mesh = new ReliefFieldMesh("height");
        mesh.catchUp(topology, field);
        int quadsBefore = mesh.residentQuads();
        float[] before = mesh.cellUv(cell(2, 2));

        field.floorSrcY = 32;
        topology.setGroundKind(2, 2, CellTopology.GroundKind.RUBBLE);
        mesh.catchUp(topology, field);

        assertEquals(quadsBefore, mesh.residentQuads(), "no slot was added for a redraw");
        assertEquals(0, mesh.freeSlotCount(), "and none was given back");
        float[] after = mesh.cellUv(cell(2, 2));
        assertNotNull(after);
        assertTrue(before[1] != after[1], "the cell now reads a different row of its atlas");
    }

    /**
     * A cell that moves between buckets — losing its derived art when a wall goes
     * up over it — hands its slot back, and the next cell that needs one in that
     * buffer takes it rather than growing the buffer. A slot standing empty in
     * one buffer is the price of a cell having moved to another, and it is a
     * fixed price: the same cell moving back does not cost a second one.
     */
    @Test
    void aSlotGivenUpIsHandedToTheNextCellThatNeedsOne() {
        CellTopology topology = grid();
        Field field = new Field(topology);
        ReliefFieldMesh mesh = new ReliefFieldMesh("height");
        mesh.catchUp(topology, field);

        topology.setWall(2, 2, true);
        mesh.catchUp(topology, field);
        assertEquals(1, mesh.freeSlotCount(), "the floor atlas has a slot spare");
        assertEquals(17, mesh.residentQuads(), "and the cell now sits in the untextured buffer");
        assertFalse(mesh.cellIsTextured(cell(2, 2)));

        topology.setWall(2, 2, false);
        mesh.catchUp(topology, field);
        assertEquals(17, mesh.residentQuads(), "the floor atlas reused the slot it had spare");
        assertEquals(1, mesh.freeSlotCount(), "and gave the untextured one back in its place");
        assertTrue(mesh.cellIsTextured(cell(2, 2)));
    }

    /**
     * A reader that has fallen further behind than the topology remembers cannot
     * be patched, so it bakes again. The log is a ring: past its capacity the
     * earliest changes have been overwritten and a partial catch-up would leave
     * cells standing at a height that is no longer there.
     */
    @Test
    void fallingFurtherBehindThanTheLogRemembersBakesAgain() {
        CellTopology topology = grid();
        Field field = new Field(topology);
        ReliefFieldMesh mesh = new ReliefFieldMesh("height");
        mesh.catchUp(topology, field);

        for (int i = 0; i <= topology.changeLogCapacity(); i++) {
            topology.setGroundKind(2, 2, i % 2 == 0
                    ? CellTopology.GroundKind.RUBBLE : CellTopology.GroundKind.INDOOR);
        }
        field.resolved.clear();
        mesh.catchUp(topology, field);

        assertEquals(16, field.resolved.size(), "the whole grid was resolved again");
    }

    /** A settled world is re-resolved not at all, which is the whole point. */
    @Test
    void aWorldThatHasNotChangedCostsNothing() {
        CellTopology topology = grid();
        Field field = new Field(topology);
        ReliefFieldMesh mesh = new ReliefFieldMesh("height");
        mesh.catchUp(topology, field);

        field.resolved.clear();
        for (int frame = 0; frame < 10; frame++) {
            assertTrue(mesh.catchUp(topology, field));
        }
        assertTrue(field.resolved.isEmpty(), "ten frames of a settled map resolve no cells");
    }

    /** A different battle is a different grid, and nothing of the last one survives. */
    @Test
    void anotherBattleIsBakedFromScratch() {
        CellTopology first = grid();
        ReliefFieldMesh mesh = new ReliefFieldMesh("height");
        mesh.catchUp(first, new Field(first));

        CellTopology second = new CellTopology(2, 2);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) second.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
        }
        Field field = new Field(second);
        assertTrue(mesh.catchUp(second, field));

        assertEquals(4, field.resolved.size());
        assertEquals(4, mesh.residentQuads());
        assertTrue(mesh.isServing(second));
        assertFalse(mesh.isServing(first));
    }

    /** Nothing is resident for a world that does not exist. */
    @Test
    void thereIsNoFieldWithoutATopology() {
        ReliefFieldMesh mesh = new ReliefFieldMesh("height");
        assertFalse(mesh.catchUp(null, (x, y, sink) -> {}));
        assertFalse(mesh.isServing(null));
        assertTrue(mesh.isBehind(null));
    }

    /** A resolver that names no sheet writes the cell flat rather than leaving a hole. */
    @Test
    void aCellWithNoSheetIsStillWritten() {
        CellTopology topology = new CellTopology(2, 1);
        ReliefFieldMesh mesh = new ReliefFieldMesh("normal");
        mesh.catchUp(topology, (x, y, sink) -> sink.quad(null, 1, 1, 0, 0, 1, 1, 0.5f, 0.5f, 1f, 1f));
        assertEquals(1, mesh.bucketCount());
        assertEquals(2, mesh.residentQuads());
        assertArrayEquals(new int[]{128, 128, 255, 255}, mesh.cellColor(0));
    }
}
