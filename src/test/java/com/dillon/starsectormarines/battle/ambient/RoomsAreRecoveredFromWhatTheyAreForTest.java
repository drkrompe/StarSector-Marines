package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A room on a surface map is the run of floor stamped with one purpose, and
 * nothing else is.
 *
 * <p>The ambient model wants an extent, a purpose and an id from a place, and a
 * generated map already knows all three per cell — so a job site is recovered
 * rather than published, and no generator has to remember to hand its rooms on.
 * What that leaves to get right is the boundary, and it is wrong in two
 * directions at once.
 *
 * <p>Too <b>wide</b>, and two rooms become one: three barrack blocks off one
 * hallway would be a single posting holding three watches, and the work in each
 * would be offered to somebody standing in another. Too <b>generous about what
 * counts</b>, and a corridor becomes a place — which is worse, because a
 * corridor runs the length of a building and would swallow everything either
 * end of it.
 */
class RoomsAreRecoveredFromWhatTheyAreForTest {

    private static final int W = 24;
    private static final int H = 12;

    /**
     * Two rooms of one purpose, either side of a hall, are two rooms.
     *
     * <p>The case a fortress actually generates: its program owes three barrack
     * blocks, and they are three postings rather than one barracks of ninety-six
     * cells that happens to be in three pieces.
     */
    @Test
    void twoRoomsOfOnePurposeAreTwoSites() {
        CellTopology topology = new CellTopology(W, H);
        stamp(topology, RoomPurpose.BARRACKS, 2, 2, 6, 6);
        stamp(topology, RoomPurpose.CORRIDOR, 8, 2, 2, 6);
        stamp(topology, RoomPurpose.BARRACKS, 10, 2, 6, 6);

        List<RoomSite> sites = RoomSite.findAll(topology, W, H);

        assertEquals(2, sites.size(), "the two blocks came back as " + sites.size() + " site(s)");
        for (RoomSite site : sites) {
            assertEquals(RoomPurpose.BARRACKS, site.purpose());
            assertEquals(36, site.cellCount(), "a six-by-six block holds 36 cells");
        }
        assertFalse(sites.get(0).id() == sites.get(1).id(),
                "two rooms were given one id, so their work shares a claim group");
    }

    /**
     * A hall between them is not a place, however much floor it covers.
     *
     * <p>Left in the map's own vocabulary rather than dropped from it: a
     * corridor is a real purpose that a renderer and a fill both read. What it
     * is not is somewhere with business of its own, and a site made of one would
     * join every room it touches into a single posting.
     */
    @Test
    void aCorridorIsNotAPlace() {
        CellTopology topology = new CellTopology(W, H);
        stamp(topology, RoomPurpose.CORRIDOR, 2, 2, 20, 4);

        assertTrue(RoomSite.findAll(topology, W, H).isEmpty(),
                "a hallway was published as somewhere to work");
    }

    /**
     * A site covers its own cells and no others.
     *
     * <p>Which is the whole of what an extent is for: it decides which of the
     * map's published work is <em>here</em>. A room answering for a cell outside
     * itself would take the neighbouring room's benches onto its own watch bill.
     */
    @Test
    void aSiteCoversItsOwnCellsAndNoOthers() {
        CellTopology topology = new CellTopology(W, H);
        stamp(topology, RoomPurpose.VEHICLE_BAY, 4, 3, 8, 5);

        List<RoomSite> sites = RoomSite.findAll(topology, W, H);
        assertEquals(1, sites.size());
        RoomSite bay = sites.get(0);

        assertTrue(bay.contains(4, 3), "the bay does not contain its own corner");
        assertTrue(bay.contains(11, 7), "the bay does not contain its own far corner");
        assertFalse(bay.contains(3, 3), "the bay answers for the cell outside its wall");
        assertFalse(bay.contains(12, 7), "the bay answers for the cell outside its wall");

        assertTrue(bay.contains(bay.centreX(), bay.centreY()),
                "the bay's own middle is not in it");
    }

    /** Finding the room a berth stands in is finding the room that covers it. */
    @Test
    void aBerthIsFoundInTheRoomThatCoversIt() {
        CellTopology topology = new CellTopology(W, H);
        stamp(topology, RoomPurpose.VEHICLE_BAY, 4, 3, 8, 5);
        stamp(topology, RoomPurpose.ARMORY, 14, 3, 6, 5);

        List<RoomSite> sites = RoomSite.findAll(topology, W, H);

        RoomSite found = RoomSite.covering(sites, 6, 4);
        assertNotNull(found, "a cell inside the bay resolved to no room at all");
        assertEquals(RoomPurpose.VEHICLE_BAY, found.purpose());
        assertEquals(RoomPurpose.ARMORY, RoomSite.covering(sites, 16, 4).purpose());
        assertNull(RoomSite.covering(sites, 13, 4), "the gap between them is somebody's room");
    }

    private static void stamp(CellTopology topology, RoomPurpose purpose,
                              int x, int y, int spanX, int spanY) {
        for (int dx = 0; dx < spanX; dx++) {
            for (int dy = 0; dy < spanY; dy++) {
                topology.setRoomPurpose(x + dx, y + dy, purpose);
            }
        }
    }
}
