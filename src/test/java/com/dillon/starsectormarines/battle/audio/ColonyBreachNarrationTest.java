package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ColonyBreachNarrationTest {
    private static final String MISSION = "story_colony_breach";

    private static WaylineNarration catalog() throws Exception {
        return WaylineNarration.parse(new JSONObject(Files.readString(Path.of("mod/data/marines/colony_breach_narration.json"))));
    }

    @Test void everyLineIsABreachBeatWithARegisteredRecording() throws Exception {
        String manifest = Files.readString(Path.of("asset-pipeline/src/tool/resources/audio/audio.json"));
        String sounds = Files.readString(Path.of("mod/data/config/sounds.json"));
        var lines = catalog().lines();
        assertFalse(lines.isEmpty());
        for (var line : lines) {
            assertTrue(line.event().startsWith("colony_breach."), line.event());
            assertEquals(MISSION, line.missionId());
            assertTrue(manifest.contains("\"id\": \"" + line.id() + "\""), "audio.json: " + line.id());
            assertTrue(sounds.contains("\"" + line.id() + "\":"), "sounds.json: " + line.id());
        }
    }

    @Test void aBeatPlaysItsLineAndAnObsoleteOneIsSuperseded() throws Exception {
        var log = new StoryBeatLog();
        log.emitOnce(1, "colony_breach.bore_erupted_1", StoryBeatLog.Audience.PLAYER);
        log.emitOnce(2, "colony_breach.bore_collapsed_1", StoryBeatLog.Audience.PLAYER);
        var queue = new StoryNarrationQueue(catalog(), MISSION);
        queue.advance(0.1f, log.events(), false);
        var line = queue.poll();
        assertEquals("colony_breach.bore_collapsed_1", line.event());
        assertNull(queue.poll(), "the eruption call is superseded by the collapse");
    }

    @Test void observerBeatsAreSilent() throws Exception {
        var log = new StoryBeatLog();
        log.emitOnce(1, "colony_breach.tremor", StoryBeatLog.Audience.OBSERVER_ONLY);
        var queue = new StoryNarrationQueue(catalog(), MISSION);
        queue.advance(0.1f, log.events(), false);
        assertNull(queue.poll());
    }
}
