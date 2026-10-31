package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Cell;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime;
import com.dillon.starsectormarines.battle.cordon.CordonBeats;
import com.dillon.starsectormarines.battle.cordon.CordonBoreEscalation;
import com.dillon.starsectormarines.battle.cordon.CordonCamp;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys.Hold;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys.Phase;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys.Reading;
import com.dillon.starsectormarines.battle.cordon.CordonRedoubt;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonTown;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.i18n.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Cordon's rows in the shared mission picture ({@code cordon-nouns.md}, The player's picture). On the Road, by
 * phase: Hold Ain Tafrant, beaconed at the town's staging area; Bring the people out, beaconed at the gap, the town's
 * gate and each convoy on the road; and once the town is finished, Fall back behind the line, beaconed at the rally
 * place. The line is not the Road's objective and has no row. On the Line: the line, beaconed at any redoubt calling. Each row's focus is the place the company is wanted now, so Locate and the edge indicator say where
 * to go: a convoy held for an escort or by the swarm first, then one on the road, the gate while one loads, and the
 * gap while the next is still to come. Read-only, and only what the allies' radio would tell the company.
 */
public final class CordonChecklist {
    private CordonChecklist() { }

    /** One redoubt as the line's row reads it. */
    record Post(int number, CordonRedoubt.State state, int x, int y) { }

