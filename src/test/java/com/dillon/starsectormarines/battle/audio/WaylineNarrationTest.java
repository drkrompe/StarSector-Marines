package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class WaylineNarrationTest {
    @Test
    void everyPackagedLineHasARecordedTakeAndSoundRegistration() throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of("mod", WaylineNarration.PATH)));
        WaylineNarration catalog = WaylineNarration.parse(root);
        JSONObject sounds = new JSONObject(Files.readString(Path.of("mod/data/config/sounds.json")).replaceAll("(?m)#.*$", ""));
        Path sources = Path.of("asset-pipeline/src/tool/resources/audio");
        var clips = new JSONObject(Files.readString(sources.resolve("audio.json")).replaceAll("(?m)^\\s*//.*$", "")).getJSONArray("clips");
        var ids = new HashSet<String>();
        for (var line : catalog.lines()) {
            assertTrue(ids.add(line.id()));
            assertEquals("sounds/story/wayline/" + line.id() + ".ogg",
                    sounds.getJSONArray(line.id()).getJSONObject(0).getString("file"));
            JSONObject receipt = new JSONObject(Files.readString(sources.resolve("wayline/" + line.id() + ".json")));
            assertEquals(root.getString("voiceId"), receipt.getString("voiceId"));
            assertEquals(root.getString("modelId"), receipt.getString("modelId"));
            assertEquals(receipt.getDouble("durationSeconds"), line.durationSeconds(), 0.001);
            assertTrue(Files.size(sources.resolve("wayline/" + line.id() + ".mp3")) > 1000);
            int matching = 0;
            for (int i = 0; i < clips.length(); i++) {
                JSONObject clip = clips.getJSONObject(i);
                if (!line.id().equals(clip.getString("id"))) continue;
                matching++;
                assertEquals("ui", clip.getString("kind"));
                assertEquals("wayline/" + line.id() + ".mp3", clip.getString("source"));
                assertEquals("story/wayline", clip.getString("out"));
            }
            assertEquals(1, matching);
            for (String prior : line.supersedes()) assertNotNull(catalog.find(line.missionId(), prior));
        }
        assertEquals(14, ids.size());
    }

    @Test
    void chapterIdentityRequiresStorySource() {
        for (OpeningOperationKind kind : OpeningOperationKind.values()) {
            assertEquals(kind.missionId, WaylineNarration.storyMissionId(
                    Mission.builder().id(kind.missionId).source(MissionSource.STORY).build()));
            assertNull(WaylineNarration.storyMissionId(
                    Mission.builder().id(kind.missionId).source(MissionSource.RECURRING).build()));
        }
        assertNull(WaylineNarration.storyMissionId(null));
    }
}
