package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.audio.BattleRadioChatter.Cue;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;

/**
 * Sparse, exchange-local casting for the battlefield net. Observes accepted own-force
 * assignments; never renames a squad, writes an order, or consumes simulation randomness.
 */
public final class OperationRadio {
    public static final String PATH = "data/marines/operation_radio.json";
    private static Catalog builtins = new Catalog(List.of(), Map.of(), Map.of());
    private final Catalog catalog;
    private final Random random;
    private final Function<Transmission, SoundAPI> play;
    private final Map<Integer, AssignmentKey> observed = new HashMap<>();
    private final ArrayDeque<Transmission> sequence = new ArrayDeque<>();
    private Pending pending;
    private Pending context;
    private Transmission current;
    private SoundAPI sound;
    private float remaining, gap, cooldown, sequenceAge;
    private int lastActor = -1;

    public record Line(String id, String speaker, String text, float durationSeconds) {
        public Line {
            if (id == null || id.isBlank() || speaker == null || speaker.isBlank()
                    || text == null || text.isBlank() || !Float.isFinite(durationSeconds)
                    || durationSeconds <= 0) throw new IllegalArgumentException("Invalid operation-radio line");
        }
    }

    public record Actor(String id, String label, String call, String ack, String readback,
                        Map<Cue, String> reports) {
        public Actor { reports = Map.copyOf(reports); }
    }

    public record Catalog(List<Actor> actors, Map<AssignmentKind, String> instructions, Map<String, Line> lines) {
        public Catalog {
            actors = List.copyOf(actors);
            instructions = Map.copyOf(instructions);
            lines = Map.copyOf(lines);
            for (Actor actor : actors) {
                for (String id : List.of(actor.call(), actor.ack(), actor.readback())) {
                    if (!lines.containsKey(id)) throw new IllegalArgumentException("Missing radio turn " + id);
                }
                for (Cue cue : Cue.values()) {
                    if (actor.reports().get(cue) == null || !lines.containsKey(actor.reports().get(cue)))
                        throw new IllegalArgumentException("Missing report " + actor.id() + "/" + cue);
                }
            }
            for (String id : instructions.values())
                if (!lines.containsKey(id)) throw new IllegalArgumentException("Missing instruction " + id);
        }
    }

    /** Immutable presentation values make the admission policy testable without a battle fixture. */
    public record Observation(int squadId, AssignmentKey assignment) { }

    /** Authored node geometry outlives copied node objects and capture-zone renumbering. */
    public record AssignmentKey(AssignmentKind kind, String destination) {
        public static AssignmentKey of(ObjectiveAssignment assignment) {
            if (assignment == null) return null;
            TacticalNode node = assignment.targetNode();
            String destination;
            if (node != null) {
                destination = "node:" + node.kind + ":" + node.anchorX + ":" + node.anchorY
                        + ":" + node.left + ":" + node.top + ":" + node.right + ":" + node.bottom;
            } else if (assignment.objectiveId() >= 0) {
                destination = "objective:" + assignment.objectiveId();
            } else if (assignment.kind() == AssignmentKind.ESCORT) {
                // A moving escort rally is progress on the same task, not a fresh order.
                destination = "escort";
            } else {
                destination = assignment.targetZoneId() + ":" + assignment.targetCellX()
                        + ":" + assignment.targetCellY() + ":" + assignment.targetRadiusCells();
            }
            return new AssignmentKey(assignment.kind(), destination);
        }
    }

    public record Transmission(Line line, String speaker) { }
    private static final class Pending {
        final int squadId;
        final AssignmentKey assignment;
        float age;
        Pending(int squadId, AssignmentKey assignment) {
            this.squadId = squadId;
            this.assignment = assignment;
        }
    }

    public OperationRadio() {
        this(builtins, new Random(), transmission -> {
            try {
                return Global.getSoundPlayer().playUISound(transmission.line().id(), 1f, 1f);
            } catch (RuntimeException failure) {
                Global.getLogger(OperationRadio.class).warn("Cannot play operation radio", failure);
                return null;
            }
        });
    }

    OperationRadio(Catalog catalog, Random random, Function<Transmission, SoundAPI> play) {
        this.catalog = catalog;
        this.random = random;
        this.play = play;
    }

    public static void loadBuiltins() {
        try {
            builtins = parse(Global.getSettings().loadJSON(PATH));
        } catch (Exception failure) {
            builtins = new Catalog(List.of(), Map.of(), Map.of());
            Global.getLogger(OperationRadio.class).warn("Operation radio unavailable", failure);
        }
    }