    public static List<Item> items(BattleSimulation sim, CordonSequence cordon) {
        int tick = sim.getSimTickIndex();
        List<Item> items = new ArrayList<>();
        CordonConvoys convoys = cordon.convoys();
        if (cordon.variant() == CordonVariant.ROAD) {
            CordonTown town = cordon.town();
            List<Reading> readings = convoys == null ? List.of() : convoys.readings(sim);
            if (town != null) {
                int standing = 0;
                for (int squad : town.teams()) standing += sim.squadMemberCount(squad);
                int aboard = 0, seats = 0;
                for (Reading r : readings) {
                    if (r.phase() != Phase.LOADING) continue;
                    aboard += r.aboard();
                    seats += r.seats();
                }
                items.add(town(cordon.outcome(), cordon.townFinished(), new Town(town.state(), town.calling(standing),
                        town.staging().cells(), town.staging().centreX(), town.staging().centreY(),
                        convoys == null ? 0 : convoys.town(), standing, town.garrison(), aboard, seats)));
            }
            if (convoys != null && convoys.corridor().size() > 1) {
                List<int[]> corridor = convoys.corridor();
                CordonCamp camp = convoys.camp();
                items.add(convoys(cordon.outcome(), readings, new People(convoys.peopleOut(), convoys.peopleLost(),
                                convoys.peopleTotal(), cordon.peopleNeeded()),
                        corridor.get(Math.min(convoys.gapIndex(), corridor.size() - 1)),
                        corridor.get(corridor.size() - 1),
                        camp == null ? null : new Camp(camp.centreX(), camp.centreY(), convoys.sheltered(),
                                convoys.unloading()), tick));
            }
            CordonBoreEscalation escalation = cordon.escalation();
            if (escalation != null) {
                Item bores = lineBores(cordon.outcome(), cordon.withdrawn(), escalation.surging(),
                        escalation.readings(sim));
                if (bores != null) items.add(bores);
            }
            int[] rally = cordon.rally();
            boolean offered = cordon.pullOutOffered() && !cordon.fallingBack();
            if ((cordon.fallingBack() || offered) && rally != null) {
                items.add(fallBack(cordon.outcome(), offered, cordon.withdrawn(), rally,
                        cordon.pullOut() == null ? List.of() : cordon.pullOut().cells()));
            }
        } else {
            List<Post> posts = new ArrayList<>();
            for (int i = 0; i < cordon.redoubts().size(); i++) {
                CordonRedoubt r = cordon.redoubts().get(i);
                posts.add(new Post(r.number(), r.state(), r.centreX(), r.centreY()));
            }
            items.add(line(cordon.outcome(), cordon.endBeat(), posts, cordon.fallen(),
                    cordon.timetable().allowedFallen(), tick, ticks(cordon.timetable().windowSeconds())));
        }
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

    /** The townspeople as the Road's people row reads them: out, lost, how many the town had and how many must come out. */
    record People(int out, int lost, int population, int needed) { }

    /** The safe camp the convoys bring the people to: its middle, the people sheltering there, and whether a truck is letting people down in it. */
    record Camp(int x, int y, int sheltered, boolean unloading) { }

    /** As {@link #convoys(CordonSequence.Outcome, List, People, int[], int[], Camp, int)} on a Road with no camp. */
    static Item convoys(CordonSequence.Outcome outcome, List<Reading> readings, People people, int[] gap, int[] gate,
                        int tick) {
        return convoys(outcome, readings, people, gap, gate, null, tick);
    }

    /**
     * The Road's second row, Bring the people out: the townspeople out against the briefing's number, beaconed at the
     * gap, the town's gate, the safe camp and each convoy on the road; its focus the camp while a truck is letting
     * people down there. Complete once enough are out (or the Road is won), failed once too few are left alive to make
     * the number (or the Road is lost).
     *
     * @param camp the safe camp, or {@code null} where the convoys bring the people back to where they set out
     */
    static Item convoys(CordonSequence.Outcome outcome, List<Reading> readings, People people, int[] gap, int[] gate,
                        Camp camp, int tick) {
        int needed = people.needed();
        State state = people.out() >= needed || outcome == CordonSequence.Outcome.HELD ? State.COMPLETE
                : people.population() - people.lost() < needed || outcome == CordonSequence.Outcome.BROKEN ? State.FAILED
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
        if (camp != null) {
            markers.add(new Marker("cordon-camp", Strings.get("cordonHudCamp"), camp.x(), camp.y(),
                    Strings.format("cordonHudCampStatus", camp.sheltered()), state));
            if (camp.unloading() && state == State.ACTIVE) focus = "cordon-camp";
        }
        String phase = state == State.COMPLETE ? Strings.get("cordonHudConvoysDone")
                : state == State.FAILED ? Strings.get("cordonHudConvoysFailed")
                : wanted == null ? Strings.get("cordonHudConvoysDone") : convoyStatus(wanted, tick);
        String blocker = state != State.ACTIVE || wanted == null ? "" : switch (wanted.hold()) {
            case ESCORT -> Strings.format("cordonHudBlockerEscort", wanted.number());
            case BLOCKED -> Strings.format("cordonHudBlockerBlocked", wanted.number());
            case NONE -> "";
        };
        return new Item("cordon-2-convoys", Strings.get("cordonHudConvoysName"), state, phase,
                Strings.get("cordonHudConvoysSteps"),
                Strings.get(state == State.ACTIVE ? "cordonHudConvoysInstruction" : "cordonHudConvoysClosed"),
                Strings.format("cordonHudConvoysTally", people.out(), people.population(), needed, people.lost()),
                needed <= 0 ? -1 : Math.min(1f, people.out() / (float) needed), blocker, markers, focus);
    }

    /**
     * The town as its row reads it: how the staging area stands, whether the town is calling, the staging area's cells
     * and middle, the townspeople still trapped, and the defenders standing of those it stood up with.
     */
    record Town(CordonTown.State state, boolean calling, List<int[]> cells, int x, int y, int trapped, int standing,
                int garrison, int aboard, int seats) { }

    /**
     * The Road's first row, Hold Ain Tafrant: beaconed at the staging area (its cells the beacon's footprint), saying
     * how it stands (holding, pressed, overrun) and whether the town is calling, tallying the townspeople still
     * trapped, the boarding of a convoy at the gate and the defenders standing. Its focus is the staging area while it
     * runs; it says so when the swarm is in it or the town is calling. Complete once the town is empty, failed once it
     * is abandoned (its staging area overrun) or the company is lost.
     *
     * @param finished why the town is finished ({@code town_empty}, {@code town_abandoned}), or {@code null}
     */
    static Item town(CordonSequence.Outcome outcome, String finished, Town town) {
        boolean overrun = "town_abandoned".equals(finished);
        State state = overrun ? State.FAILED
                : finished != null ? State.COMPLETE
                : outcome == CordonSequence.Outcome.BROKEN ? State.FAILED : State.ACTIVE;
        String status = town.state() == CordonTown.State.HOLDING && town.calling()
                ? Strings.get("cordonHudTownCalling") : Strings.named("cordonHudStagingState", town.state().name());
        List<Cell> footprint = new ArrayList<>();
        for (int[] c : town.cells()) footprint.add(new Cell(c[0], c[1]));
        String id = "cordon-staging";
        List<Marker> markers = List.of(new Marker(id, Strings.get("cordonHudStaging"), town.x(), town.y(), status,
                overrun ? State.FAILED : state, footprint));
        String phase = state == State.COMPLETE ? Strings.get("cordonHudTownDone")
                : state == State.FAILED ? Strings.get("cordonHudTownLost") : status;
        String blocker = state != State.ACTIVE ? ""
                : town.state() == CordonTown.State.PRESSED ? Strings.get("cordonHudBlockerStaging")
                : town.calling() ? Strings.get("cordonHudBlockerTown") : "";
        return new Item("cordon-1-town", Strings.get("cordonHudTownName"), state, phase,
                Strings.get("cordonHudTownSteps"),
                Strings.get(state == State.ACTIVE ? "cordonHudTownInstruction" : "cordonHudTownClosed"),
                town.seats() > 0
                        ? Strings.format("cordonHudTownTallyBoarding", town.trapped(), town.aboard(), town.seats(),
                                town.standing(), town.garrison())
                        : Strings.format("cordonHudTownTally", town.trapped(), town.standing(), town.garrison()),
                town.garrison() <= 0 ? -1 : Math.min(1f, town.standing() / (float) town.garrison()), blocker,
                markers, state == State.ACTIVE ? id : "");
    }

    /** The fall-back row with no pull-out zone and nothing offered. */
    static Item fallBack(CordonSequence.Outcome outcome, boolean withdrawn, int[] rally) {
        return fallBack(outcome, false, withdrawn, rally, List.of());
    }

    /**
     * The Road's last row ({@code cordon-nouns.md}, The pull-out), beaconed at the pull-out zone (its cells the beacon's
     * footprint; the rally place where there is none). While pulling out is {@code offered} and not yet ordered it is
     * the warning that the company has done enough and tells the player how to pull out: order a squad to the zone.
     * Once the company is falling back it asks for every squad behind the line and some in the zone, complete once
     * they are.
     */
    static Item fallBack(CordonSequence.Outcome outcome, boolean offered, boolean withdrawn, int[] rally,
                         List<int[]> zone) {
        State state = withdrawn || outcome == CordonSequence.Outcome.HELD ? State.COMPLETE
                : outcome == CordonSequence.Outcome.BROKEN ? State.FAILED : State.ACTIVE;
        String id = "cordon-rally";
        String phase = state != State.ACTIVE ? Strings.get("cordonHudFallBackDone")
                : Strings.get(offered ? "cordonHudPullOutOffered" : "cordonHudFallBackPhase");
        List<Cell> footprint = new ArrayList<>();
        for (int[] c : zone) footprint.add(new Cell(c[0], c[1]));
        String name = Strings.get(zone.isEmpty() ? "cordonHudRally" : "cordonHudPullOutZone");
        List<Marker> markers = List.of(new Marker(id, name, rally[0], rally[1], phase, state, footprint));
        String blocker = state != State.ACTIVE ? ""
                : Strings.get(offered ? "cordonHudBlockerPullOut" : zone.isEmpty() ? "cordonHudBlockerFallBack"
                        : "cordonHudBlockerFallBackZone");
        return new Item("cordon-3-fall-back", Strings.get(offered ? "cordonHudPullOutName" : "cordonHudFallBackName"),
                state, phase, Strings.get(offered ? "cordonHudPullOutSteps" : "cordonHudFallBackSteps"),
                Strings.get(offered ? "cordonHudPullOutInstruction" : "cordonHudFallBackInstruction"), "", -1,
                blocker, markers, state == State.ACTIVE ? id : "");
    }

    /**
     * The Road's line under pressure ({@code cordon-nouns.md}, Line bores): shown from the first site stirring, each
     * stirring site beaconed with the time until it opens and each open line bore with its number; its focus is the
     * site opening soonest. Once the final surge comes it says so and asks for the company back behind the line;
     * complete once the company is behind it, failed with the Road. {@code null} while nothing has stirred.
     */
    static Item lineBores(CordonSequence.Outcome outcome, boolean withdrawn, boolean surging,
                          List<CordonBoreEscalation.Reading> readings) {
        List<Marker> markers = new ArrayList<>();
        String focus = "";
        int soonest = Integer.MAX_VALUE, open = 0;
        boolean any = surging;
        for (CordonBoreEscalation.Reading r : readings) {
            CordonBoreEscalation.Site site = r.site();
            String id = "cordon-line-bore-" + site.order();
            String where = switch (site.kind()) {
                case BETWEEN -> Strings.format("cordonHudLineBoreBetween", site.west(), site.east());
                case FRONT -> Strings.format("cordonHudLineBoreBefore", site.west());
                case TOWN_BEYOND, TOWN_FLANK -> Strings.get("cordonHudLineBoreTown");
            };
            switch (r.status()) {
                case STIRRING -> {
                    any = true;
                    markers.add(new Marker(id, Strings.get("cordonHudLineBore"), site.x(), site.y(),
                            Strings.format("cordonHudLineBoreStirring", where, clock(r.ticksLeft())), State.ACTIVE));
                    if (r.ticksLeft() < soonest) {
                        soonest = r.ticksLeft();
                        focus = id;
                    }
                }
                case OPEN -> {
                    any = true;
                    open++;
                    markers.add(new Marker(id, Strings.get("cordonHudLineBore"), site.x(), site.y(),
                            Strings.format("cordonHudLineBoreOpen", where), State.ACTIVE));
                }
                case COLLAPSED -> any = true;
                case PLANNED -> { }
            }
        }
        if (!any) return null;
        State state = withdrawn && surging || outcome == CordonSequence.Outcome.HELD ? State.COMPLETE
                : outcome == CordonSequence.Outcome.BROKEN ? State.FAILED : State.ACTIVE;
        if (state != State.ACTIVE) {
            markers.clear();
            focus = "";
        }
        String wanted = focus;
        String phase = state == State.COMPLETE ? Strings.get("cordonHudFallBackDone")
                : state == State.FAILED ? Strings.get("cordonBroken")
                : surging ? Strings.get("cordonHudLineBoresSurge")
                : !wanted.isEmpty() ? markers.stream().filter(m -> m.id().equals(wanted)).findFirst()
                        .map(Marker::status).orElse("")
                : Strings.format("cordonHudLineBoresQuiet", open);
        return new Item("cordon-2b-line-bores", Strings.get("cordonHudLineBoresName"), state, phase,
                Strings.get("cordonHudLineBoresSteps"), Strings.get("cordonHudLineBoresInstruction"),
                Strings.format("cordonHudLineBoresTally", open, readings.size()), -1,
                state == State.ACTIVE && surging ? Strings.get("cordonHudBlockerSurge") : "", markers, focus);
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
            case WAITING -> r.departTick() < 0 ? Strings.format("cordonHudConvoyUncalled", r.number())
                    : Strings.format("cordonHudConvoyDue", r.number(), clock(r.departTick() - tick));
            case INBOUND -> Strings.format(r.hold() == Hold.ESCORT ? "cordonHudConvoyHeld"
                    : r.hold() == Hold.BLOCKED ? "cordonHudConvoyBlocked" : "cordonHudConvoyInbound", r.number());
            case LOADING -> Strings.format("cordonHudConvoyLoading", r.number());
            case OUTBOUND -> Strings.format(r.hold() == Hold.ESCORT ? "cordonHudConvoyHeld"
                    : r.hold() == Hold.BLOCKED ? "cordonHudConvoyBlocked" : "cordonHudConvoyOutbound", r.number());
            case OUT -> Strings.format("cordonHudConvoyOut", r.number());
            case LOST -> Strings.format("cordonHudConvoyLost", r.number());
        };
    }

