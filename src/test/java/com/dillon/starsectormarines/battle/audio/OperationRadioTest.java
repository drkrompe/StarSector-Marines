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
import java.util.HashSet;
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
        return catalog(1);
    }

    private static Catalog catalog(int variants) {
        Map<String, Line> lines = new HashMap<>();
        List<Actor> actors = new ArrayList<>();
        lines.put("order", new Line("order", "command", "Move to your assigned position.", 1));
        for (String name : List.of("kestrel", "hammer", "viper")) {
            for (String turn : List.of("call", "ack", "readback")) {
                String id = name + "_" + turn;
                lines.put(id, new Line(id, turn.equals("call") ? "command" : name, id, 1));
            }
            Map<Cue, List<String>> reports = new EnumMap<>(Cue.class);
            for (Cue cue : Cue.values()) {
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < variants; i++) {
                    String id = name + "_" + i + "_" + cue.name();
                    lines.put(id, new Line(id, name, id, 1));
                    ids.add(id);
                }
                reports.put(cue, ids);
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
    void eachCueUsesEveryRecordingBeforeRepeatingEvenAcrossPreemption() {
        OperationRadio varied = new OperationRadio(catalog(3), new Random(9), turn -> null);
        Map<Cue, String> previous = new EnumMap<>(Cue.class);
        for (int cycle = 0; cycle < 4; cycle++) {
            Map<Cue, HashSet<String>> heard = new EnumMap<>(Cue.class);
            for (int i = 0; i < 9; i++) {
                for (Cue cue : Cue.values()) {
                    var report = varied.report(cue);
                    assertNotNull(report);
                    assertTrue(heard.computeIfAbsent(cue, ignored -> new HashSet<>()).add(report.line().id()),
                            "A cue must exhaust its pool before replaying a line");
                    assertNotEquals(previous.put(cue, report.line().id()), report.line().id(),
                            "Refilling a pool must not repeat its previous line");
                    assertEquals(report.line().speaker(), report.speaker());
                    assertNull(varied.report(cue), "A busy channel must not consume another variant");
                    varied.stop(); // Story preemption preserves per-battle variety history.
                }
            }
        }
    }

    @Test
    void singleActorPoolsWorkAndResetStartsAFreshBattle() {
        Catalog full = catalog(3);
        Actor actor = full.actors().get(0);
        Catalog single = new Catalog(List.of(actor), full.instructions(), full.lines());
        Random fixed = new Random(0) {
            @Override public int nextInt(int bound) { return 0; }
        };
        OperationRadio varied = new OperationRadio(single, fixed, turn -> null);
        String first = varied.report(Cue.CONTACT).line().id();
        varied.stop();
        assertNotEquals(first, varied.report(Cue.CONTACT).line().id());
        varied.reset();
        assertEquals(first, varied.report(Cue.CONTACT).line().id());
        varied.stop();
        String previous = first;
        for (int i = 0; i < 10; i++) {
            String next = varied.report(Cue.CONTACT).line().id();
            assertNotEquals(previous, next);
            previous = next;
            varied.stop();
        }
    }

    @Test
    void reportPoolsRejectEmptyDuplicateMissingAndWrongSpeakerLines() {
        Catalog full = catalog(3);
        Actor actor = full.actors().get(0);
        for (List<String> invalid : List.of(List.<String>of(), List.of("missing"),
                List.of(actor.call()), List.of(actor.reports().get(Cue.CONTACT).get(0),
                        actor.reports().get(Cue.CONTACT).get(0)))) {
            Map<Cue, List<String>> reports = new EnumMap<>(actor.reports());
            reports.put(Cue.CONTACT, invalid);
            Actor broken = new Actor(actor.id(), actor.label(), actor.call(), actor.ack(), actor.readback(), reports);
            assertThrows(IllegalArgumentException.class,
                    () -> new Catalog(List.of(broken), full.instructions(), full.lines()));
        }
    }

    @Test
    void aSingleRecordingRemainsPlayableAfterItsPoolIsExhausted() {
        Catalog full = catalog();
        OperationRadio single = new OperationRadio(
                new Catalog(List.of(full.actors().get(0)), full.instructions(), full.lines()),
                new Random(2), turn -> null);
        String first = single.report(Cue.CONTACT).line().id();
        for (int i = 0; i < 3; i++) {
            single.stop();
            assertEquals(first, single.report(Cue.CONTACT).line().id());
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
        assertEquals(6, catalog.actors().size());
        assertEquals(151, catalog.lines().size());
        for (Cue cue : Cue.values()) {
            assertEquals(18, catalog.actors().stream().mapToInt(actor -> actor.reports().get(cue).size()).sum());
        }
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
        // Catalogs using the original one-ID-per-event schema remain usable.
        var actors = root.getJSONArray("actors");
        for (int i = 0; i < actors.length(); i++) {
            JSONObject reports = actors.getJSONObject(i).getJSONObject("reports");
            for (Cue cue : Cue.values()) reports.put(cue.name(), reports.getJSONArray(cue.name()).getString(0));
        }
        Catalog legacy = OperationRadio.parse(root);
        for (Actor actor : legacy.actors()) {
            for (Cue cue : Cue.values()) assertEquals(1, actor.reports().get(cue).size());
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
