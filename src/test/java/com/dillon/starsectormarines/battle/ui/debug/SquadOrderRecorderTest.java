package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.ui.debug.SquadOrderRecorder.Layer;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the recorder's tallying core on synthetic label streams. The
 * question the capture exists to answer — "is this squad flapping or did it
 * change its mind once?" — is answered entirely by the run bookkeeping, so
 * that is what is asked directly rather than through a played battle.
 */
class SquadOrderRecorderTest {

    private static Map<Layer, String> goal(String value) {
        Map<Layer, String> labels = new EnumMap<>(Layer.class);
        labels.put(Layer.GOAL, value);
        return labels;
    }

    @Test
    void flappingValuesSeparateFromASingleMindChange() throws Exception {
        SquadOrderRecorder flapping = new SquadOrderRecorder(7, 10);
        for (int i = 0; i < 10; i++) {
            flapping.sample(i, goal(i % 2 == 0 ? "A" : "B"));
        }
        SquadOrderRecorder settled = new SquadOrderRecorder(7, 10);
        for (int i = 0; i < 10; i++) {
            settled.sample(i, goal(i < 5 ? "A" : "B"));
        }

        // Same five frames apiece in both; only the run counts tell them apart.
        assertEquals(9, changes(flapping));
        assertEquals(1, changes(settled));
        assertEquals(5, runs(flapping, "A"));
        assertEquals(1, runs(settled, "A"));
        assertEquals(1, longestRun(flapping, "A"));
        assertEquals(5, longestRun(settled, "A"));
        assertEquals(5, frames(flapping, "A"));
        assertEquals(5, frames(settled, "A"));
    }

    @Test
    void aFrameOnWhichTheSimDidNotAdvanceIsNotASample() throws Exception {
        SquadOrderRecorder recorder = new SquadOrderRecorder(1, 10);
        recorder.sample(400, goal("HOLD"));
        recorder.sample(400, goal("ADVANCE"));
        recorder.sample(400, goal("HOLD"));

        assertEquals(1, recorder.frames());
        assertEquals(0, changes(recorder));
    }

    @Test
    void theWindowStopsAcceptingSamplesOnceFull() throws Exception {
        SquadOrderRecorder recorder = new SquadOrderRecorder(1, 3);
        for (int i = 0; i < 10; i++) recorder.sample(i, goal("HOLD"));

        assertTrue(recorder.isComplete());
        assertEquals(3, recorder.frames());
    }

    @Test
    void aLostSquadCompletesTheCaptureWithWhatItHas() throws Exception {
        SquadOrderRecorder recorder = new SquadOrderRecorder(1, 100);
        recorder.sample(0, goal("HOLD"));
        assertFalse(recorder.isComplete());

        recorder.noteSquadLost();

        assertTrue(recorder.isComplete());
        assertEquals(1, recorder.frames());
    }

    @Test
    void aLayerWithNoLabelThisFrameStillTallies() throws Exception {
        SquadOrderRecorder recorder = new SquadOrderRecorder(1, 2);
        recorder.sample(0, goal("HOLD"));
        recorder.sample(1, goal("HOLD"));

        // ACTION was never labelled; it should read as one steady "no order"
        // run rather than as a hole in the capture.
        JSONObject action = layer(recorder, Layer.ACTION);
        assertEquals(1, action.getInt("distinctValues"));
        assertEquals(0, action.getInt("changes"));
        assertEquals(2, action.getJSONArray("values").getJSONObject(0).getInt("frames"));
    }

    @Test
    void jsonLeadsWithTheDominantValueAndLogsEachTransition() throws Exception {
        SquadOrderRecorder recorder = new SquadOrderRecorder(4, 10);
        for (int i = 0; i < 8; i++) recorder.sample(i, goal("HOLD"));
        recorder.sample(8, goal("ADVANCE"));
        recorder.sample(9, goal("HOLD"));

        JSONObject root = recorder.toJson();
        assertEquals(4, root.getInt("squadId"));
        assertEquals(10, root.getInt("frames"));
        assertEquals(9, root.getInt("tickSpan"));

        JSONArray values = layer(recorder, Layer.GOAL).getJSONArray("values");
        assertEquals("HOLD", values.getJSONObject(0).getString("value"));
        assertEquals(9, values.getJSONObject(0).getInt("frames"));
        assertEquals(2, values.getJSONObject(0).getInt("runs"));
        assertEquals(8, values.getJSONObject(0).getInt("longestRun"));

        JSONArray transitions = root.getJSONArray("transitions");
        assertEquals(2, transitions.length());
        assertEquals("goal", transitions.getJSONObject(0).getString("layer"));
        assertEquals("HOLD", transitions.getJSONObject(0).getString("from"));
        assertEquals("ADVANCE", transitions.getJSONObject(0).getString("to"));
        assertEquals(8, transitions.getJSONObject(0).getInt("tick"));
    }

