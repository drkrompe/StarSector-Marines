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
import com.dillon.starsectormarines.marine.OfficerCommandProfile;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
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
    private boolean fieldCommanderDeployed;
    private final Map<Cue, List<Report>> remainingReports = new EnumMap<>(Cue.class);
    private final Map<Cue, String> lastReports = new EnumMap<>(Cue.class);
    private record Report(int actorIndex, String lineId) { }

    public record Line(String id, String speaker, String text, float durationSeconds) {
        public Line {
            if (id == null || id.isBlank() || speaker == null || speaker.isBlank()
                    || text == null || text.isBlank() || !Float.isFinite(durationSeconds)
                    || durationSeconds <= 0) throw new IllegalArgumentException("Invalid operation-radio line");
        }
    }

    public record Actor(String id, String label, String call, String ack, String readback,
                        Map<Cue, List<String>> reports, String portrait) {
        public Actor(String id, String label, String call, String ack, String readback,
                     Map<Cue, List<String>> reports) {
            this(id, label, call, ack, readback, reports, "");
        }

        public Actor {
            portrait = portrait == null ? "" : portrait;
            Map<Cue, List<String>> copy = new EnumMap<>(Cue.class);
            reports.forEach((cue, ids) -> copy.put(cue, List.copyOf(ids)));
            reports = Map.copyOf(copy);
        }
    }

    /**
     * The company's own officer on the ground. Castle may hand a company-wide tasking to her, and she
     * passes it to one callsign: an alternative voicing of the same exchange, never a replacement.
     */
    public record FieldCommand(String id, String label, String portrait, String call, String ack,
                               Map<String, String> handoffs, Map<String, String> withdrawHandoffs) {
        public FieldCommand {
            portrait = portrait == null ? "" : portrait;
            handoffs = Map.copyOf(handoffs);
            withdrawHandoffs = Map.copyOf(withdrawHandoffs);
        }

        String handoff(String actor, AssignmentKind kind) {
            String specific = kind == AssignmentKind.WITHDRAW ? withdrawHandoffs.get(actor) : null;
            return specific != null ? specific : handoffs.get(actor);
        }
    }

    public record Catalog(List<Actor> actors, Map<AssignmentKind, String> instructions, Map<String, Line> lines,
                          FieldCommand fieldCommand) {
        public Catalog(List<Actor> actors, Map<AssignmentKind, String> instructions, Map<String, Line> lines) {
            this(actors, instructions, lines, null);
        }

        public Catalog {
            actors = List.copyOf(actors);
            instructions = Map.copyOf(instructions);
            lines = Map.copyOf(lines);
            if (fieldCommand != null) {
                if (!speaks(lines, fieldCommand.call(), "command") || !speaks(lines, fieldCommand.ack(), fieldCommand.id()))
                    throw new IllegalArgumentException("Invalid field-command handshake");
                for (Actor actor : actors)
                    if (!speaks(lines, fieldCommand.handoffs().get(actor.id()), fieldCommand.id()))
                        throw new IllegalArgumentException("Missing field-command handoff " + actor.id());
                for (String id : fieldCommand.withdrawHandoffs().values())
                    if (!speaks(lines, id, fieldCommand.id()))
                        throw new IllegalArgumentException("Invalid field-command handoff " + id);
            }
            for (Actor actor : actors) {
                for (String id : List.of(actor.call(), actor.ack(), actor.readback())) {
                    if (!lines.containsKey(id)) throw new IllegalArgumentException("Missing radio turn " + id);
                }
                for (Cue cue : Cue.values()) {
                    List<String> reports = actor.reports().get(cue);
                    if (reports == null || reports.isEmpty())
                        throw new IllegalArgumentException("Missing report " + actor.id() + "/" + cue);
                    if (new HashSet<>(reports).size() != reports.size())
                        throw new IllegalArgumentException("Duplicate report " + actor.id() + "/" + cue);
                    for (String id : reports) {
                        Line line = lines.get(id);
                        if (line == null || !line.speaker().equals(actor.id()))
                            throw new IllegalArgumentException("Invalid report " + actor.id() + "/" + id);
                    }
                }
            }
            for (String id : instructions.values())
                if (!lines.containsKey(id)) throw new IllegalArgumentException("Missing instruction " + id);
        }

        private static boolean speaks(Map<String, Line> lines, String id, String speaker) {
            Line line = id == null ? null : lines.get(id);
            return line != null && line.speaker().equals(speaker);
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

    public record Transmission(Line line, String speaker, String portrait) { }
    private static final class Pending {
        final int squadId;
        final AssignmentKey assignment;
        /** Several squads were retasked together: a company-level fact the field commander may voice. */
        boolean company;
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
            Map<Cue, List<String>> reports = new EnumMap<>(Cue.class);
            for (Cue cue : Cue.values()) {
                JSONObject pools = actor.getJSONObject("reports");
                JSONArray variants = pools.optJSONArray(cue.name());
                List<String> ids = new ArrayList<>();
                if (variants == null) ids.add(pools.getString(cue.name()));
                else for (int j = 0; j < variants.length(); j++) ids.add(variants.getString(j));
                reports.put(cue, ids);
            }
            actors.add(new Actor(actor.getString("id"), actor.getString("label"), actor.getString("call"),
                    actor.getString("ack"), actor.getString("readback"), reports, actor.optString("portrait", "")));
        }
        Map<AssignmentKind, String> instructions = new EnumMap<>(AssignmentKind.class);
        JSONObject orders = root.getJSONObject("instructions");
        for (AssignmentKind kind : AssignmentKind.values())
            if (orders.has(kind.name())) instructions.put(kind, orders.getString(kind.name()));
        FieldCommand field = null;
        JSONObject ground = root.optJSONObject("fieldCommand");
        if (ground != null) {
            field = new FieldCommand(ground.getString("id"), ground.getString("label"),
                    ground.optString("portrait", ""), ground.getString("call"), ground.getString("ack"),
                    strings(ground.getJSONObject("handoffs")), strings(ground.optJSONObject("withdrawHandoffs")));
        }
        return new Catalog(actors, instructions, lines, field);
    }

    private static Map<String, String> strings(JSONObject object) throws JSONException {
        Map<String, String> result = new HashMap<>();
        String[] keys = object == null ? null : JSONObject.getNames(object);
        if (keys == null) return result;
        for (String key : keys) result.put(key, object.getString(key));
        return result;
    }

    /** Reads only living own infantry and the arbiter's current accepted directive after simulation advance. */
    public void observe(BattleSimulation sim) {
        OfficerCommandProfile officer = sim.operationOfficer();
        fieldCommanderDeployed = officer != null && officer.founding();
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
        if (candidate != null) candidate.company = changes > 1;
        observed.clear();
        observed.putAll(latest);
        if (pending != null && !valid(pending)) pending = null;
        if (context != null && !valid(context)) discardUnspoken();
        if (pending == null && !busy() && cooldown <= 0) pending = candidate;
    }

    /** Whether the company's founding officer leads this deployment; she speaks only when she is down there. */
    void fieldCommanderDeployed(boolean deployed) {
        fieldCommanderDeployed = deployed;
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
        AssignmentKind kind = pending.assignment.kind();
        String order = catalog.instructions().get(kind);
        FieldCommand field = catalog.fieldCommand();
        // A company-wide retasking may go through the field commander or straight to the squad.
        boolean relay = pending.company && fieldCommanderDeployed && field != null && random.nextBoolean();
        context = pending;
        pending = null;
        sequenceAge = 0;
        cooldown = 30;
        List<String> turns = relay
                ? List.of(field.call(), field.ack(), order, field.handoff(actor.id(), kind), actor.readback())
                : List.of(actor.call(), actor.ack(), order, actor.readback());
        for (String id : turns) sequence.addLast(transmission(id, actor));
        return start(sequence.removeFirst());
    }

    /** The shared combat policy has already admitted and prioritized this confirmed report. */
    public Transmission report(Cue cue) {
        if (!available() || busy()) return null;
        List<Report> pool = remainingReports.computeIfAbsent(cue, ignored -> new ArrayList<>());
        if (pool.isEmpty()) {
            for (int i = 0; i < catalog.actors().size(); i++) {
                for (String id : catalog.actors().get(i).reports().get(cue)) pool.add(new Report(i, id));
            }
            Collections.shuffle(pool, random);
        }
        // Exhaust the cue's recordings before refilling. Prefer a different actor,
        // but never strand that actor's unused lines at the end of a cycle.
        int selected = -1;
        for (int i = 0; i < pool.size(); i++) {
            Report candidate = pool.get(i);
            if (candidate.lineId().equals(lastReports.get(cue))) continue;
            if (selected < 0) selected = i;
            if (candidate.actorIndex() != lastActor) { selected = i; break; }
        }
        Report report = pool.remove(selected < 0 ? 0 : selected);
        lastActor = report.actorIndex();
        lastReports.put(cue, report.lineId());
        return start(transmission(report.lineId(), catalog.actors().get(lastActor)));
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
        if (line.speaker().equals("command"))
            return new Transmission(line, "COMPANY COMMAND", "graphics/portraits/castle.png");
        FieldCommand field = catalog.fieldCommand();
        if (field != null && line.speaker().equals(field.id()))
            return new Transmission(line, field.label(), field.portrait());
        return new Transmission(line, actor.label(), actor.portrait());
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

    /** Zoey and host teardown preempt incidental speech. Observed orders remain consumed. */
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
        fieldCommanderDeployed = false;
        remainingReports.clear();
        lastReports.clear();
    }
}
