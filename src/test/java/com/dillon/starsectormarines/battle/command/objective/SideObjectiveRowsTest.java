package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Cell;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.battle.objective.side.Ground;
import com.dillon.starsectormarines.battle.objective.side.SideField;
import com.dillon.starsectormarines.battle.objective.side.SideObjective;
import com.dillon.starsectormarines.battle.objective.side.SideObjective.Hook;
import com.dillon.starsectormarines.battle.objective.side.SideObjective.Window;
import com.dillon.starsectormarines.battle.objective.side.SideTrigger;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A side objective's row sits in the Optional section, is never tallied, and points its beacon at the place. */
class SideObjectiveRowsTest {
    private static final SideField EMPTY = new SideField() {
        @Override public boolean anyIn(Who who, Ground ground) { return false; }
        @Override public long nearest(Who who, float x, float y, float reach) { return 0L; }
        @Override public boolean onField(long id) { return false; }
        @Override public float x(long id) { return 0f; }
        @Override public float y(long id) { return 0f; }
    };

    @Test void aSideObjectiveRowIsOptionalAndBeaconedOnItsGround() {
        var well = Ground.of(List.of(new Cell(4, 4), new Cell(5, 4)));
        var job = new SideObjective("warden", new Hook("test", "offerer", null),
                new SideTrigger.Hold(SideField.Who.LEADER, well, 8f), Window.always(), null, null, null);
        Item row = SideObjectiveRows.item(job, EMPTY);
        assertTrue(row.optional());
        assertEquals("side-warden", row.id());
        assertEquals(State.ACTIVE, row.state());
        assertEquals(-1f, row.progress(), "an untouched hold shows no invented progress");
        assertEquals(new Cell(4, 4), new Cell(row.focus().x(), row.focus().y()));
        assertEquals(2, row.focus().footprint().size());
    }

    @Test void theTallyCountsOnlyTheMissionsOwnRows() {
        var required = new Item("raid", "Raid", State.COMPLETE, "", "", "", "", -1, "", List.of(), "");
        var optional = new Item("side-x", "Job", State.COMPLETE, "", "", "", "", -1, "", List.of(), "", true);
        var snapshot = new MissionObjectiveSnapshot(List.of(required, optional), List.of());
        assertEquals(List.of(required), snapshot.required());
        assertEquals(List.of(optional), snapshot.optional());
        assertFalse(required.optional());
    }

    @Test void aLapsedOrFailedJobReadsAsFailedOnThePicture() {
        assertEquals(State.FAILED, SideObjective.State.LAPSED.picture());
        assertEquals(State.FAILED, SideObjective.State.FAILED.picture());
        assertEquals(State.COMPLETE, SideObjective.State.DONE.picture());
        assertEquals(State.ACTIVE, SideObjective.State.WORKING.picture());
    }
}
