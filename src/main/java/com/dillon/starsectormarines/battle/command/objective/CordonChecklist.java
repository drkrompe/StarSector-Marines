package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime;
import com.dillon.starsectormarines.battle.cordon.CordonBeats;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys.Hold;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys.Phase;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys.Reading;
import com.dillon.starsectormarines.battle.cordon.CordonRedoubt;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.i18n.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Cordon's rows in the shared mission picture ({@code cordon-nouns.md}, Flow): on the Road the relief convoys,
 * beaconed at the gap, the town's gate and each convoy on the road, and the line, beaconed at the gap's redoubts and
 * any redoubt calling. Each row's focus is the place the company is wanted now, so Locate and the edge indicator say
 * where to go: a convoy held for an escort or by the swarm first, then one on the road, the gate while one loads,
 * and the gap while the next is still to come. Read-only, and only what the allies' radio would tell the company.
 */
public final class CordonChecklist {
    private CordonChecklist() { }

    /** One redoubt as the line's row reads it. */
    record Post(int number, CordonRedoubt.State state, int x, int y, boolean gap) { }

    public static List<Item> items(BattleSimulation sim, CordonSequence cordon) {
        int tick = sim.getSimTickIndex();
        List<Item> items = new ArrayList<>();
        CordonConvoys convoys = cordon.convoys();
        if (cordon.variant() == CordonVariant.ROAD && convoys != null && convoys.corridor().size() > 1) {
            List<int[]> corridor = convoys.corridor();
            items.add(convoys(cordon.outcome(), convoys.readings(sim), cordon.timetable().convoysNeeded(),
                    convoys.peopleOut(), corridor.get(Math.min(convoys.gapIndex(), corridor.size() - 1)),
                    corridor.get(corridor.size() - 1), tick));
        }
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < cordon.redoubts().size(); i++) {
            CordonRedoubt r = cordon.redoubts().get(i);
            posts.add(new Post(r.number(), r.state(), r.centreX(), r.centreY(), cordon.gap().contains(i)));
        }
        items.add(line(cordon.variant(), cordon.outcome(), cordon.endBeat(), posts, cordon.fallen(),
                cordon.timetable().allowedFallen(), tick, ticks(cordon.timetable().windowSeconds())));
        // The rolled beats follow in the Optional section, in roll order.
        if (cordon.beats() != null) for (CordonBeats.Reading beat : cordon.beats().readings(sim)) items.add(beat(beat));
        return items;
    }

    /**
     * A rolled beat's Optional row: its briefing line, what it pays, and a beacon where it is while it runs. A beat
     * asking for Marines says so; one that lapsed is struck with the failed, as a side objective's is.
     */
    static Item beat(CordonBeats.Reading beat) {
        State state = switch (beat.status()) {
            case ACTIVE -> State.ACTIVE;
            case SUCCEEDED -> State.COMPLETE;
            case FAILED, LAPSED -> State.FAILED;
        };
        String phase = beat.status() == ObjectiveRuntime.Status.ACTIVE
                ? Strings.get(beat.calling() ? "cordonHudBeatCalling" : "cordonHudBeatWaiting")
                : Strings.named("cordonHudBeat", beat.status().name());
        String id = "cordon-beat-" + beat.key();
        List<Marker> markers = state == State.ACTIVE && beat.x() >= 0
                ? List.of(new Marker(id + "-site", beat.title(), beat.x(), beat.y(), phase, state)) : List.of();
        String pays = beat.credits() > 0 ? Strings.format("sideObjectivePays", beat.credits()) : "";
        return new Item(id, beat.title(), state, phase, beat.briefing(), pays.isEmpty() ? beat.briefing() : pays,
                pays, -1, "", markers, markers.isEmpty() ? "" : markers.get(0).id(), true);
    }

    static Item convoys(CordonSequence.Outcome outcome, List<Reading> readings, int needed, int peopleOut,
                        int[] gap, int[] gate, int tick) {
        int out = 0, lost = 0;
        for (Reading r : readings) {
            if (r.phase() == Phase.OUT) out++;
            else if (r.phase() == Phase.LOST) lost++;
        }
        State state = out >= needed || outcome == CordonSequence.Outcome.HELD ? State.COMPLETE
                : readings.size() - lost < needed || outcome == CordonSequence.Outcome.BROKEN ? State.FAILED
                : State.ACTIVE;
        Reading wanted = wanted(readings);
        String gapId = "cordon-gap", gateId = "cordon-gate";
        List<Marker> markers = new ArrayList<>();
        markers.add(new Marker(gapId, Strings.get("cordonHudGap"), gap[0], gap[1],
                Strings.get("cordonHudGapStatus"), state));
        markers.add(new Marker(gateId, Strings.get("cordonHudGate"), gate[0], gate[1],
                Strings.get("cordonHudGateStatus"), state));
        String focus = state != State.ACTIVE ? "" : gapId;
        for (Reading r : readings) {
            if (r.x() < 0 || r.phase() == Phase.OUT || r.phase() == Phase.LOST) continue;
            String id = "cordon-convoy-" + r.number();
            markers.add(new Marker(id, Strings.format("cordonHudConvoy", r.number()), r.x(), r.y(),
                    convoyStatus(r, tick), State.ACTIVE));
            if (r == wanted && r.phase() != Phase.LOADING) focus = id;
        }
        if (wanted != null && wanted.phase() == Phase.LOADING && state == State.ACTIVE) focus = gateId;
        String phase = state == State.COMPLETE ? Strings.get("cordonHudConvoysDone")
                : state == State.FAILED ? Strings.get("cordonHudConvoysFailed")
                : wanted == null ? Strings.get("cordonHudConvoysDone") : convoyStatus(wanted, tick);
        String blocker = state != State.ACTIVE || wanted == null ? "" : switch (wanted.hold()) {
            case ESCORT -> Strings.format("cordonHudBlockerEscort", wanted.number());
            case BLOCKED -> Strings.format("cordonHudBlockerBlocked", wanted.number());
            case NONE -> "";
        };
        return new Item("cordon-1-convoys", Strings.get("cordonHudConvoysName"), state, phase,
                Strings.get("cordonHudConvoysSteps"),
                Strings.get(state == State.ACTIVE ? "cordonHudConvoysInstruction" : "cordonHudConvoysClosed"),
                Strings.format("cordonHudConvoysTally", out, needed, lost, peopleOut),
                needed <= 0 ? -1 : Math.min(1f, out / (float) needed), blocker, markers, focus);
    }

    /**
     * The convoy the company is wanted at: the first held for an escort or by the swarm, else the first on the road,
     * else the first loading at the gate, else the next to come; {@code null} when none is left to run.
     */
    static Reading wanted(List<Reading> readings) {
        Reading moving = null, loading = null, waiting = null;
        for (Reading r : readings) {
            switch (r.phase()) {
                case INBOUND, OUTBOUND -> {
                    if (r.hold() != Hold.NONE) return r;
                    if (moving == null) moving = r;
                }
                case LOADING -> { if (loading == null) loading = r; }
                case WAITING -> { if (waiting == null) waiting = r; }
                default -> { }
            }
        }
        return moving != null ? moving : loading != null ? loading : waiting;
    }

    static String convoyStatus(Reading r, int tick) {
        return switch (r.phase()) {
            case WAITING -> Strings.format("cordonHudConvoyDue", r.number(), clock(r.departTick() - tick));
            case INBOUND -> Strings.format(r.hold() == Hold.ESCORT ? "cordonHudConvoyHeld"
                    : r.hold() == Hold.BLOCKED ? "cordonHudConvoyBlocked" : "cordonHudConvoyInbound", r.number());
            case LOADING -> Strings.format("cordonHudConvoyLoading", r.number());
            case OUTBOUND -> Strings.format(r.hold() == Hold.ESCORT ? "cordonHudConvoyHeld"
                    : r.hold() == Hold.BLOCKED ? "cordonHudConvoyBlocked" : "cordonHudConvoyOutbound", r.number());
            case OUT -> Strings.format("cordonHudConvoyOut", r.number());
            case LOST -> Strings.format("cordonHudConvoyLost", r.number());
        };
    }

    static Item line(CordonVariant variant, CordonSequence.Outcome outcome, String endBeat, List<Post> posts,
                     int fallen, int allowedFallen, int tick, int windowTicks) {
        boolean road = variant == CordonVariant.ROAD;
        // The line's own breaks fail this row; a Road lost on its convoys leaves the line as it stood.
        boolean broken = outcome == CordonSequence.Outcome.BROKEN && endBeat != null
                && !endBeat.startsWith("convoys");
        State state = outcome == CordonSequence.Outcome.HELD ? State.COMPLETE : broken ? State.FAILED : State.ACTIVE;
        List<Marker> markers = new ArrayList<>();
        List<Integer> calling = new ArrayList<>();
        String standingGap = "";
        for (Post p : posts) {
            if (p.state() == CordonRedoubt.State.CALLING) calling.add(p.number());
            if (!p.gap() && p.state() != CordonRedoubt.State.CALLING) continue;
            String id = "cordon-redoubt-" + p.number();
            State shown = p.state() == CordonRedoubt.State.FALLEN ? State.FAILED : State.ACTIVE;
            markers.add(new Marker(id, Strings.format(p.gap() ? "cordonHudGapRedoubt" : "cordonHudRedoubt", p.number()),
                    p.x(), p.y(), Strings.named("cordonHudRedoubtState", p.state().name()), shown));
            if (p.gap() && shown == State.ACTIVE && standingGap.isEmpty()) standingGap = id;
        }
        // A calling redoubt outranks a quiet gap redoubt for the focus.
        String focus = state != State.ACTIVE ? "" : !calling.isEmpty() ? "cordon-redoubt-" + calling.get(0) : standingGap;
        int left = Math.max(0, windowTicks - tick);
        String phase = state == State.COMPLETE ? Strings.get("cordonHeld")
                : state == State.FAILED ? Strings.get("cordonBroken")
                : !calling.isEmpty() ? Strings.format("cordonHudRedoubtCalling", calling.get(0))
                : Strings.get("cordonHudLineHolding");
        String blocker = state != State.ACTIVE || calling.isEmpty() ? ""
                : calling.size() == 1 ? Strings.format("cordonHudBlockerCalling", calling.get(0))
                : Strings.format("cordonHudBlockerCallingMany", joined(calling));
        return new Item("cordon-2-line", Strings.get(road ? "cordonHudGapName" : "cordonHudLineName"), state, phase,
                Strings.get(road ? "cordonHudGapSteps" : "cordonHudLineSteps"),
                Strings.get(road ? "cordonHudGapInstruction" : "cordonHudLineInstruction"),
                Strings.format("cordonHudLineTally", fallen, allowedFallen, clock(left)),
                windowTicks <= 0 ? -1 : Math.min(1f, tick / (float) windowTicks), blocker, markers, focus);
    }

    private static String joined(List<Integer> numbers) {
        StringBuilder out = new StringBuilder();
        for (int n : numbers) out.append(out.length() == 0 ? "" : ", ").append(n);
        return out.toString();
    }

    /** Ticks as m:ss, never below zero. */
    static String clock(int ticks) {
        int seconds = Math.max(0, (int) Math.ceil(ticks * BattleSimulation.TICK_DT));
        return Strings.format("cordonHudClock", seconds / 60, String.format(Locale.ROOT, "%02d", seconds % 60));
    }

    private static int ticks(int seconds) {
        return Math.round(seconds / BattleSimulation.TICK_DT);
    }
}
