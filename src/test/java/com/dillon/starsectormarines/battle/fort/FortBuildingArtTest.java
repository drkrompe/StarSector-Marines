package com.dillon.starsectormarines.battle.fort;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a fort building adds over its kit: the laws that pick its state and register its banner, and what it refuses. */
class FortBuildingArtTest {

    private static final String ART = """
            { "banner": { "x": 0.7, "y": 3.9, "cells": 1.7 },
              "blastDoor": { "sprite": "d.png", "damaged": "e.png" } }
            """;

    @Test
    void stateFollowsWreckAndDurability() throws Exception {
        FortBuildingArt art = FortBuildingArt.parse("s", new JSONObject(ART));
        assertEquals(FortBuildingArt.State.INTACT, art.stateOf(false, 1f));
        assertEquals(FortBuildingArt.State.INTACT, art.stateOf(false, 0.6f));
        assertEquals(FortBuildingArt.State.DAMAGED, art.stateOf(false, 0.59f));
        assertEquals(FortBuildingArt.State.WRECKED, art.stateOf(true, 1f));
        assertEquals(2, art.spritePaths().size());
    }

    @Test
    void aBannerAuthoredForTheOtherDoorTurnsThroughTheFloorsCentre() throws Exception {
        FortBuildingArt bottom = FortBuildingArt.parse("s", new JSONObject(ART));
        FortBuildingArt top = FortBuildingArt.parse("s", new JSONObject(ART.replace("{ \"banner\"", "{ \"door\": \"top\", \"banner\"")));
        assertFalse(bottom.needsHalfTurn(false));
        assertTrue(bottom.needsHalfTurn(true));
        assertFalse(top.needsHalfTurn(true));
        assertArrayEquals(new float[]{6.3f, 1.1f}, FortBuildingArt.registered(true, 7f, 5f, 0.7f, 3.9f), 1e-5f);
    }

    @Test
    void refusesABadDoorSideAndAnOutOfRangeBanner() {
        assertThrows(JSONException.class, () -> FortBuildingArt.parse("s", new JSONObject(
                ART.replace("{ \"banner\"", "{ \"door\": \"left\", \"banner\""))));
        assertThrows(JSONException.class, () -> FortBuildingArt.parse("s", new JSONObject(
                ART.replace("\"cells\": 1.7", "\"cells\": 99.0"))));
        assertThrows(JSONException.class, () -> FortBuildingArt.parse("s", new JSONObject(
                "{ \"blastDoor\": { \"sprite\": \"d.png\" } }")));
    }
}
