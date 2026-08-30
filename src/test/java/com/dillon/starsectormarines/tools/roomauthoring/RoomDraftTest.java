package com.dillon.starsectormarines.tools.roomauthoring;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayout;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The editing model: what a click does to a room.
 *
 * <p>Asked of the draft alone. Whether the widget wired to it calls the right
 * method is a question about the widget, and standing up a window to find out
 * would test Swing rather than the rule.
 */
class RoomDraftTest {

    private static RoomDraft draft() {
        return new RoomDraft(new RoomLayout(RoomPurpose.STOCKROOM, RoomFit.STANDARD,
                RoomShape.rectangle(6, 4), List.of(), List.of(), false));
    }

    @Test
    void aCellCanBeTakenOutOfTheRoomAndPutBack() {
        RoomDraft draft = draft();
        assertTrue(draft.isFloor(2, 2));

        draft.toggleCell(2, 2);
        assertFalse(draft.isFloor(2, 2), "the cell was not removed from the footprint");
        assertFalse(draft.layout().shape().contains(2, 2), "the shape kept a removed cell");

        draft.toggleCell(2, 2);
        assertTrue(draft.isFloor(2, 2));
    }

    /**
     * Deck taken away takes what stood on it.
     *
     * <p>The alternative is a document carrying furniture outside its own room,
     * which replays as a placement the floor silently refuses — and then reads,
     * afterwards, as a fill that came out sparse for no reason anybody can see.
     */
    @Test
    void furnitureGoesWithTheDeckItStoodOn() {
        RoomDraft draft = draft();
        draft.addFixture(1, 1, "doodad.chest-1", null);
        assertEquals(1, draft.provides());

        draft.toggleCell(1, 1);
        assertEquals(0, draft.provides(), "the fixture outlived the deck under it");
    }

    /** The same rule when the whole footprint shrinks rather than one cell. */
    @Test
    void shrinkingTheRoomDropsWhatIsNowOutsideIt() {
        RoomDraft draft = draft();
        draft.addFixture(1, 1, "doodad.chest-1", null);
        draft.addFixture(5, 3, "doodad.chest-2", null);
        assertEquals(2, draft.provides());

        draft.resize(3, 2);
        assertEquals(1, draft.provides(), "a fixture was left standing outside the room");
    }

    /**
     * One control both places and corrects: clicking an occupied cell clears it.
     *
     * <p>Last thing first, because a cell legitimately carries several things —
     * paving under a fixture — and undoing one click at a time is what a person
     * clicking expects.
     */
    @Test
    void clickingAnOccupiedCellTakesAwayTheLastThingPutThere() {
        RoomDraft draft = draft();
        draft.pave(2, 2, "doodad.fl-grate-1");
        draft.addFixture(2, 2, "doodad.chest-1", Affordance.STOW);

        assertTrue(draft.removeAt(2, 2));
        assertEquals(0, draft.provides(), "the fixture was not the thing taken away");
        assertEquals(1, draft.at(2, 2).size(), "the paving under it went too");

        assertTrue(draft.removeAt(2, 2));
        assertFalse(draft.removeAt(2, 2), "an empty cell reported something removed");
    }

    /**
     * A run of circulation is not "on" a cell in the sense a click means.
     *
     * <p>Letting a click delete the room's lane because it happened to cross the
     * cell would be a very expensive misclick — a fill that seals its own room
     * is thrown away entire.
     */
    @Test
    void aClickCannotDeleteTheRoomsCirculation() {
        RoomDraft draft = draft();
        draft.reserveLane(0, 2, 6, 1);
        assertTrue(draft.isLane(3, 2));

        assertFalse(draft.removeAt(3, 2), "a click reported taking away part of a lane");
        assertTrue(draft.isLane(3, 2), "the lane was deleted by a click on one of its cells");
    }

    @Test
    void paintedDeckIsCarriedIntoTheDocument() {
        RoomDraft draft = draft();
        draft.paintGround(1, 1, 2, 2, GroundKind.STRIPED);

        assertEquals(GroundKind.STRIPED, draft.groundAt(2, 2));
        assertEquals(new LayoutOp.Ground(1, 1, 2, 2, GroundKind.STRIPED),
                draft.layout().ops().get(0));
    }

    /**
     * Capacity is the fixture count, and the draft agrees with the document it
     * produces — the figure the editor shows is the figure the ship will use.
     */
    @Test
    void theEditorsCountIsTheCountTheShipWillUse() {
        RoomDraft draft = draft();
        draft.reserveLane(0, 2, 6, 1);
        draft.pave(0, 0, "doodad.fl-grate-1");
        draft.addFixture(1, 1, "doodad.chest-1", Affordance.STOW);
        draft.addFixture(3, 1, "doodad.chest-2", null);

        assertEquals(2, draft.provides());
        assertEquals(draft.provides(), draft.layout().provides());
    }
}
