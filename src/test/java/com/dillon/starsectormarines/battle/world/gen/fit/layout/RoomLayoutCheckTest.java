package com.dillon.starsectormarines.battle.world.gen.fit.layout;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a layout would really do, told before it is saved.
 *
 * <p>Both ways an authored room fails are silent, which is the whole reason
 * this exists. A room that declares fifteen fixtures and stands up none of them
 * looks, from the outside, exactly like a room nobody has furnished yet.
 */
class RoomLayoutCheckTest {

    private static RoomLayout layout(List<LayoutOp> ops) {
        return new RoomLayout(RoomPurpose.STOCKROOM, RoomFit.STANDARD,
                RoomShape.rectangle(8, 6), ops, List.of(), false);
    }

    @Test
    void anArrangementThatFitsReportsClean() {
        RoomLayout room = layout(List.of(
                new LayoutOp.Lane(0, 3, 8, 2),
                new LayoutOp.Fixture(1, 1, "doodad.chest-1", Affordance.STOW),
                new LayoutOp.Fixture(3, 1, "doodad.chest-2", null)));

        RoomLayoutCheck.Report report = RoomLayoutCheck.replay(room);

        assertTrue(report.clean(), report.complaints().toString());
        assertEquals(2, report.declared());
        assertEquals(2, report.placed());
        assertTrue(report.circulationSurvives());
    }

    /**
     * A fixture standing on reserved circulation is refused, and the count says
     * so rather than the document's own optimism.
     *
     * <p>This is the case that produced the finding: a seeded armoury comes back
     * with six of its eight rows reserved, so an author placing across it gets a
     * room that generates far sparser than the one they drew — and nothing
     * anywhere says a word about it.
     */
    @Test
    void fixturesOnAReservedLaneAreRefusedAndCounted() {
        RoomLayout room = layout(List.of(
                new LayoutOp.Lane(0, 0, 8, 6),
                new LayoutOp.Fixture(1, 1, "doodad.chest-1", null),
                new LayoutOp.Fixture(3, 1, "doodad.chest-2", null)));

        RoomLayoutCheck.Report report = RoomLayoutCheck.replay(room);

        assertEquals(2, report.declared());
        assertEquals(0, report.placed(), "a lane accepted furniture");
        assertFalse(report.clean());
        assertTrue(String.join(" ", report.complaints()).contains("2 of 2 fixtures"),
                report.complaints().toString());
    }

    /**
     * An arrangement that seals its own room is named as such.
     *
     * <p>The ship throws away a fill that severs circulation, so the room ships
     * as bare deck — every fixture placed successfully and the room still empty.
     * A count alone would call that a success.
     */
    @Test
    void anArrangementThatSealsTheRoomIsNamed() {
        List<LayoutOp> ops = new ArrayList<>();
        ops.add(new LayoutOp.Lane(0, 0, 8, 1));
        // A wall of fixtures straight across, which is what a line of them is.
        for (int x = 0; x < 8; x++) {
            ops.add(new LayoutOp.Fixture(x, 3, "doodad.chest-1", null));
        }
        ops.add(new LayoutOp.Lane(0, 5, 8, 1));

        RoomLayoutCheck.Report report = RoomLayoutCheck.replay(layout(ops));

        assertFalse(report.circulationSurvives(),
                "a line of fixtures across the room did not read as the wall it is");
        assertTrue(String.join(" ", report.complaints()).contains("bare deck"),
                report.complaints().toString());
    }

    /**
     * An id the catalog does not have places nothing and says nothing, so the
     * check says it instead.
     */
    @Test
    void anIdTheCatalogLacksIsNamedRatherThanIgnored() {
        RoomLayout room = layout(List.of(
                new LayoutOp.Fixture(1, 1, "doodad.nonexistent-workbench", null)));

        RoomLayoutCheck.Report report = RoomLayoutCheck.replay(room);

        assertEquals(List.of("doodad.nonexistent-workbench"), report.unknownIds());
        assertEquals(0, report.placed());
        assertFalse(report.clean());
    }
}
