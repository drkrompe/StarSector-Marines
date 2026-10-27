package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.audio.BattleRadioChatter.Cue;
import com.dillon.starsectormarines.battle.audio.OperationRadio.Actor;
import com.dillon.starsectormarines.battle.audio.OperationRadio.AssignmentKey;
import com.dillon.starsectormarines.battle.audio.OperationRadio.Catalog;
import com.dillon.starsectormarines.battle.audio.OperationRadio.FieldCommand;
import com.dillon.starsectormarines.battle.audio.OperationRadio.Line;
import com.dillon.starsectormarines.battle.audio.OperationRadio.Observation;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.fs.starfarer.api.SoundAPI;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
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
            actors.add(new Actor(name, name, name + "_call", name + "_ack", name + "_readback",
                    reports, "graphics/portraits/radio-" + name + ".png"));
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
        assertEquals("graphics/portraits/castle.png", call.portrait());
        assertNull(radio.advance(3, true, false, false));
        assertEquals(call, radio.current(), "Duration estimate must not cut off actual playback");
        finish();
        assertNull(radio.advance(0.2f, true, false, false));
        var ack = radio.advance(0.2f, true, false, false);
        assertEquals(actor + "_ack", ack.line().id());
        assertEquals(actor, ack.speaker());
        assertEquals("graphics/portraits/radio-" + actor + ".png", ack.portrait());
        finish();
        var instruction = radio.advance(0.36f, true, false, false);
        assertEquals("order", instruction.line().id());
        assertEquals(call.portrait(), instruction.portrait());
        finish();
        var readback = radio.advance(0.36f, true, false, false);
        assertEquals(actor + "_readback", readback.line().id());
        assertEquals(ack.portrait(), readback.portrait());
        finish();
        assertFalse(radio.busy());
    }

    private static Catalog withFieldCommand() {
        Catalog base = catalog();
        Map<String, Line> lines = new HashMap<>(base.lines());
        lines.put("sabine_call", new Line("sabine_call", "command", "Morgan. This is Command.", 1));
        lines.put("sabine_ack", new Line("sabine_ack", "sabine", "Morgan. Go ahead.", 1));
        Map<String, String> handoffs = new HashMap<>();
        Map<String, String> withdraw = new HashMap<>();
        for (Actor actor : base.actors()) {
            String id = "sabine_handoff_" + actor.id();
            lines.put(id, new Line(id, "sabine", id, 1));
            handoffs.put(actor.id(), id);
            String out = "sabine_withdraw_" + actor.id();
            lines.put(out, new Line(out, "sabine", out, 1));
            withdraw.put(actor.id(), out);
        }
        lines.put("withdraw", new Line("withdraw", "command", "Fall back.", 1));
        FieldCommand field = new FieldCommand("sabine", "MORGAN HALE / FIELD COMMAND",
                "graphics/portraits/sabine-hale.png", "sabine_call", "sabine_ack", handoffs, withdraw);
        return new Catalog(base.actors(), Map.of(AssignmentKind.ATTACK_MOVE, "order",
                AssignmentKind.WITHDRAW, "withdraw"), lines, field);
    }

    /** Always takes the relay when it is offered, so a test sees whether it was offered at all. */
    private static Random relayWhenOffered() {
        return new Random(5) {
            @Override public boolean nextBoolean() { return true; }
        };
    }

    private static List<String> exchange(OperationRadio radio, FakeSound sound) {
        List<String> turns = new ArrayList<>();
        var turn = radio.beginExchange();
        while (turn != null) {
            turns.add(turn.line().id());
            sound.playing = false;
            radio.advance(1.3f, true, false, false);
            turn = radio.advance(0.36f, true, false, false);
        }
        return turns;
    }

    @Test
    void deployedFieldCommanderRelaysACompanyWideRetaskingToOneCallsign() {
        FakeSound played = new FakeSound();
        OperationRadio net = new OperationRadio(withFieldCommand(), relayWhenOffered(), turn -> {
            played.playing = true;
            return played;
        });
        net.fieldCommanderDeployed(true);
        net.observe(List.of(order(1, 20), order(2, 30), order(3, 40)));
        var call = net.beginExchange();
        assertEquals("sabine_call", call.line().id());
        assertEquals("COMPANY COMMAND", call.speaker());
        played.playing = false;
        net.advance(1.3f, true, false, false);
        var ack = net.advance(0.36f, true, false, false);
        assertEquals("sabine_ack", ack.line().id());
        assertEquals("MORGAN HALE / FIELD COMMAND", ack.speaker());
        assertEquals("graphics/portraits/sabine-hale.png", ack.portrait());
        List<String> rest = new ArrayList<>();
        for (var turn = ack; turn != null; ) {
            played.playing = false;
            net.advance(1.3f, true, false, false);
            turn = net.advance(0.36f, true, false, false);
            if (turn != null) rest.add(turn.line().id());
        }
        assertEquals(3, rest.size());
        assertEquals("order", rest.get(0), "Castle's instruction is the same one a squad would hear");
        String actor = rest.get(1).replace("sabine_handoff_", "");
        assertEquals(actor + "_readback", rest.get(2), "The callsign she names is the one that answers");
    }

    @Test
    void withdrawalUsesHerLastOutHandoff() {
        FakeSound played = new FakeSound();
        OperationRadio net = new OperationRadio(withFieldCommand(), relayWhenOffered(), turn -> {
            played.playing = true;
            return played;
        });
        net.fieldCommanderDeployed(true);
        net.observe(List.of(new Observation(1, new AssignmentKey(AssignmentKind.WITHDRAW, "pad")),
                new Observation(2, new AssignmentKey(AssignmentKind.WITHDRAW, "pad"))));
        List<String> turns = exchange(net, played);
        assertEquals("sabine_call", turns.get(0));
        assertTrue(turns.get(3).startsWith("sabine_withdraw_"), turns.toString());
    }

    @Test
    void castleKeepsSingleSquadWorkAndTheWholeNetWhenSheIsNotDeployed() {
        FakeSound played = new FakeSound();
        OperationRadio net = new OperationRadio(withFieldCommand(), relayWhenOffered(), turn -> {
            played.playing = true;
            return played;
        });
        net.fieldCommanderDeployed(true);
        net.observe(List.of(order(1, 20)));
        List<String> single = exchange(net, played);
        assertEquals(4, single.size());
        assertTrue(single.get(0).endsWith("_call") && !single.get(0).startsWith("sabine"),
                "One squad's new task stays between Castle and that squad");

        net.reset();
        net.fieldCommanderDeployed(false);
        net.observe(List.of(order(1, 20), order(2, 30)));
        List<String> absent = exchange(net, played);
        assertTrue(absent.stream().noneMatch(id -> id.startsWith("sabine")),
                "She speaks only when she is actually down there: " + absent);
    }

    @Test
    void fieldCommandMustCoverEveryCallsignInHerOwnVoice() {
        Catalog full = withFieldCommand();
        FieldCommand field = full.fieldCommand();
        Map<String, String> missing = new HashMap<>(field.handoffs());
        missing.remove("hammer");
        assertThrows(IllegalArgumentException.class, () -> new Catalog(full.actors(), full.instructions(),
                full.lines(), new FieldCommand(field.id(), field.label(), field.portrait(), field.call(),
                        field.ack(), missing, field.withdrawHandoffs())));
        assertThrows(IllegalArgumentException.class, () -> new Catalog(full.actors(), full.instructions(),
                full.lines(), new FieldCommand(field.id(), field.label(), field.portrait(), field.call(),
                        "kestrel_ack", field.handoffs(), field.withdrawHandoffs())));
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
            assertEquals("graphics/portraits/radio-" + report.line().speaker() + ".png", report.portrait());
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

    private static final class FakeSound implements SoundAPI {
        boolean playing;
        public void stop() { playing = false; }
        public boolean isPlaying() { return playing; }
        public void setVolume(float volume) { }
        public void setPitch(float pitch) { }
        public void setLocation(float x, float y) { }
    }
}
