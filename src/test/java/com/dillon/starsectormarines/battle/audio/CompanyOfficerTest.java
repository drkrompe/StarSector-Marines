package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.audio.BattleRadioChatter.Cue;
import com.fs.starfarer.api.SoundAPI;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CompanyOfficerTest {
    private final CompanyOfficer.Line first = new CompanyOfficer.Line("one", Cue.CONTACT, "Contact received.", 3);
    private final CompanyOfficer.Line second = new CompanyOfficer.Line("two", Cue.CONTACT, "Steady, company.", 3);
    private final FakeSound sound = new FakeSound();
    private final CompanyOfficer officer = new CompanyOfficer(List.of(first, second), new Random(1), line -> {
        sound.playing = true;
        return sound;
    });

    @Test
    void answersAfterTheSquadFinishesAndAQuietGapThenThrottlesReplies() {
        officer.heard(Cue.CONTACT);
        assertNull(officer.advance(3, true, true));
        assertNull(officer.advance(0.5f, true, false));
        CompanyOfficer.Line selected = officer.advance(0.5f, true, false);
        assertNotNull(selected);
        officer.advance(8, true, false);
        assertTrue(officer.busy(), "Keep the channel until actual playback finishes");
        sound.playing = false;
        officer.advance(0, true, false);
        officer.heard(Cue.CONTACT);
        assertNull(officer.advance(1, true, false), "No new response inside the command cooldown");
        officer.advance(45, true, false);
        officer.heard(Cue.CONTACT);
        assertNotEquals(selected, officer.advance(1, true, false), "Alternate takes for repeated reports");
    }

    @Test
    void staleReportsExpireWhileBlockedOrPausedAndRoutineChatterHasNoReply() {
        officer.heard(Cue.CONTACT);
        officer.advance(13, true, true);
        assertNull(officer.advance(2, true, false));
        officer.heard(Cue.CONTACT);
        assertNull(officer.advance(2, false, false));
        officer.advance(11, false, false);
        assertNull(officer.advance(2, true, false));
        officer.heard(Cue.ACKNOWLEDGE);
        assertNull(officer.advance(2, true, false));
    }

    @Test
    void missionPriorityAndDetachStopPlaybackAndForgetPendingReplies() {
        officer.heard(Cue.CONTACT);
        officer.advance(1, true, false);
        officer.stop();
        assertFalse(sound.playing);
        assertFalse(officer.busy());
        officer.reset();
        officer.heard(Cue.CONTACT);
        officer.stop();
        assertNull(officer.advance(1, true, false));
    }

    @Test
    void completionKeepsActiveSpeechButDropsUnspokenReplies() {
        officer.heard(Cue.CONTACT);
        officer.discardPending();
        assertNull(officer.advance(1, false, false));
        officer.heard(Cue.CONTACT);
        officer.advance(1, true, false);
        officer.discardPending();
        officer.advance(1, false, false);
        assertTrue(officer.busy());
        assertTrue(sound.playing);
    }

    @Test
    void mutedAudioStillHasAReadableLifetime() {
        CompanyOfficer muted = new CompanyOfficer(List.of(first), new Random(1), line -> null);
        muted.heard(Cue.CONTACT);
        muted.advance(1, true, false);
        muted.advance(3, false, false);
        assertEquals(first, muted.current());
        muted.advance(1, false, false);
        assertNull(muted.current());
    }

    @Test
    void approvedVoiceTakesAreRegisteredWithTheSharedCommsRecipe() throws Exception {
        JSONObject catalog = new JSONObject(Files.readString(Path.of("mod", CompanyOfficer.PATH)));
        assertEquals("cuwCdnt9CKAB9XrTA1j0", catalog.getString("voiceId"));
        assertEquals("eleven_v4", catalog.getString("modelId"));
        JSONObject sounds = new JSONObject(Files.readString(Path.of("mod/data/config/sounds.json"))
                .replaceAll("(?m)#.*$", ""));
        Path sources = Path.of("asset-pipeline/src/tool/resources/audio");
        var clips = new JSONObject(Files.readString(sources.resolve("audio.json"))
                .replaceAll("(?m)^\\s*//.*$", "")).getJSONArray("clips");
        var ids = new HashSet<String>();
        for (var line : CompanyOfficer.parse(catalog)) {
            assertTrue(ids.add(line.id()));
            JSONObject receipt = new JSONObject(Files.readString(sources.resolve("company-officer/" + line.id() + ".json")));
            assertEquals(catalog.getString("voiceId"), receipt.getString("voiceId"));
            assertEquals(catalog.getString("modelId"), receipt.getString("modelId"));
            assertEquals(line.durationSeconds(), receipt.getDouble("durationSeconds"), 0.001);
            assertEquals(receipt.getString("audioSha256"), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(
                            sources.resolve("company-officer/" + line.id() + ".mp3")))));
            assertEquals("sounds/battle/company-officer/" + line.id() + ".ogg",
                    sounds.getJSONArray(line.id()).getJSONObject(0).getString("file"));
            int matches = 0;
            for (int i = 0; i < clips.length(); i++) {
                JSONObject clip = clips.getJSONObject(i);
                if (!line.id().equals(clip.getString("id"))) continue;
                matches++;
                assertEquals("comms", clip.getString("processing"));
                assertEquals("ui", clip.getString("kind"));
                assertEquals("battle/company-officer", clip.getString("out"));
                assertEquals("company-officer/" + line.id() + ".mp3", clip.getString("source"));
            }
            assertEquals(1, matches);
        }
        assertEquals(10, ids.size());
    }

    private static final class FakeSound implements SoundAPI {
        boolean playing;
        public void stop() { playing = false; }
        public boolean isPlaying() { return playing; }
        public void setVolume(float volume) { }
        public void setPitch(float pitch) { }
        public void setLocation(float x, float y) { }
    }
}