    /**
     * The Line's row: beaconed at any redoubt calling, the first of them its focus, tallying the redoubts lost against
     * those allowed and the window's time left. Only the line's own break fails it; a wiped company does too.
     */
    static Item line(CordonSequence.Outcome outcome, String endBeat, List<Post> posts, int fallen, int allowedFallen,
                     int tick, int windowTicks) {
        boolean broken = outcome == CordonSequence.Outcome.BROKEN && endBeat != null;
        State state = outcome == CordonSequence.Outcome.HELD ? State.COMPLETE : broken ? State.FAILED : State.ACTIVE;
        List<Marker> markers = new ArrayList<>();
        List<Integer> calling = new ArrayList<>();
        for (Post p : posts) {
            if (p.state() != CordonRedoubt.State.CALLING) continue;
            calling.add(p.number());
            markers.add(new Marker("cordon-redoubt-" + p.number(), Strings.format("cordonHudRedoubt", p.number()),
                    p.x(), p.y(), Strings.named("cordonHudRedoubtState", p.state().name()), State.ACTIVE));
        }
        String focus = state != State.ACTIVE || calling.isEmpty() ? "" : "cordon-redoubt-" + calling.get(0);
        int left = Math.max(0, windowTicks - tick);
        String phase = state == State.COMPLETE ? Strings.get("cordonHeld")
                : state == State.FAILED ? Strings.get("cordonBroken")
                : !calling.isEmpty() ? Strings.format("cordonHudRedoubtCalling", calling.get(0))
                : Strings.get("cordonHudLineHolding");
        String blocker = state != State.ACTIVE || calling.isEmpty() ? ""
                : calling.size() == 1 ? Strings.format("cordonHudBlockerCalling", calling.get(0))
                : Strings.format("cordonHudBlockerCallingMany", joined(calling));
        return new Item("cordon-2-line", Strings.get("cordonHudLineName"), state, phase,
                Strings.get("cordonHudLineSteps"), Strings.get("cordonHudLineInstruction"),
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
