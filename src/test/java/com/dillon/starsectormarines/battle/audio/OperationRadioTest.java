package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.audio.BattleRadioChatter.Cue;
import com.dillon.starsectormarines.battle.audio.OperationRadio.Actor;
import com.dillon.starsectormarines.battle.audio.OperationRadio.AssignmentKey;
import com.dillon.starsectormarines.battle.audio.OperationRadio.Catalog;
import com.dillon.starsectormarines.battle.audio.OperationRadio.Line;
import com.dillon.starsectormarines.battle.audio.OperationRadio.Observation;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.fs.starfarer.api.SoundAPI;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class OperationRadioTest {
    private final FakeSound sound = new FakeSound();
    private final OperationRadio radio = new OperationRadio(catalog(), new Random(3), turn -> {
        sound.playing = true;
        return sound;
    });

    private static Catalog catalog() {
        Map<String, Line> lines = new HashMap<>();
        List<Actor> actors = new ArrayList<>();
        lines.put("order", new Line("order", "command", "Move to your assigned position.", 1));
        for (String name : List.of("kestrel", "hammer", "viper")) {
            for (String turn : List.of("call", "ack", "readback")) {
                String id = name + "_" + turn;
                lines.put(id, new Line(id, turn.equals("call") ? "command" : name, id, 1));
            }
            Map<Cue, String> reports = new EnumMap<>(Cue.class);
            for (Cue cue : Cue.values()) {
                String id = name + "_" + cue.name();
                lines.put(id, new Line(id, name, id, 1));
                reports.put(cue, id);
            }
            actors.add(new Actor(name, name, name + "_call", name + "_ack", name + "_readback", reports));
        }
        return new Catalog(actors, Map.of(AssignmentKind.ATTACK_MOVE, "order"), lines);
    }

    private static Observation order(int squad, int cell) {
        return new Observation(squad, AssignmentKey.of(ObjectiveAssignment.attackMove(squad, cell, 10)));
    }

    private void finish() {
        sound.playing = false;
        assertNull(radio.advance(1.3f, true, false, false));
    }

    @Test
    void handshakeWaitsForActualPlaybackAndKeepsOneActorAcrossItsTurns() {
        radio.observe(List.of(order(7, 20)));
        var call = radio.beginExchange();
        String actor = call.line().id().replace("_call", "");
        assertEquals("COMPANY COMMAND", call.speaker());
        assertNull(radio.advance(3, true, false, false));
        assertEquals(call, radio.current(), "Duration estimate must not cut off actual playback");
        finish();
        assertNull(radio.advance(0.2f, true, false, false));
        var ack = radio.advance(0.2f, true, false, false);
        assertEquals(actor + "_ack", ack.line().id());
        assertEquals(actor, ack.speaker());
        finish();
        assertEquals("order", radio.advance(0.36f, true, false, false).line().id());
        finish();
        assertEquals(actor + "_readback", radio.advance(0.36f, true, false, false).line().id());
        finish();
        assertFalse(radio.busy());
    }

    @Test
    void hundredSquadsProduceOneConversationAndNoBacklog() {
        List<Observation> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++) rows.add(order(i, 20));
        radio.observe(rows);
        assertNotNull(radio.beginExchange());
        for (int i = 0; i < 3; i++) {
            finish();
            assertNotNull(radio.advance(0.36f, true, false, false));
        }
        finish();
        assertFalse(radio.busy());
        assertNull(radio.advance(31, true, false, false));
        radio.observe(rows);
        assertNull(radio.beginExchange(), "Unselected changes are consumed, not a hundred queued conversations");
        rows.set(77, order(77, 30));
        radio.observe(rows);
        assertNotNull(radio.beginExchange(), "A fresh order can speak once the global cooldown opens");
    }

    @Test
    void cooldownConsumesFurtherChangesRatherThanReplayingThemLater() {
        radio.observe(List.of(order(1, 20)));
        radio.beginExchange();
        radio.stop();
        radio.observe(List.of(order(1, 21)));
        assertNull(radio.beginExchange());
        radio.advance(31, true, false, false);
        radio.observe(List.of(order(1, 21)));
        assertNull(radio.beginExchange());
    }

    @Test
    void changedOrdersAndLostUnitsInvalidateOnlyUnspokenTurns() {
        radio.observe(List.of(order(1, 20)));
        radio.beginExchange();
        radio.observe(List.of(order(1, 30)));
        assertTrue(sound.playing);
        finish();
        assertNull(radio.advance(1, true, false, false));
        assertFalse(radio.busy());
        radio.reset();
        radio.observe(List.of(order(1, 20)));
        radio.observe(List.of());
        assertNull(radio.beginExchange());
    }

    @Test
    void pauseAndCompletionKeepActiveSpeechButAdmitNoNewTurns() {
        radio.observe(List.of(order(1, 20)));
        radio.beginExchange();
        assertNull(radio.advance(1, false, false, false));
        assertTrue(sound.playing);
        sound.playing = false;
        radio.advance(1, false, false, false);
        assertNull(radio.advance(1, false, false, false));
        assertNotNull(radio.advance(0, true, false, false));
        radio.advance(0, false, true, false);
        assertTrue(sound.playing);
        finish();
        assertNull(radio.advance(1, true, false, false));
        assertFalse(radio.busy());
    }

    @Test
    void blockedOrPausedContextExpiresAndMissionPreemptionDoesNotRepeatOrders() {
        radio.observe(List.of(order(1, 20)));
        radio.advance(13, false, false, true);
        assertNull(radio.beginExchange());
        radio.observe(List.of(order(1, 21)));
        assertNotNull(radio.beginExchange());
        radio.stop();
        assertFalse(sound.playing);
        radio.advance(31, true, false, false);
        radio.observe(List.of(order(1, 21)));
        assertNull(radio.beginExchange());
        radio.reset();
        radio.observe(List.of(order(1, 21)));
        assertNotNull(radio.beginExchange(), "A new battle resets consumed observations");
        radio.advance(26, false, false, false);
        finish();
        assertNull(radio.advance(1, true, false, false), "A long pause must not resume an obsolete instruction");
    }

    @Test
    void reportsRotateActorsAndMutedSpeechHasAReadableLifetime() {
        OperationRadio muted = new OperationRadio(catalog(), new Random(1), turn -> null);
        String previous = null;
        for (Cue cue : Cue.values()) {
            var report = muted.report(cue);
            assertNotNull(report);
            assertNotEquals(previous, report.speaker());
            assertTrue(report.line().id().endsWith(cue.name()));
            previous = report.speaker();
            assertNull(muted.report(Cue.CONTACT), "No simultaneous transmissions");
            muted.advance(1, false, false, false);
            assertEquals(report, muted.current());
            muted.advance(0.3f, false, false, false);
            assertNull(muted.current());
        }
    }

    @Test
    void copiedNodesAndMovingEscortRalliesDoNotCreateNewAssignments() {
        TacticalNode first = new TacticalNode(TacticalNode.Kind.BARRACKS, 10, 10, 8, 8, 12, 12, Faction.DEFENDER, 50, 6);
        TacticalNode copy = new TacticalNode(TacticalNode.Kind.BARRACKS, 10, 10, 8, 8, 12, 12, Faction.DEFENDER, 50, 6);
        assertEquals(AssignmentKey.of(ObjectiveAssignment.secureCompound(1, 5, first)),
                AssignmentKey.of(ObjectiveAssignment.secureCompound(1, 17, copy)));
        assertNotEquals(AssignmentKey.of(ObjectiveAssignment.secureCompound(1, 5, first)),
                AssignmentKey.of(ObjectiveAssignment.holdNode(1, first)));
        assertEquals(AssignmentKey.of(ObjectiveAssignment.escort(1, 10, 12)),
                AssignmentKey.of(ObjectiveAssignment.escort(1, 30, 42)));
    }

    @Test
    void everyShippedTurnHasMatchingVoiceProvenanceAndCommsRegistration() throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of("mod", OperationRadio.PATH)));
        Catalog catalog = OperationRadio.parse(root);
        assertEquals(3, catalog.actors().size());
        assertEquals(37, catalog.lines().size());
        assertEquals("cuwCdnt9CKAB9XrTA1j0", root.getJSONObject("voices").getJSONObject("command").getString("voiceId"));
        JSONObject sounds = new JSONObject(Files.readString(Path.of("mod/data/config/sounds.json"))
                .replaceAll("(?m)#.*$", ""));
        Path sources = Path.of("asset-pipeline/src/tool/resources/audio");
        var clips = new JSONObject(Files.readString(sources.resolve("audio.json"))
                .replaceAll("(?m)^\\s*//.*$", "")).getJSONArray("clips");
        for (Line line : catalog.lines().values()) {
            JSONObject receipt = new JSONObject(Files.readString(sources.resolve("operation-radio/live/" + line.id() + ".json")));
            assertEquals(root.getJSONObject("voices").getJSONObject(line.speaker()).getString("voiceId"), receipt.getString("voiceId"));
            assertEquals(root.getString("modelId"), receipt.getString("modelId"));
            assertEquals(line.durationSeconds(), receipt.getDouble("durationSeconds"), 0.001);
            assertEquals(receipt.getString("audioSha256"), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(
                            sources.resolve("operation-radio/live/" + line.id() + ".mp3")))));
            assertEquals("sounds/battle/operation-radio/" + line.id() + ".ogg",
                    sounds.getJSONArray(line.id()).getJSONObject(0).getString("file"));
            int matches = 0;
            for (int i = 0; i < clips.length(); i++) {
                JSONObject clip = clips.getJSONObject(i);
                if (!line.id().equals(clip.getString("id"))) continue;
                matches++;
                assertEquals("comms", clip.getString("processing"));
                assertEquals("ui", clip.getString("kind"));
                assertEquals("battle/operation-radio", clip.getString("out"));
                assertEquals("operation-radio/live/" + line.id() + ".mp3", clip.getString("source"));
            }
            assertEquals(1, matches);
        }
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
