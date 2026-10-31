package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.battle.power.WalkingBarrage.Line;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The barrage row counts down off the frozen timetable: to the opening, to each lift, to the next incendiary line. */
class HeartChecklistTest {
    // An incendiary opening, a high-explosive line, then a second incendiary line.
    private static final List<Line> LINES = List.of(new Line(0.3f, 40f, 50f, 420), new Line(0.36f, 90f, 40f, 0),
            new Line(0.52f, 130f, 40f, 420));
    private static final List<int[]> CELLS = List.of(new int[]{30, 50}, new int[]{36, 50}, new int[]{52, 50});
    private static final int[] BATTERY = {2, 50};

    @Test void beforeTheOpeningItCountsDownToTheIncendiaryShootAndBeaconsTheFirstLine() {
        Item row = HeartChecklist.barrage(LINES, 8f, 4, 4, State.ACTIVE, CELLS, BATTERY);
        assertEquals(State.ACTIVE, row.state());
        assertEquals("Incendiary barrage opens in 0:32", row.phase());
        assertEquals("heart-barrage-line", row.focusMarkerId());
        assertEquals(30, row.focus().x());
        assertEquals(2, row.markers().size(), "the line and the battery");
    }

    @Test void whileALineFiresItCountsDownToTheLiftAndToTheNextIncendiaryLine() {
        Item firingHe = HeartChecklist.barrage(LINES, 100f, 4, 4, State.ACTIVE, CELLS, BATTERY);
        assertEquals("Line 2 firing, lifts in 0:30", firingHe.phase());
        assertEquals(36, firingHe.focus().x());
        assertTrue(firingHe.progressText().endsWith("next incendiary (line 3) in 0:30"), firingHe.progressText());
        // The last incendiary line firing: nothing left to light, so the tally is the guns alone.
        Item last = HeartChecklist.barrage(LINES, 140f, 4, 4, State.ACTIVE, CELLS, BATTERY);
        assertTrue(last.phase().startsWith("Line 3 firing incendiary"), last.phase());
        assertFalse(last.progressText().contains("next incendiary"), last.progressText());
    }

    @Test void onceTheGunsAreDoneTheRowStaysOpenOnTheBatteryWithNoLineBeacon() {
        Item row = HeartChecklist.barrage(LINES, 200f, 3, 4, State.ACTIVE, CELLS, BATTERY);
        assertEquals(State.ACTIVE, row.state());
        assertEquals("Barrage ended", row.phase());
        assertEquals(1, row.markers().size());
        assertEquals(1f, row.progress());
    }

    @Test void anOverrunBatteryFailsTheRowAndALowOneSaysSo() {
        Item lost = HeartChecklist.barrage(LINES, 100f, 0, 4, State.FAILED, CELLS, null);
        assertEquals(State.FAILED, lost.state());
        assertTrue(lost.markers().isEmpty());
        assertNotEquals("", HeartChecklist.barrage(LINES, 100f, 2, 4, State.ACTIVE, CELLS, BATTERY).blocker());
        assertEquals("", HeartChecklist.barrage(LINES, 100f, 3, 4, State.ACTIVE, CELLS, BATTERY).blocker());
    }
}
