package com.dillon.starsectormarines.battle.world.gen.fit.layout;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The document reads back as what was written, and refuses loudly what it
 * cannot understand.
 *
 * <p>Round-tripping is the property the editor depends on: the workbench seeds a
 * document from a procedural fitting and the author corrects it, so a write that
 * did not parse back to the same arrangement would be losing work silently.
 */
class RoomLayoutJsonTest {

    private static final String DOCUMENT = """
            {
              "purpose": "STOCKROOM",
              "fit": "STANDARD",
              "shape": ["####", "####", "##.."],
              "hookups": [ { "slots": [ { "cells": [[0, -1], [1, -1]] } ] } ],
              "ops": [
                { "op": "lane",    "x": 0, "y": 1, "spanX": 4, "spanY": 1 },
                { "op": "ground",  "x": 0, "y": 0, "spanX": 4, "spanY": 1, "kind": "STRIPED" },
                { "op": "pave",    "x": 0, "y": 0, "id": "doodad.fl-grate-1" },
                { "op": "fixture", "x": 1, "y": 0, "id": "doodad.chest-1", "affordance": "STOW" },
                { "op": "fixture", "x": 2, "y": 0, "id": "doodad.chest-2" },
                { "op": "task",    "x": 1, "y": 1, "affordance": "STOW", "fixtureX": 1, "fixtureY": 0 },
                { "op": "berth",   "x": 0, "y": 2, "spanX": 2, "spanY": 1, "facing": "NORTH" },
                { "op": "closed",  "x": 3, "y": 1, "spanX": 1, "spanY": 1 }
              ]
            }
            """;

    @Test
    void aDocumentParsesToTheArrangementItDescribes() throws Exception {
        RoomLayout layout = RoomLayoutJson.parse(new JSONObject(DOCUMENT));

        assertEquals(RoomPurpose.STOCKROOM, layout.purpose());
        assertEquals(RoomFit.STANDARD, layout.fit());
        assertEquals(8, layout.ops().size());
        assertEquals(2, layout.provides(), "capacity is the fixture count");

        // The footprint is a mask, so the notched corner has to survive.
        assertTrue(layout.shape().contains(0, 2));
        assertTrue(!layout.shape().contains(3, 2), "the authored notch was filled in");

        assertEquals(new LayoutOp.Ground(0, 0, 4, 1, GroundKind.STRIPED), layout.ops().get(1));
        assertEquals(new LayoutOp.Fixture(1, 0, "doodad.chest-1", Affordance.STOW),
                layout.ops().get(3));
        assertEquals(new LayoutOp.Fixture(2, 0, "doodad.chest-2", null),
                layout.ops().get(4), "a fixture that affords nothing was given an affordance");
        assertEquals(new LayoutOp.Berth(0, 2, 2, 1, Gantry.Facing.NORTH), layout.ops().get(6));

        assertEquals(1, layout.hookups().size(), "the authored door was dropped");
    }

    @Test
    void whatIsWrittenParsesBackToTheSameArrangement() throws Exception {
        RoomLayout original = RoomLayoutJson.parse(new JSONObject(DOCUMENT));
        RoomLayout again = RoomLayoutJson.parse(
                new JSONObject(RoomLayoutJson.write(original).toString()));

        assertEquals(original.purpose(), again.purpose());
        assertEquals(original.fit(), again.fit());
        assertEquals(original.ops(), again.ops(), "a step changed on the way through");
        assertEquals(original.shape(), again.shape(), "the footprint changed on the way through");
        assertEquals(original.hookups().size(), again.hookups().size());
    }

    /**
     * Every refusal names what was wrong.
     *
     * <p>The failure being guarded is silent rather than loud: a document that
     * half-parses furnishes a room half way, and the result reads as a fill
     * defect rather than as a typo in a file.
     */
    @Test
    void whatCannotBeUnderstoodIsRefusedWithItsReason() {
        assertMessageContains("no shape", "{ \"purpose\": \"STOCKROOM\" }", "shape");
        assertMessageContains("unknown purpose",
                "{ \"purpose\": \"WARDROOM\", \"shape\": [\"##\"] }", "WARDROOM");
        assertMessageContains("unknown op",
                "{ \"purpose\": \"STOCKROOM\", \"shape\": [\"##\"],"
                        + " \"ops\": [ { \"op\": \"sprinkle\" } ] }", "sprinkle");
        assertMessageContains("fixture with no id",
                "{ \"purpose\": \"STOCKROOM\", \"shape\": [\"##\"],"
                        + " \"ops\": [ { \"op\": \"fixture\", \"x\": 0, \"y\": 0 } ] }", "id");
        assertMessageContains("unknown affordance",
                "{ \"purpose\": \"STOCKROOM\", \"shape\": [\"##\"], \"ops\": [ { \"op\": \"fixture\","
                        + " \"x\": 0, \"y\": 0, \"id\": \"doodad.chest-1\","
                        + " \"affordance\": \"LOITER\" } ] }", "LOITER");
    }

    private static void assertMessageContains(String what, String json, String expected) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> RoomLayoutJson.parse(new JSONObject(json)), what + " was accepted");
        assertTrue(failure.getMessage().contains(expected),
                what + ": the refusal did not say '" + expected + "' — " + failure.getMessage());
    }
}
