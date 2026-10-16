package com.dillon.starsectormarines.campaign.nexerelin;

import exerelin.campaign.customstart.CustomStart;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nexerelin's custom-start list names the Second Wind start, and the start knows where to put the fleet. */
class SecondWindNexStartTest {

    @Test
    void customStartEntryNamesThisClassAndOnlyTheCorvusSector() throws Exception {
        JSONArray starts = new JSONObject(Files.readString(Path.of("mod/data/config/exerelin/customStarts.json")))
                .getJSONArray("starts");
        assertEquals(1, starts.length());
        JSONObject entry = starts.getJSONObject(0);
        Class<?> start = Class.forName(entry.getString("className"), false, getClass().getClassLoader());
        assertEquals(SecondWindNexStart.class, start);
        assertTrue(CustomStart.class.isAssignableFrom(start));
        assertEquals(-1, entry.getInt("randomSector"), "the opening assumes Corvus");
        assertTrue(Files.readString(Path.of("mod/data/campaign/rules.csv")).contains(entry.getString("desc")),
                "the description matches the vanilla start's tooltip");
    }

    @Test
    void theFleetIsPlacedAtTheOpeningsYardElseJangala() {
        assertEquals("corvus_iv", SecondWindNexStart.startEntityId("corvus_iv"));
        assertEquals("jangala", SecondWindNexStart.startEntityId(null));
        assertEquals("jangala", SecondWindNexStart.startEntityId(""));
    }
}