    /**
     * The reported order has to be the one the squad is carrying out. A player
     * order overrides the commander's assignment without replacing it, and a
     * capture that read the commander's field named {@code CLEAR_ZONE} for
     * every frame of a squad walking to a cell the player clicked — confidently
     * and wrongly, with the player's order appearing nowhere.
     */
    @Test
    void aLivePlayerOrderIsTheReportedOrderAndTheMissionStaysReadable() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = sim.spawn(new EntitySpec("Marine", Faction.MARINE,
                UnitType.MARINE, 2, 2).squad(squadId));
        squad.assignedObjective = ObjectiveAssignment.clearZone(squadId, 194);

        assertEquals("CLEAR_ZONE zone:194",
                SquadOrderRecorder.executingAssignmentLabel(squad));

        sim.getSquadMoveOrderService().requestMove(squadId, 15, 9);
        sim.getSquadMoveOrderSystem().tick(sim);

        assertNotNull(squad.playerTacticalOrder(),
                "the move order must have been accepted, or this measures nothing");
        assertEquals("player ATTACK_MOVE cell:15,9",
                SquadOrderRecorder.executingAssignmentLabel(squad));
        assertEquals("CLEAR_ZONE zone:194",
                SquadOrderRecorder.assignmentLabel(squad.assignedObjective),
                "the mission assignment underneath stays inspectable");
    }

    /**
     * The layers have to be wired to those two readings, which is the half of
     * the defect a label test alone cannot see: the labels were always right,
     * the {@code order} and {@code assignment} layers just asked the wrong
     * field for them.
     */
    @Test
    void theOrderLayersReportThePlayerOrderAndTheMissionLayerTheAssignment()
            throws Exception {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = sim.spawn(new EntitySpec("Marine", Faction.MARINE,
                UnitType.MARINE, 2, 2).squad(squadId));
        squad.assignedObjective = ObjectiveAssignment.clearZone(squadId, 194);
        sim.getSquadMoveOrderService().requestMove(squadId, 15, 9);
        sim.getSquadMoveOrderSystem().tick(sim);

        SquadOrderRecorder recorder = new SquadOrderRecorder(squadId, 1);
        recorder.sample(squad, sim);

        assertEquals("player ATTACK_MOVE cell:15,9",
                onlyValue(recorder, Layer.ASSIGNMENT));
        assertEquals("CLEAR_ZONE zone:194", onlyValue(recorder, Layer.MISSION));
        assertTrue(onlyValue(recorder, Layer.ORDER)
                        .startsWith("player ATTACK_MOVE cell:15,9 »"),
                "the composite order line leads with what is being executed");
    }

    private static String onlyValue(SquadOrderRecorder recorder, Layer layer)
            throws Exception {
        JSONArray values = layer(recorder, layer).getJSONArray("values");
        assertEquals(1, values.length());
        return values.getJSONObject(0).getString("value");
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(20, 12);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
    }

    // --- helpers: read one layer's tallies back out of the JSON view ---

    private static JSONObject layer(SquadOrderRecorder recorder, Layer layer) throws Exception {
        return recorder.toJson().getJSONObject("layers").getJSONObject(layer.key);
    }

    private static int changes(SquadOrderRecorder recorder) throws Exception {
        return layer(recorder, Layer.GOAL).getInt("changes");
    }

    private static int frames(SquadOrderRecorder recorder, String value) throws Exception {
        return valueRow(recorder, value).getInt("frames");
    }

    private static int runs(SquadOrderRecorder recorder, String value) throws Exception {
        return valueRow(recorder, value).getInt("runs");
    }

    private static int longestRun(SquadOrderRecorder recorder, String value) throws Exception {
        return valueRow(recorder, value).getInt("longestRun");
    }

    private static JSONObject valueRow(SquadOrderRecorder recorder, String value) throws Exception {
        JSONArray values = layer(recorder, Layer.GOAL).getJSONArray("values");
        for (int i = 0; i < values.length(); i++) {
            JSONObject row = values.getJSONObject(i);
            if (value.equals(row.getString("value"))) return row;
        }
        throw new AssertionError("no tally for " + value);
    }
}
