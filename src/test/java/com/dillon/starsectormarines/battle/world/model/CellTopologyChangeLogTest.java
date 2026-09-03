package com.dillon.starsectormarines.battle.world.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a topology remembers about being edited.
 *
 * <p>The log exists so a reader holding a baked copy of the map can be told
 * which cells to redo instead of redoing all of them. Two properties carry the
 * whole contract: a mutation that changes what somebody holds across frames is
 * recorded, and one that does not is not — a log that filled up with navigation
 * and vision flags would push real edits out of a ring the reader is trying to
 * catch up on.
 */
class CellTopologyChangeLogTest {

    private static CellTopology grid() {
        return new CellTopology(8, 8);
    }

    @Test
    void aFreshTopologyHasNothingToCatchUpOn() {
        assertEquals(0L, grid().changeCount());
    }

    @Test
    void everyMutationThatMovesATileIsRecorded() {
        CellTopology topology = grid();

        topology.setGroundKind(3, 2, CellTopology.GroundKind.RUBBLE);
        assertEquals(1L, topology.changeCount());
        assertEquals(topology.index(3, 2), topology.changedCellAt(0));

        topology.setWall(4, 2, true);
        assertEquals(topology.index(4, 2), topology.changedCellAt(1));

        topology.setWallDirMask(4, 2, CellTopology.WALL_DIR_N);
        assertEquals(topology.index(4, 2), topology.changedCellAt(2));

        topology.setNatureOverlayIndex(5, 5, 3);
        assertEquals(topology.index(5, 5), topology.changedCellAt(3));

        assertEquals(4L, topology.changeCount());
    }

    /**
     * A tag no resident consumer reads leaves the log alone. A parked vehicle and
     * a shelf run are facts navigation and their own props read every frame
     * anyway, and a hundred of them a second would evict the breach a reader
     * actually has to redraw.
     */
    @Test
    void aTagNoResidentConsumerReadsIsNotAChange() {
        CellTopology topology = grid();
        topology.setTag(1, 1, CellTopology.Tag.FIXTURE, true);
        topology.setTag(1, 1, CellTopology.Tag.VEHICLE, true);
        assertEquals(0L, topology.changeCount());

        topology.setTag(1, 1, CellTopology.Tag.CROSSWALK, true);
        assertEquals(1L, topology.changeCount(), "stripes are painted on the ground");
    }

    /**
     * A caved-in roof is recorded, because the resident consumer it moves is the
     * relief composite's height field rather than the ground mesh. Left out, a
     * building would go on shadowing ground its roof no longer covers for as long
     * as the battle lasted — which is precisely the invalidation the per-frame
     * rebuild used to buy by redrawing the whole map.
     */
    @Test
    void aCavedRoofAndAnApertureAreChangesToTheHeightField() {
        CellTopology topology = grid();
        topology.setRoofDestroyed(2, 3, true);
        assertEquals(1L, topology.changeCount());
        assertEquals(topology.index(2, 3), topology.changedCellAt(0));

        topology.setWindow(4, 4, true);
        assertEquals(2L, topology.changeCount());
        assertEquals(topology.index(4, 4), topology.changedCellAt(1));
    }

    /**
     * The log is a ring, and the count is not. A reader compares its own mark
     * against the count to know whether what it missed is still in there, so the
     * count has to keep rising past the ring's size while the slots wrap.
     */
    @Test
    void theCountOutrunsTheRingItWritesInto() {
        CellTopology topology = grid();
        int capacity = topology.changeLogCapacity();
        assertTrue(capacity > 0);

        for (int i = 0; i < capacity + 5; i++) {
            topology.setGroundKind(i % 8, (i / 8) % 8, CellTopology.GroundKind.STREET);
        }

        assertEquals(capacity + 5L, topology.changeCount());
        // The newest entry is readable at its own sequence; the oldest has been
        // written over by it, which is exactly the condition a reader detects by
        // comparing the gap against the capacity.
        long newest = topology.changeCount() - 1;
        int expected = topology.index((capacity + 4) % 8, ((capacity + 4) / 8) % 8);
        assertEquals(expected, topology.changedCellAt(newest));
        assertEquals(topology.changedCellAt(newest), topology.changedCellAt(newest - capacity));
    }
}
