package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two readings any mission harness can take of a battle about places (`garrisonables.md`, not every fight is into
 * prepared positions; holding taken ground; `conquest-command.md`, a place the defender loses). Readings, never
 * assertions; sampled every {@value #SAMPLE_TICKS} ticks.
 *
 * <p><b>Fight mix.</b> Every ground shooter that fired in a window is one contact sample, as it stood at its last shot,
 * and each end of it is <em>on a post</em> or <em>in the open</em>: on a post is dug in, standing on a fighting-position
 * cell, under an intact roof (inside a building), or an emplacement itself; a machine is in the open. The target end is
 * the shooter's own target, or where the round went when it has none. A contact whose two ends are both
 * in the open is a brawl; one whose shooter is in the open and target on a post is an assault on a post; the reverse is
 * a defence from a post; both on posts is a duel of posts.
 *
 * <p><b>Hold meets counter-attack.</b> A counter-attack is the run of samples during which a side has squads its
 * commander gave a counter-attack launch ({@code ..._COUNTERATTACK_LAUNCH}, {@code ..._COUNTERATTACK_LAUNCHED}). It
 * <em>met a hold</em> if one of its squads came within {@value #MEET_CELLS} cells of a hostile squad holding taken ground
 * ({@code HOLD_GROUND_*}), <em>met a post</em> if it came that close to a hostile squad whose order mans a place or holds
 * a building or which has a member dug in, and <em>met</em> anybody if any hostile squad was that close. Whether it won
 * its place back is read off the trace's own "retook it at tick" notes. A hold is <em>pressed by the counter-attack</em>
 * on a sample when a counter-attack squad of any stage (reserve, probe, muster, launch) stands within
 * {@value #PRESS_CELLS} cells of it.
 */
public final class PlaceFightProbe {

    static final int SAMPLE_TICKS = 30;
    static final int MEET_CELLS = 24;
    static final int PRESS_CELLS = 40;

    private static final Pattern RETOOK = Pattern.compile("retook it at tick (\\d+)");

    /** Shooters that fired in the current window: [faction ordinal, shooter on a post, target on a post]. */
    private final Map<Long, int[]> window = new HashMap<>();
    /** Per shooting faction: [contacts, open-open, open-at-post, post-at-open, post-post]. */
    private final Map<Faction, long[]> mix = new EnumMap<>(Faction.class);

    private final List<Episode> episodes = new ArrayList<>();
    private final Map<Faction, Episode> open = new EnumMap<>(Faction.class);
    /** Compounds the marines have held since the defender last held them, and the ticks the defender won one back. */
    private final Set<CompoundService.Record> marineHeld = new HashSet<>();
    private final List<Integer> compoundRetakes = new ArrayList<>();
    private final List<JSONObject> turns = new ArrayList<>();
    private boolean fortWasHeld;
    private int holderSamples;
    private final Map<String, Integer> holderKinds = new TreeMap<>();
    private int holderSamplesPressedByCounterattack;
    private int holderSamplesDugIn;

    private static final class Episode {
        final Faction side;
        final int start;
        int end;
        final Set<Integer> squads = new HashSet<>();
        final Map<Integer, Integer> aliveAtJoin = new HashMap<>();
        boolean metHold;
        final Set<String> holdKinds = new TreeSet<>();
        boolean metDugInHold;
        boolean metPost;
        boolean met;
        int holdersMet;
        int lost;

        Episode(Faction side, int start) {
            this.side = side;
            this.start = start;
            this.end = start;
        }
    }

    private record Body(int squadId, Faction faction, float x, float y, int alive, boolean dugIn, String reason,
                        ObjectiveAssignment order, boolean posted) { }

    public void sample(BattleSimulation sim) {
        collectShots(sim);
        int tick = sim.getSimTickIndex();
        if (tick % SAMPLE_TICKS != 0) return;
        flushWindow();
        sampleCompounds(sim, tick);
        sampleCounterattacks(sim, tick);
    }

    private void sampleCompounds(BattleSimulation sim, int tick) {
        CompoundService compounds = sim.getCompoundService();
        if (compounds != null) {
            for (CompoundService.Record record : compounds.getRecords()) {
                if (record.state == CompoundState.MARINE_HELD) {
                    if (marineHeld.add(record)) turns.add(turn(sim, tick, "compound"));
                } else if (record.state == CompoundState.DEFENDER_HELD && marineHeld.remove(record)) {
                    compoundRetakes.add(tick);
                }
            }
        }
        boolean anyFort = false;
        boolean whole = true;
        for (DefenceStructureFact fact : sim.defenceStructures()) {
            if (!fact.ofFort()) continue;
            anyFort = true;
            if (fact.holder() != Faction.MARINE) whole = false;
        }
        boolean fortHeld = anyFort && whole;
        if (fortHeld && !fortWasHeld) turns.add(turn(sim, tick, "fort"));
        fortWasHeld = fortHeld;
    }

    /**
     * What the defender had left to counter-attack with when a place turned: its squads that are not a garrison of a
     * building, and their members.
     */
    private static JSONObject turn(BattleSimulation sim, int tick, String kind) {
        CommanderSnapshot<?> snapshot = sim.getCommanderSnapshot(Faction.DEFENDER);
        int squads = 0;
        int members = 0;
        int garrisons = 0;
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.DEFENDER || squad.aliveMembers <= 0) continue;
            CommandDirective directive = snapshot == null ? null : snapshot.directiveFor(squad.id);
            String reason = directive == null ? "" : directive.reason();
            if (reason.contains("GARRISON") || squad.defensePost != null) {
                garrisons++;
                continue;
            }
            squads++;
            members += squad.aliveMembers;
        }
        try {
            return new JSONObject().put("tick", tick).put("kind", kind).put("mobileSquads", squads)
                    .put("mobileMembers", members).put("garrisonSquads", garrisons);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Every shot fired this tick by something on the ground, classified at both ends; a shooter counts once per window,
     * as it stood at its last shot, so a machine gun's burst weighs what a rifleman's single round does.
     */
    private void collectShots(BattleSimulation sim) {
        FightingPositionIndex positions = sim.fightingPositions();
        for (ShotEvent shot : sim.getShotsThisFrame()) {
            long shooter = shot.shooterId;
            if (shooter == 0L || sim.liveUnitIndexOf(shooter) < 0) continue;
            UnitType type = sim.identity().type(shooter);
            if (type.isBasedAircraft() || type.isDrone() || type.isDroneHub()) continue;
            Faction faction = sim.identity().faction(shooter);
            if (faction == null || faction == Faction.CIVILIAN) continue;
            long target = sim.targetOf(shooter);
            if (target == 0L || sim.identity().faction(target) == null
                    || !faction.hostileTo(sim.identity().faction(target))) {
                target = shot.flightTargetId != 0L ? sim.resolveUnit(shot.flightTargetId) : 0L;
            }
            boolean targetPost;
            if (target != 0L && sim.identity().faction(target) != null
                    && faction.hostileTo(sim.identity().faction(target))) {
                targetPost = onPost(sim, positions, target);
            } else {
                int x = (int) Math.floor(shot.toX);
                int y = (int) Math.floor(shot.toY);
                targetPost = (positions != null && positions.isFightingPositionCell(x, y))
                        || (sim.getTopology().inBounds(x, y) && sim.getTopology().isRoofIntact(x, y));
            }
            window.put(shooter, new int[] {faction.ordinal(), onPost(sim, positions, shooter) ? 1 : 0, targetPost ? 1 : 0});
        }
    }

    private void flushWindow() {
        Faction[] factions = Faction.values();
        for (int[] contact : window.values()) {
            long[] row = mix.computeIfAbsent(factions[contact[0]], f -> new long[5]);
            row[0]++;
            row[1 + contact[1] * 2 + contact[2]]++;
        }
        window.clear();
    }

    private static boolean infantry(UnitType type) {
        return type.combatant && !type.isStatic() && !type.isTurret() && !type.isMech() && !type.isGroundVehicle()
                && !type.isDrone() && !type.isMachineFrame() && !type.isBasedAircraft() && !type.isDroneHub();
    }

    private static boolean onPost(BattleSimulation sim, FightingPositionIndex positions, long id) {
        UnitType type = sim.identity().type(id);
        if (type.isStatic() || type.isTurret()) return true;
        if (!infantry(type)) return false;
        if (sim.isDugIn(id)) return true;
        int x = sim.world().cellX(id);
        int y = sim.world().cellY(id);
        if (positions != null && positions.isFightingPositionCell(x, y)) return true;
        return sim.isRoofShielded(id);
    }

    private void sampleCounterattacks(BattleSimulation sim, int tick) {
        Map<Faction, CommanderSnapshot<?>> snapshots = new EnumMap<>(Faction.class);
        List<Body> bodies = new ArrayList<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.aliveMembers <= 0) continue;
            CommanderSnapshot<?> snapshot = snapshots.computeIfAbsent(squad.faction, sim::getCommanderSnapshot);
            CommandDirective directive = snapshot == null ? null : snapshot.directiveFor(squad.id);
            String reason = directive == null || directive.status() == CommandDirective.Status.RELEASED
                    || directive.status() == CommandDirective.Status.REJECTED ? "" : directive.reason();
            float sx = 0f;
            float sy = 0f;
            int members = 0;
            boolean dugIn = false;
            for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                long member = sim.squadMemberAt(squad.id, i);
                sx += sim.world().cellX(member);
                sy += sim.world().cellY(member);
                members++;
                if (sim.isDugIn(member)) dugIn = true;
            }
            if (members == 0) continue;
            ObjectiveAssignment order = squad.assignedObjective;
            boolean posted = dugIn || squad.defensePost != null
                    || (order != null && (order.mansPlace() || order.kind() == AssignmentKind.HOLD_NODE));
            bodies.add(new Body(squad.id, squad.faction, sx / members, sy / members, squad.aliveMembers, dugIn, reason,
                    order, posted));
        }
        for (Body holder : bodies) {
            if (!holding(holder.reason())) continue;
            holderSamples++;
            holderKinds.merge(holdKind(holder.reason()), 1, Integer::sum);
            if (holder.dugIn()) holderSamplesDugIn++;
            for (Body other : bodies) {
                if (other.reason().contains("COUNTERATTACK_") && holder.faction().hostileTo(other.faction())
                        && near(holder, other, PRESS_CELLS)) {
                    holderSamplesPressedByCounterattack++;
                    break;
                }
            }
        }
        Map<Faction, List<Body>> launched = new EnumMap<>(Faction.class);
        for (Body body : bodies) {
            if (launched(body.reason())) launched.computeIfAbsent(body.faction(), f -> new ArrayList<>()).add(body);
        }
        for (Faction side : Faction.values()) {
            List<Body> group = launched.get(side);
            Episode episode = open.get(side);
            if (group == null || group.isEmpty()) {
                if (episode != null) close(episode, sim, tick);
                continue;
            }
            if (episode == null) {
                episode = new Episode(side, tick);
                open.put(side, episode);
                episodes.add(episode);
            }
            episode.end = tick;
            Set<Integer> holdersNear = new HashSet<>();
            for (Body member : group) {
                if (episode.squads.add(member.squadId())) episode.aliveAtJoin.put(member.squadId(), member.alive());
                for (Body other : bodies) {
                    if (!side.hostileTo(other.faction()) || !near(member, other, MEET_CELLS)) continue;
                    episode.met = true;
                    if (other.posted()) episode.metPost = true;
                    if (holding(other.reason())) {
                        episode.metHold = true;
                        episode.holdKinds.add(holdKind(other.reason()));
                        if (other.dugIn()) episode.metDugInHold = true;
                        holdersNear.add(other.squadId());
                    }
                }
            }
            episode.holdersMet = Math.max(episode.holdersMet, holdersNear.size());
        }
    }

    private void close(Episode episode, BattleSimulation sim, int tick) {
        int lost = 0;
        for (Map.Entry<Integer, Integer> joined : episode.aliveAtJoin.entrySet()) {
            Squad squad = sim.getSquad(joined.getKey());
            int alive = squad == null ? 0 : Math.max(0, squad.aliveMembers);
            lost += Math.max(0, joined.getValue() - alive);
        }
        episode.lost = lost;
        open.remove(episode.side);
    }

    /**
     * A squad its commander keeps on ground its side took: the attacker's hold of taken ground ({@code HOLD_GROUND_*}), and
     * a story mission's militia holding a gain ({@code ..._HOLD_GAIN}) or a dispatch window.
     */
    static boolean holding(String reason) {
        return holdKind(reason) != null;
    }

    /**
     * Which kind of hold a reason is: {@code ground} (the attacker's hold of taken ground), {@code garrison} (the squad a
     * captured compound is sent to hold it), {@code gain} (a story mission's militia on a gain or a dispatch window), or
     * {@code null} for none.
     */
    static String holdKind(String reason) {
        if (reason.startsWith("HOLD_GROUND_")) return "ground";
        if (reason.equals("GARRISON_HOLD")) return "garrison";
        if (reason.endsWith("_HOLD_GAIN") || reason.endsWith("_DEFEND_DISPATCH_WINDOW")) return "gain";
        return null;
    }

    private static boolean launched(String reason) {
        return reason.endsWith("COUNTERATTACK_LAUNCH") || reason.endsWith("COUNTERATTACK_LAUNCHED");
    }

    private static boolean near(Body a, Body b, int cells) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return dx * dx + dy * dy <= (float) cells * cells;
    }

    /** Call once the battle has ended; closes open counter-attacks and reads retakes off the trace. */
    public JSONObject json(BattleSimulation sim, String trace) throws JSONException {
        for (Episode episode : new ArrayList<>(open.values())) close(episode, sim, sim.getSimTickIndex());
        List<Integer> retakes = new ArrayList<>();
        if (trace != null) {
            for (String line : trace.split("\n")) {
                if (!line.contains("retook it at tick")) continue;
                Matcher m = RETOOK.matcher(line);
                while (m.find()) {
                    int t = Integer.parseInt(m.group(1));
                    if (!retakes.contains(t)) retakes.add(t);
                }
            }
        }
        for (int t : compoundRetakes) {
            if (!retakes.contains(t)) retakes.add(t);
        }
        JSONObject mixJson = new JSONObject();
        long[] all = new long[5];
        for (Map.Entry<Faction, long[]> entry : mix.entrySet()) {
            mixJson.put(entry.getKey().name(), mixRow(entry.getValue()));
            for (int i = 0; i < 5; i++) all[i] += entry.getValue()[i];
        }
        mixJson.put("ALL", mixRow(all));
        int launches = 0, metHold = 0, metDugInHold = 0, metPost = 0, met = 0, retook = 0, retookMetHold = 0, retookOther = 0, lost = 0;
        JSONArray list = new JSONArray();
        for (Episode episode : episodes) {
            launches++;
            boolean won = false;
            for (int t : retakes) {
                if (t >= episode.start && t <= episode.end + 4 * SAMPLE_TICKS) won = true;
            }
            if (episode.metHold) metHold++;
            if (episode.metDugInHold) metDugInHold++;
            if (episode.metPost) metPost++;
            if (episode.met) met++;
            if (won) {
                retook++;
                if (episode.metHold) retookMetHold++;
                else retookOther++;
            }
            lost += episode.lost;
            list.put(new JSONObject().put("side", episode.side.name()).put("start", episode.start)
                    .put("end", episode.end).put("squads", episode.squads.size()).put("lost", episode.lost)
                    .put("met", episode.met).put("metPost", episode.metPost).put("metHold", episode.metHold).put("metDugInHold", episode.metDugInHold)
                    .put("holdKinds", new JSONArray(episode.holdKinds))
                    .put("holdersMet", episode.holdersMet).put("retook", won));
        }
        JSONObject losses = new JSONObject();
        if (trace != null) {
            Map<String, Integer> dead = new HashMap<>();
            for (String line : trace.split("\n")) {
                if (!line.contains("\"casualty\"")) continue;
                JSONObject event = new JSONObject(line);
                if (!"casualty".equals(event.optString("event")) || !event.optBoolean("combatant")) continue;
                dead.merge(event.optString("faction"), 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> entry : dead.entrySet()) losses.put(entry.getKey(), entry.getValue());
        }
        JSONObject notes = trace == null ? new JSONObject() : ConquestTrialEvidenceTest.counterattackNotes(trace);
        notes.remove("launchList");
        return new JSONObject().put("winner", sim.isComplete() && sim.getWinner() != null ? sim.getWinner().name()
                        : sim.isComplete() ? "none" : "timeout").put("ticks", sim.getSimTickIndex()).put("losses", losses)
                .put("fightMix", mixJson).put("counterattackNotes", notes).put("turns", new JSONArray(turns))
                .put("counterattacks", new JSONObject().put("launches", launches).put("met", met)
                        .put("metPost", metPost).put("metHold", metHold).put("metDugInHold", metDugInHold).put("retook", retook)
                        .put("retookMetHold", retookMetHold).put("retookOther", retookOther).put("lost", lost)
                        .put("retakeNotes", retakes.size() - compoundRetakes.size()).put("compoundRetakes", compoundRetakes.size()).put("episodes", list))
                .put("hold", new JSONObject().put("holderSamples", holderSamples).put("kinds", new JSONObject(holderKinds))
                        .put("dugInSamples", holderSamplesDugIn)
                        .put("pressedByCounterattack", holderSamplesPressedByCounterattack));
    }

    private static JSONObject mixRow(long[] row) throws JSONException {
        return new JSONObject().put("contacts", row[0]).put("openOpen", row[1]).put("openAtPost", row[2])
                .put("postAtOpen", row[3]).put("postPost", row[4]);
    }

    /** One greppable line: {@code [place-fight] <run> <json>}. */
    public String line(String runId, BattleSimulation sim, String trace) throws JSONException {
        return String.format(Locale.ROOT, "[place-fight] %s %s", runId, json(sim, trace).toString());
    }
}