    public static Catalog parse(JSONObject root) throws JSONException {
        Map<String, Line> lines = new HashMap<>();
        JSONArray entries = root.getJSONArray("lines");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            Line line = new Line(entry.getString("id"), entry.getString("speaker"),
                    entry.getString("text"), (float) entry.getDouble("durationSeconds"));
            if (lines.put(line.id(), line) != null) throw new IllegalArgumentException("Duplicate radio line");
        }
        List<Actor> actors = new ArrayList<>();
        JSONArray cast = root.getJSONArray("actors");
        for (int i = 0; i < cast.length(); i++) {
            JSONObject actor = cast.getJSONObject(i);
            Map<Cue, String> reports = new EnumMap<>(Cue.class);
            for (Cue cue : Cue.values()) reports.put(cue, actor.getJSONObject("reports").getString(cue.name()));
            actors.add(new Actor(actor.getString("id"), actor.getString("label"), actor.getString("call"),
                    actor.getString("ack"), actor.getString("readback"), reports));
        }
        Map<AssignmentKind, String> instructions = new EnumMap<>(AssignmentKind.class);
        JSONObject orders = root.getJSONObject("instructions");
        for (AssignmentKind kind : AssignmentKind.values())
            if (orders.has(kind.name())) instructions.put(kind, orders.getString(kind.name()));
        return new Catalog(actors, instructions, lines);
    }

    /** Reads only living own infantry and the arbiter's current accepted directive after simulation advance. */
    public void observe(BattleSimulation sim) {
        List<Observation> rows = new ArrayList<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE || squad.aliveMembers <= 0
                    || squad.isMechSquad() || squad.isDroneSquad()) continue;
            CommandDirective directive = sim.getCommanderService().assignments().activeDirective(squad.id);
            AssignmentKey key = null;
            if (directive != null && directive.perspective() == Faction.MARINE && directive.ownsAssignment()
                    && (directive.authority() == CommandAuthority.MISSION_COMMAND
                        || directive.authority() == CommandAuthority.PLAYER_INTERVENTION)
                    && squad.assignmentForExecution() != null) key = AssignmentKey.of(directive.assignment());
            rows.add(new Observation(squad.id, key));
        }
        observe(rows);
    }

    /** Reservoir-samples simultaneous changes into one pending exchange, never a queue per squad. */
    void observe(List<Observation> rows) {
        Map<Integer, AssignmentKey> latest = new HashMap<>();
        Pending candidate = null;
        int changes = 0;
        for (Observation row : rows) {
            AssignmentKey key = row.assignment();
            latest.put(row.squadId(), key);
            if (key != null && catalog.instructions().containsKey(key.kind())
                    && !Objects.equals(observed.get(row.squadId()), key)
                    && random.nextInt(++changes) == 0) candidate = new Pending(row.squadId(), key);
        }
        observed.clear();
        observed.putAll(latest);
        if (pending != null && !valid(pending)) pending = null;
        if (context != null && !valid(context)) discardUnspoken();
        if (pending == null && !busy() && cooldown <= 0) pending = candidate;
    }

    private boolean valid(Pending item) {
        return item.assignment.equals(observed.get(item.squadId));
    }

    /** Real-time playback of admitted turns. New exchanges compete separately with combat reports. */
    public Transmission advance(float dt, boolean running, boolean complete,
                                boolean channelBusy) {
        dt = Math.max(0, dt);
        cooldown = Math.max(0, cooldown - dt);
        gap = Math.max(0, gap - dt);
        if (pending != null && (pending.age += dt) > 12) pending = null;
        if (context != null && (sequenceAge += dt) > 25) discardUnspoken();
        if (complete) { pending = null; discardUnspoken(); }
        if (current != null) {
            remaining -= dt;
            if (remaining <= 0 && (sound == null || !sound.isPlaying())) {
                if (sound != null) sound.stop();
                sound = null;
                current = null;
                gap = 0.35f;
                if (sequence.isEmpty()) context = null;
            }
        }
        if (!running || complete || channelBusy || current != null || gap > 0 || !available()) return null;
        if (!sequence.isEmpty()) return start(sequence.removeFirst());
        return null;
    }

    /** Called only when the host is running and the shared channel and voice budget are open. */
    public Transmission beginExchange() {
        if (!available() || busy() || gap > 0 || pending == null || cooldown > 0 || !valid(pending)) return null;
        Actor actor = actor();
        String order = catalog.instructions().get(pending.assignment.kind());
        context = pending;
        pending = null;
        sequenceAge = 0;
        cooldown = 30;
        for (String id : List.of(actor.call(), actor.ack(), order, actor.readback()))
            sequence.addLast(transmission(id, actor));
        return start(sequence.removeFirst());
    }

    /** The shared combat policy has already admitted and prioritized this confirmed report. */
    public Transmission report(Cue cue) {
        if (!available() || busy()) return null;
        Actor actor = actor();
        return start(transmission(actor.reports().get(cue), actor));
    }

    private Actor actor() {
        int selected = random.nextInt(catalog.actors().size());
        if (catalog.actors().size() > 1 && selected == lastActor)
            selected = (selected + 1) % catalog.actors().size();
        lastActor = selected;
        return catalog.actors().get(selected);
    }

    private Transmission transmission(String id, Actor actor) {
        Line line = catalog.lines().get(id);
        return new Transmission(line, line.speaker().equals("command") ? "COMPANY COMMAND" : actor.label());
    }

    private Transmission start(Transmission transmission) {
        current = transmission;
        remaining = transmission.line().durationSeconds() + 0.25f;
        sound = play.apply(transmission);
        return transmission;
    }

    private void discardUnspoken() {
        sequence.clear();
        context = null;
    }

    public boolean available() { return !catalog.actors().isEmpty(); }
    public boolean busy() { return current != null || !sequence.isEmpty(); }
    public Transmission current() { return current; }

    /** Mara and host teardown preempt incidental speech. Observed orders remain consumed. */
    public void stop() {
        if (sound != null) sound.stop();
        sound = null;
        current = null;
        pending = null;
        discardUnspoken();
        remaining = gap = 0;
    }

    public void reset() {
        stop();
        observed.clear();
        cooldown = 0;
        lastActor = -1;
    }
}

