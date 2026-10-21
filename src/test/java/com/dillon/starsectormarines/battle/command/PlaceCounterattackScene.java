package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The defender counter-attacks a lost <em>compound</em> (`conquest-command.md`, Defender command): a place that is not a fort, because
 * the game is much larger than forts. One barracks taken by a party of marines, and the defender's Conquest commander
 * with a pool of patrols a long way south of it. Each loop changes one thing.
 *
 * <ul>
 *   <li>{@code retake}: frail holders inside. A probe goes first and watches from short of the barracks; the group is
 *       sized from what it observed, mustered, and launched together only with that strength at the staging cell.</li>
 *   <li>{@code outmatched}: thirty holders in plain sight and a small pool. The probe observes, and the group does not
 *       go: it stands down, the pool holds nothing for the place while it waits, and it is observed again after the
 *       wait.</li>
 *   <li>{@code repulsed}: a few holders in the yard and many unseen inside, none of whom can be killed. The probe sees
 *       the few; the push meets the many, fails, and the estimate is raised to what it met.</li>
 * </ul>
 *
 * <p>Verdicts are mechanics of the commander; who wins is in the metrics.
 */
public final class PlaceCounterattackScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 240;
    private static final int PULSE_TICKS = 75;
    /** The barracks: interior box, a wall ring one cell outside it, a two-cell door in the south wall. */
    private static final int LEFT = 70, TOP = 50, RIGHT = 89, BOTTOM = 64, DOOR_X = 79;
    private static final int POOL_Y = 185;
    private static final int POOL_SIZE = 6;
    private static final float TOUGH = 1_000_000f;
    private static final float FRAIL = 12f;
    /** An order ending this close (Chebyshev) to the barracks' middle is an order at the place. */
    private static final int AT_PLACE_CELLS = 10;

    @Override public String id() { return "place-counterattack"; }

    @Override public String label() {
        return "The defender counter-attacks a lost compound: a probe observes first, the group is sized from what it saw, "
                + "launches only with that strength, does not go against more than it can raise, and raises its estimate "
                + "after a repulse";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("retake", "outmatched", "repulsed")) reports.add(playOne(loop, frames));
        return reports;
    }

    private static NavigationGrid grid;
    private static CellTopology topology;

    private static void stamp() {
        grid = new NavigationGrid(WIDTH, HEIGHT);
        topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        for (int x = LEFT - 1; x <= RIGHT + 1; x++) {
            wall(x, TOP - 1);
            wall(x, BOTTOM + 1);
        }
        for (int y = TOP - 1; y <= BOTTOM + 1; y++) {
            wall(LEFT - 1, y);
            wall(RIGHT + 1, y);
        }
        for (int x = DOOR_X; x <= DOOR_X + 1; x++) {
            grid.setWalkableFloor(x, BOTTOM + 1);
            grid.setDoorway(x, BOTTOM + 1, true);
        }
        for (int y = TOP; y <= BOTTOM; y++) for (int x = LEFT; x <= RIGHT; x++) topology.setGroundKind(x, y, GroundKind.INDOOR);
    }

    private static void wall(int x, int y) {
        grid.setWalkable(x, y, false);
        topology.setGroundKind(x, y, GroundKind.INDOOR);
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        stamp();
        int patrols = loop.equals("outmatched") ? 4 : 8;
        SceneBuilder builder = SceneBuilder.over(grid, topology).missionCompletion(false);
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 3, 3, spec -> spec.moveSpeed(0f).health(TOUGH));
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, WIDTH - 3, HEIGHT - 3,
                spec -> spec.moveSpeed(0f).health(TOUGH));
        for (int i = 0; i < patrols; i++) {
            builder.squad("patrol-" + i).faction(Faction.DEFENDER).type(UnitType.MILITIA).size(POOL_SIZE)
                    .at(45 + 10 * i, POOL_Y).primary("weapon.smg").done();
        }
        switch (loop) {
            case "retake" -> builder.squad("holders").faction(Faction.MARINE).size(6).at(80, 56).primary("weapon.smg")
                    .health(FRAIL).stationary().done();
            case "outmatched" -> {
                builder.squad("holders").faction(Faction.MARINE).size(6).at(80, 56).primary("weapon.smg")
                        .health(TOUGH).stationary().done();
                for (int i = 0; i < 4; i++) {
                    builder.squad("yard-" + i).faction(Faction.MARINE).size(6).at(68 + 8 * i, 72).primary("weapon.smg")
                            .health(TOUGH).stationary().done();
                }
            }
            default -> {
                builder.squad("yard").faction(Faction.MARINE).size(4).at(80, 71).primary("weapon.smg")
                        .health(TOUGH).stationary().done();
                for (int i = 0; i < 2; i++) {
                    builder.squad("inside-" + i).faction(Faction.MARINE).size(6).at(73 + 12 * i, 53)
                            .primary("weapon.smg").health(TOUGH).stationary().done();
                }
            }
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        TacticalNode barracks = new TacticalNode(TacticalNode.Kind.BARRACKS, (LEFT + RIGHT) / 2, (TOP + BOTTOM) / 2,
                LEFT, TOP, RIGHT, BOTTOM, Faction.DEFENDER, 60, 0);
        sim.setTacticalMap(new TacticalMap(List.of(barracks)));
        sim.random().setSeed(20261020L);
        int[] pool = new int[patrols];
        Set<Integer> mobile = new TreeSet<>();
        for (int i = 0; i < patrols; i++) {
            pool[i] = world.squadId("patrol-" + i);
            mobile.add(pool[i]);
        }
        // The pool is the defender's starting patrol force, as a Conquest battle discloses it: a squad the counter-attack
        // gives back stays the commander's to use, rather than leaving its command with the order it was released from.
        ConquestDefenderCommand defenders = new ConquestDefenderCommand(
                new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, WIDTH, HEIGHT),
                new ConquestDefenderStartingForce(mobile, Map.of()));
        for (int i = 0; i < patrols; i++) {
            sim.claimSquadCommand(pool[i], CommandAuthority.MISSION_COMMAND, defenders.strategyId(), "scene patrol pool");
        }
        sim.setAutonomousCommander(Faction.DEFENDER, defenders, ConquestCommandDisclosure.INSTANCE);

        Trace trace = new Trace(pool);
        int ticks = switch (loop) {
            case "retake" -> 9000;
            default -> 12000;
        };
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 60, tick -> String.format(Locale.ROOT, "%s / tick %d / %s", loop, tick, trace.caption()))
                .run(ticks);
        return report(loop, ticks, trace);
    }

    private SceneReport report(String loop, int ticks, Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("compound-lost", t.lostTick >= 0, "the barracks read marine-held from tick " + t.lostTick));
        int probe = t.firstNoteTick(" probes: ");
        int observed = t.firstNoteTick(" observed it (");
        int muster = t.firstNoteTick(" musters: ");
        Launch first = t.launches.isEmpty() ? null : t.launches.get(0);
        verdicts.add(Verdict.of("probe-goes-first", probe >= 0 && observed >= probe && (muster < 0 || muster >= observed)
                        && (first == null || first.tick >= observed),
                "probe at tick " + probe + ", observed at " + observed + ", first muster " + muster + ", first launch "
                        + (first == null ? -1 : first.tick)));
        verdicts.add(Verdict.of("nothing-decided-unobserved", t.unobservedDecisions == 0,
                t.unobservedDecisions + " musters or outmatched verdicts with no observation in the minute before"));
        verdicts.add(Verdict.of("sized-from-observed-holders", t.sizingErrors.isEmpty() && t.sizings > 0,
                t.sizings + " sizings checked against the observed holders and losses; mismatches " + t.sizingErrors));
        switch (loop) {
            case "outmatched" -> {
                verdicts.add(Verdict.of("does-not-launch-against-more-than-it-can-raise", t.launches.isEmpty()
                        && t.count("(outmatched: ") >= 1, t.launches.size() + " launches, " + t.count("(outmatched: ")
                        + " outmatched decisions"));
                int stoodDown = t.firstNoteTick(" stands down (");
                verdicts.add(Verdict.of("stands-down-and-observes-again-after-the-wait", stoodDown >= 0
                                && t.count(" probes: ") >= 2 && t.firstNoteTick(" abandoned after ") < 0,
                        "stood down at tick " + stoodDown + ", " + t.count(" stands down (") + " stand-downs, "
                                + t.count(" probes: ") + " probes, abandoned at " + t.firstNoteTick(" abandoned after ")));
                verdicts.add(Verdict.of("holds-nothing-back-while-it-waits", stoodDown >= 0 && t.heldWhileWaiting == 0
                                && t.waitingSamples > 0,
                        t.heldWhileWaiting + " pool squad-samples held for the counter-attack over " + t.waitingSamples
                                + " samples while the place waited"));
                verdicts.add(Verdict.of("no-squad-sent-at-the-place", t.firstAtPlaceTick == Integer.MAX_VALUE,
                        "first pool order at the place at tick " + t.firstAtPlaceTick + " (" + t.firstAtPlaceReason + ")"));
            }
            default -> {
                verdicts.add(Verdict.of("launches-only-with-the-strength-it-believes-it-needs", !t.launches.isEmpty()
                                && t.launches.stream().allMatch(l -> l.strength >= PlaceCounterattack.COMMIT_SHARE * l.required),
                        t.launches.isEmpty() ? "no launch" : "launches " + t.launchText()));
                verdicts.add(Verdict.of("launched-together", first != null && first.squads.equals(first.musteredJustBefore),
                        first == null ? "no launch" : "launched " + first.squads + " on tick " + first.tick
                                + "; the group the pulse before " + first.musteredJustBefore));
                verdicts.add(Verdict.of("no-squad-sent-at-the-place-before-the-launch",
                        first != null && t.firstAtPlaceTick >= first.tick,
                        "first pool order at the place at tick " + t.firstAtPlaceTick + " (" + t.firstAtPlaceReason + ")"
                                + (first == null ? "" : ", launch at tick " + first.tick)));
                if (loop.equals("repulsed")) {
                    Matcher m = Pattern.compile("estimate after the repulse: (\\d+) holders believed, was (\\d+)")
                            .matcher(t.joined());
                    boolean found = m.find();
                    int after = found ? Integer.parseInt(m.group(1)) : -1;
                    int before = found ? Integer.parseInt(m.group(2)) : -1;
                    int firstRequired = t.requireds.isEmpty() ? -1 : t.requireds.get(0);
                    int nextRequired = t.requireds.size() > 1 ? t.requireds.get(1) : -1;
                    verdicts.add(Verdict.of("estimate-raised-after-the-repulse", found && after >= before
                                    && (nextRequired < 0 || nextRequired > firstRequired),
                            found ? "holders believed " + before + " before the push, " + after + " after it; believed "
                                    + "needed " + firstRequired + " then " + nextRequired
                                    : "no repulse (launches " + t.launches.size() + ")"));
                }
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("lostTick", t.lostTick);
        metrics.put("probeTick", probe);
        metrics.put("observedTick", observed);
        metrics.put("observations", t.count(" observed it ("));
        metrics.put("musterTick", muster);
        metrics.put("launches", t.launches.size());
        metrics.put("firstLaunchTick", first == null ? -1 : first.tick);
        metrics.put("firstLaunchStrength", first == null ? -1 : first.strength);
        metrics.put("firstLaunchRequired", first == null ? -1 : first.required);
        metrics.put("outmatched", t.count("(outmatched: "));
        metrics.put("giveUps", t.count(" gives up ("));
        metrics.put("retakenTick", t.retakenTick);
        metrics.put("abandonedTick", t.firstNoteTick(" abandoned after "));
        metrics.put("standDowns", t.count(" stands down ("));
        metrics.put("poolAlive", t.poolAlive);
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    private record Launch(int tick, int strength, int required, Set<Integer> squads, Set<Integer> musteredJustBefore) {}

    private static final Pattern LAUNCH = Pattern.compile("launches \\(.*\\): (\\d+) squads, strength (\\d+) against (\\d+) believed");
    private static final Pattern OBSERVED = Pattern.compile("observed it \\(.*\\): (\\d+) holders believed, (\\d+) remembered");
    private static final Pattern SIZED = Pattern.compile("believed to take (\\d+) \\((\\d+) holders, (\\d+) losses\\)");
    private static final Pattern WAIT = Pattern.compile(" stands down \\(.*observed again from tick (\\d+)");
    private static final Pattern OUTMATCHED = Pattern.compile("outmatched: believed to take (\\d+)");

    private static final class Trace implements TickObserver {
        final int[] pool;
        final List<Integer> noteTicks = new ArrayList<>();
        final List<String> noteText = new ArrayList<>();
        final List<Launch> launches = new ArrayList<>();
        final List<Integer> requireds = new ArrayList<>();
        final List<String> sizingErrors = new ArrayList<>();
        int sizings;
        int unobservedDecisions;
        int lastObservedTick = Integer.MIN_VALUE;
        float lastObservedHolders = -1f;
        float lastObservedLosses = -1f;
        Set<Integer> musteringBefore = new TreeSet<>();
        int lostTick = -1;
        int retakenTick = -1;
        int firstAtPlaceTick = Integer.MAX_VALUE;
        String firstAtPlaceReason = "none";
        int poolAlive;
        /** While a stood-down place waits: samples taken, and pool squads still holding a counter-attack order in them. */
        int waitingSamples;
        int heldWhileWaiting;
        private int waitFrom = -1;
        private int waitEnd = -1;
        private int lastSummaryTick = -1;
        private String state = "";

        Trace(int[] pool) {
            this.pool = pool;
        }

        String caption() { return state; }

        String joined() { return String.join("\n", noteText); }

        String launchText() {
            StringBuilder out = new StringBuilder();
            for (Launch l : launches) out.append("[tick ").append(l.tick).append(": ").append(l.strength).append(" of ")
                    .append(l.required).append("] ");
            return out.toString().trim();
        }

        int firstNoteTick(String needle) {
            for (int i = 0; i < noteText.size(); i++) if (noteText.get(i).contains(needle)) return noteTicks.get(i);
            return -1;
        }

        int count(String needle) {
            int n = 0;
            for (String text : noteText) if (text.contains(needle)) n++;
            return n;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (CompoundService.Record record : sim.getCompoundService().getRecords()) {
                if (record.state == CompoundService.CompoundState.MARINE_HELD && lostTick < 0) lostTick = tick;
                if (record.state == CompoundService.CompoundState.DEFENDER_HELD && lostTick >= 0 && retakenTick < 0) {
                    retakenTick = tick;
                }
            }
            CommanderSnapshot<?> snapshot = sim.getCommanderSnapshot(Faction.DEFENDER);
            if (snapshot == null) return;
            boolean fresh = snapshot.tick() != lastSummaryTick;
            Set<Integer> launching = new TreeSet<>();
            Set<Integer> mustering = new TreeSet<>();
            int alive = 0;
            Set<Integer> aliveNow = new TreeSet<>();
            for (int id : pool) {
                Squad squad = sim.getSquad(id);
                if (squad == null || squad.aliveMembers <= 0) continue;
                alive++;
                aliveNow.add(id);
                CommandDirective directive = snapshot.directiveFor(id);
                String reason = directive == null ? "" : directive.reason();
                if (reason.equals("DEFENDER_COUNTERATTACK_LAUNCH")) launching.add(id);
                // The group the pulse before a launch, in whatever stage it waited: reserve, probe or muster.
                if (reason.startsWith("DEFENDER_COUNTERATTACK_") && !reason.equals("DEFENDER_COUNTERATTACK_LAUNCH")) {
                    mustering.add(id);
                }
                ObjectiveAssignment order = squad.assignedObjective;
                boolean watching = reason.equals("DEFENDER_COUNTERATTACK_PROBE") || order != null
                        && order.targetRadiusCells() == PlaceCounterattack.PROBE_RADIUS_CELLS
                        && reason.equals("DEFENDER_COUNTERATTACK_MUSTER");
                if (!watching && order != null && order.targetCellX() >= 0
                        && Math.max(Math.abs(order.targetCellX() - (LEFT + RIGHT) / 2),
                        Math.abs(order.targetCellY() - (TOP + BOTTOM) / 2)) <= AT_PLACE_CELLS && tick < firstAtPlaceTick) {
                    firstAtPlaceTick = tick;
                    firstAtPlaceReason = reason;
                }
            }
            poolAlive = alive;
            // One pulse for the stand-down's own orders to reach the squads, then nothing is the counter-attack's.
            if (waitFrom >= 0 && tick > waitFrom + 2 * PULSE_TICKS && tick < waitEnd) {
                waitingSamples++;
                heldWhileWaiting += mustering.size();
            }
            if (!fresh) return;
            lastSummaryTick = snapshot.tick();
            for (String summary : snapshot.objectiveSummaries()) {
                if (!summary.startsWith("counter-attack for ") && !summary.startsWith("counter-attack reserve for ")) continue;
                noteTicks.add(tick);
                noteText.add(summary);
                read(tick, summary, launching, aliveNow);
            }
            musteringBefore = mustering;
            state = String.format(Locale.ROOT, "launching %d, launches %d%s", launching.size(), launches.size(),
                    retakenTick >= 0 ? ", retaken" : lostTick >= 0 ? ", lost" : "");
        }

        private void read(int tick, String text, Set<Integer> launching, Set<Integer> alive) {
            Matcher o = OBSERVED.matcher(text);
            if (o.find()) {
                lastObservedTick = tick;
                lastObservedHolders = Float.parseFloat(o.group(1));
                lastObservedLosses = Float.parseFloat(o.group(2));
            }
            Matcher s = SIZED.matcher(text);
            Matcher out = OUTMATCHED.matcher(text);
            boolean decision = s.find() | out.find();
            if (decision && tick - lastObservedTick > PlaceCounterattack.OBSERVATION_FRESH_TICKS) unobservedDecisions++;
            if (text.contains(" musters: ") && s.find(0)) {
                int required = Integer.parseInt(s.group(1));
                int holders = Integer.parseInt(s.group(2));
                int losses = Integer.parseInt(s.group(3));
                requireds.add(required);
                sizings++;
                int failures = count(" gives up (");
                // The note rounds the figures it prints; the sizing it reports must be what those figures give.
                int lo = PlaceCounterattack.required(Math.max(0, holders - 0.5f), Math.max(0, losses - 0.5f), failures,
                        PlaceCounterattack.MIN_STRENGTH);
                int hi = PlaceCounterattack.required(holders + 0.5f, losses + 0.5f, failures, PlaceCounterattack.MIN_STRENGTH);
                if (required < lo || required > hi) sizingErrors.add("tick " + tick + ": " + required + " not in [" + lo
                        + "," + hi + "]");
                if (tick == lastObservedTick && Math.round(lastObservedHolders) != holders) {
                    sizingErrors.add("tick " + tick + ": sized from " + holders + " holders, observed "
                            + Math.round(lastObservedHolders));
                }
            }
            if (out.find(0)) {
                requireds.add(Integer.parseInt(out.group(1)));
                sizings++;
            }
            Matcher w = WAIT.matcher(text);
            if (w.find()) {
                waitFrom = tick;
                waitEnd = Integer.parseInt(w.group(1));
            }
            Matcher m = LAUNCH.matcher(text);
            if (m.find()) {
                Set<Integer> stillAlive = new TreeSet<>(musteringBefore);
                stillAlive.removeIf(id -> !alive.contains(id));
                launches.add(new Launch(tick, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                        new TreeSet<>(launching), stillAlive));
            }
        }
    }
}
