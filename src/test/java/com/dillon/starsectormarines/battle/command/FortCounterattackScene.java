package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.battery.FireControl;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortKind;
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
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.bsp.FortStage;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The defender counter-attacks a fort it has lost (`conquest-command.md`, Defender command): a production battery
 * fort handed to the marines, a party of marines holding its bunker, and the defender's Conquest commander with a pool
 * of patrols a long way south of it. Each loop changes one thing.
 *
 * <ul>
 *   <li>{@code quiet}: the fort is the defender's and nobody comes. Nothing is held back and nothing is mustered.</li>
 *   <li>{@code retake}: frail holders. The defender holds its reserve, musters a group to what it believes the fort
 *       takes, stages it behind the fort and launches every member on one pulse; no squad is sent at the fort before
 *       the launch, and the reserve law's floor stays out of it. Whether the bunker is won back is a reading.</li>
 *   <li>{@code repulsed}: holders who cannot be killed. The counter-attack fails, falls back to its staging cell,
 *       waits before trying again, and after the bound gives the fort up for good.</li>
 * </ul>
 *
 * <p>The fort is handed over rather than fought for: the question is what the defending commander does about a fort it
 * has lost. Verdicts are mechanics of the commander; who wins is in the metrics.
 */
public final class FortCounterattackScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 240;
    private static final int FORT_X = 80;
    private static final int FORT_Y = 70;
    private static final int PULSE_TICKS = 75;
    /** "A few command pulses". */
    private static final int FORM_BOUND_TICKS = 6 * PULSE_TICKS;
    private static final int POOL = 8;
    private static final int POOL_SIZE = 6;
    private static final int POOL_Y = 185;
    private static final int HOLDERS = 6;
    private static final float TOUGH = 1_000_000f;
    private static final float FRAIL = 12f;
    /** An order ending this close to a fort aspect is an order at the fort. */
    private static final int AT_FORT_CELLS = 14;

    @Override public String id() { return "fort-counterattack"; }

    @Override public String label() {
        return "The defender counter-attacks a lost fort: a reserve held behind it, a group mustered in proportion, "
                + "staged and launched as one push, falling back when it fails and giving the fort up after the bound";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("quiet", "retake", "repulsed")) reports.add(playOne(loop, frames));
        return reports;
    }

    private record Terrain(NavigationGrid grid, CellTopology topology, List<DefensePost> posts, List<Doodad> doodads) {}

    private static Terrain stamp() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L), WIDTH, HEIGHT, 7L);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(grid, topology, null));
        ctx.put(BspKeys.BUILDINGS, BuildingFloodFill.populate(topology, 7L));
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL);
        // The main gate faces the pool, so the counter-attack comes in the way a relief would.
        FortStage.pinned(List.of(new FortStage.Pin(FortKind.BATTERY_FORT, FORT_X, FORT_Y, FORT_X, HEIGHT - 1)))
                .run(ctx);
        return new Terrain(grid, topology, List.copyOf(ctx.defensePosts), List.copyOf(ctx.doodads));
    }

    /** The bunker's cell, read from a throwaway build of the same stamp. */
    private static int[] bunker(BattleSimulation sim) {
        for (DefenceStructureFact fact : sim.defenceStructures()) {
            if (fact.ofFort() && fact.kind() == DefenceStructureFact.Kind.FIRE_CONTROL) return new int[]{fact.cellX(), fact.cellY()};
        }
        throw new IllegalStateException("the stamp stood no bunker");
    }

    /** The nearest walkable cell to the one asked for. */
    private static int[] walkable(BattleSimulation sim, int x, int y) {
        for (int r = 0; r < 20; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int cx = x + dx;
                    int cy = y + dy;
                    if (cx < 1 || cy < 1 || cx >= WIDTH - 1 || cy >= HEIGHT - 1) continue;
                    if (sim.getGrid().isWalkable(cx, cy)) return new int[]{cx, cy};
                }
            }
        }
        return new int[]{x, y};
    }

    /** Hands every aspect of the fort to the marines, as a capture would have. */
    private static void takeFort(BattleSimulation sim) {
        Fort fort = sim.forts().forts().get(0);
        for (Fort.Place place : fort.places()) seize(place.control());
        if (fort.battery() != null) seize(fort.battery().fireControl());
    }

    private static void seize(FireControl control) {
        if (control.holder() == Faction.MARINE) return;
        control.tick(0f, false, Faction.MARINE);
        control.tick(FireControl.CAPTURE_SECONDS + 1f, false, Faction.MARINE);
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean lost = !loop.equals("quiet");
        Terrain terrain = stamp();
        Terrain probe = stamp();
        BattleSimulation probeSim = SceneBuilder.over(probe.grid(), probe.topology()).posts(probe.posts())
                .doodads(probe.doodads()).missionCompletion(false).build().sim();
        int[] bunker = bunker(probeSim);
        SceneBuilder builder = SceneBuilder.over(terrain.grid(), terrain.topology()).posts(terrain.posts())
                .doodads(terrain.doodads()).missionCompletion(false);
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 3, 3, spec -> spec.moveSpeed(0f).health(TOUGH));
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, WIDTH - 3, HEIGHT - 3,
                spec -> spec.moveSpeed(0f).health(TOUGH));
        for (int i = 0; i < POOL; i++) {
            int[] at = walkable(probeSim, FORT_X - 35 + 10 * i, POOL_Y);
            builder.squad("patrol-" + i).faction(Faction.DEFENDER).type(UnitType.MILITIA).size(POOL_SIZE)
                    .at(at[0], at[1]).primary("weapon.smg").done();
        }
        if (lost) {
            int[] at = walkable(probeSim, bunker[0], bunker[1]);
            builder.squad("holders").faction(Faction.MARINE).size(HOLDERS).at(at[0], at[1]).primary("weapon.smg")
                    .health(loop.equals("repulsed") ? TOUGH : FRAIL).stationary().done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261020L);
        if (lost) takeFort(sim);
        ConquestDefenderCommand defenders = new ConquestDefenderCommand(
                new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, WIDTH, HEIGHT));
        int[] pool = new int[POOL];
        for (int i = 0; i < POOL; i++) {
            pool[i] = world.squadId("patrol-" + i);
            sim.claimSquadCommand(pool[i], CommandAuthority.MISSION_COMMAND, defenders.strategyId(), "scene patrol pool");
        }
        sim.setAutonomousCommander(Faction.DEFENDER, defenders, ConquestCommandDisclosure.INSTANCE);

        Trace trace = new Trace(pool);
        int ticks = switch (loop) {
            case "retake" -> 9000;
            case "repulsed" -> 15000;
            default -> 2400;
        };
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 60, tick -> String.format(Locale.ROOT, "%s / tick %d / %s", loop, tick, trace.caption()))
                .run(ticks);
        return report(loop, ticks, trace);
    }

    private SceneReport report(String loop, int ticks, Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        Launch first = t.launches.isEmpty() ? null : t.launches.get(0);
        switch (loop) {
            case "quiet" -> {
                verdicts.add(Verdict.of("nothing-held-back-for-a-quiet-fort", t.counterSamples == 0,
                        t.counterSamples + " squad-samples under a counter-attack reason"));
                verdicts.add(Verdict.of("no-counter-attack", t.notes.isEmpty(), t.notes.size() + " counter-attack notes"));
            }
            case "retake" -> {
                launchVerdicts(verdicts, t, first);
                int retook = t.firstNoteTick(" retook ");
                verdicts.add(Verdict.of("retaken-group-given-back",
                        retook < 0 || t.releasedAfterRetakeTick >= 0 && t.releasedAfterRetakeTick - retook <= 2 * PULSE_TICKS,
                        retook < 0 ? "the counter-attack did not win the bunker back while launched (bunker back at tick "
                                + t.retakenTick + "), so there is nothing to give back"
                                : "retook at tick " + retook + ", group given back at tick " + t.releasedAfterRetakeTick));
            }
            default -> {
                launchVerdicts(verdicts, t, first);
                int giveUp = t.firstNoteTick(" gives up (");
                verdicts.add(Verdict.of("fails-and-falls-back", giveUp >= 0 && t.fellBackTick >= giveUp
                                && t.fellBackTick - giveUp <= 2 * PULSE_TICKS,
                        "first give-up at tick " + giveUp + ", survivors ordered back to the staging cell at tick " + t.fellBackTick));
                Launch second = t.launches.size() > 1 ? t.launches.get(1) : null;
                verdicts.add(Verdict.of("waits-before-trying-again", second == null
                                || second.tick - giveUp >= FortCounterattack.RETRY_TICKS - PULSE_TICKS,
                        second == null ? "no second launch" : "second launch at tick " + second.tick + ", "
                                + (second.tick - giveUp) + " ticks after the give-up (retry " + FortCounterattack.RETRY_TICKS + ")"));
                int abandoned = t.firstNoteTick("fort abandoned");
                verdicts.add(Verdict.of("given-up-for-good-after-the-bound", abandoned >= 0
                                && t.launches.size() <= FortCounterattack.MAX_ATTEMPTS && t.lastCounterTick <= abandoned
                                && t.atFortAfter(abandoned) == 0,
                        "abandoned at tick " + abandoned + " after " + t.launches.size() + " launches; last counter-attack "
                                + "reason at tick " + t.lastCounterTick + "; orders at the fort afterwards "
                                + (abandoned < 0 ? -1 : t.atFortAfter(abandoned))));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("reserveFormedTick", t.firstNoteTick("reserve for fort"));
        metrics.put("musterTick", t.firstNoteTick(" musters: "));
        metrics.put("launches", t.launches.size());
        metrics.put("firstLaunchTick", first == null ? -1 : first.tick);
        metrics.put("firstLaunchSquads", first == null ? -1 : first.squads.size());
        metrics.put("firstLaunchStrength", first == null ? -1 : first.strength);
        metrics.put("firstLaunchRequired", first == null ? -1 : first.required);
        metrics.put("retakenTick", t.retakenTick);
        metrics.put("giveUps", t.count(" gives up ("));
        metrics.put("abandonedTick", t.firstNoteTick("fort abandoned"));
        metrics.put("poolAlive", t.poolAlive);
        metrics.put("otherOrdersAtFort", t.otherAtFort);
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    private static void launchVerdicts(List<Verdict> verdicts, Trace t, Launch first) {
        verdicts.add(Verdict.of("fort-lost-at-the-start", t.fortLost, "every aspect held by the marines at the first pulse: "
                + t.fortLost));
        int muster = t.firstNoteTick(" musters: ");
        verdicts.add(Verdict.of("counter-attack-musters-within-a-few-pulses", muster >= 0 && muster <= FORM_BOUND_TICKS,
                "muster note at tick " + muster + " (bound " + FORM_BOUND_TICKS + ")"));
        verdicts.add(Verdict.of("mustered-in-proportion", first != null && first.squads.size() >= 2
                        && first.strength >= FortCounterattack.COMMIT_SHARE * first.required,
                first == null ? "no launch" : "launched " + first.squads.size() + " squads at strength " + first.strength
                        + " against " + first.required + " believed needed (commit share " + FortCounterattack.COMMIT_SHARE + ")"));
        verdicts.add(Verdict.of("staged-before-launch", first != null && t.stagedBefore(first),
                first == null ? "no launch" : "squads that stood at the staging cell before the launch: "
                        + t.stagedCount(first) + " of " + first.squads.size()));
        verdicts.add(Verdict.of("launched-together", first != null && first.squads.equals(first.musteredJustBefore),
                first == null ? "no launch" : "launched " + first.squads + " on tick " + first.tick + "; mustering the pulse "
                        + "before " + first.musteredJustBefore));
        verdicts.add(Verdict.of("no-squad-sent-at-the-fort-before-the-launch",
                first != null && t.firstAtFortTick >= first.tick,
                "first pool order at the fort at tick " + t.firstAtFortTick + " (" + t.firstAtFortReason + ")"
                        + (first == null ? "" : ", launch at tick " + first.tick)));
        verdicts.add(Verdict.of("reserve-floor-kept-out", t.everCountering.size() < POOL,
                "pool squads ever in the counter-attack " + t.everCountering.size() + " of " + POOL));
    }

    /** One launch as the commander announced it, and who carried the launch order on that tick. */
    private record Launch(int tick, int strength, int required, Set<Integer> squads, Set<Integer> musteredJustBefore) {}

    private static final Pattern LAUNCH = Pattern.compile("launches \\(.*\\): (\\d+) squads, strength (\\d+) against (\\d+) believed");

    private static final class Trace implements TickObserver {
        final int[] pool;
        final List<int[]> notes = new ArrayList<>();
        final List<String> noteText = new ArrayList<>();
        final List<Launch> launches = new ArrayList<>();
        final Set<Integer> everCountering = new TreeSet<>();
        final Map<Integer, Integer> stagedTick = new HashMap<>();
        Set<Integer> musteringBefore = new TreeSet<>();
        final List<int[]> atFort = new ArrayList<>();
        boolean fortLost;
        boolean fortChecked;
        int counterSamples;
        int lastCounterTick = -1;
        int retakenTick = -1;
        int releasedAfterRetakeTick = -1;
        int fellBackTick = -1;
        int firstAtFortTick = Integer.MAX_VALUE;
        String firstAtFortReason = "none";
        int poolAlive;
        int otherAtFort;
        private int lastSummaryTick = -1;
        private String state = "";

        Trace(int[] pool) {
            this.pool = pool;
        }

        String caption() { return state; }

        int firstNoteTick(String needle) {
            for (int i = 0; i < noteText.size(); i++) if (noteText.get(i).contains(needle)) return notes.get(i)[0];
            return -1;
        }

        int count(String needle) {
            int n = 0;
            for (String text : noteText) if (text.contains(needle)) n++;
            return n;
        }

        int atFortAfter(int tick) {
            int n = 0;
            for (int[] order : atFort) if (order[0] > tick) n++;
            return n;
        }

        boolean stagedBefore(Launch launch) {
            return stagedCount(launch) == launch.squads.size();
        }

        int stagedCount(Launch launch) {
            int n = 0;
            for (int id : launch.squads) if (stagedTick.getOrDefault(id, Integer.MAX_VALUE) <= launch.tick) n++;
            return n;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            List<DefenceStructureFact> facts = sim.defenceStructures();
            if (!fortChecked && tick >= PULSE_TICKS) {
                fortChecked = true;
                fortLost = true;
                for (DefenceStructureFact fact : facts) if (fact.ofFort() && fact.holder() != Faction.MARINE) fortLost = false;
            }
            for (DefenceStructureFact fact : facts) {
                if (fact.ofFort() && fact.kind() == DefenceStructureFact.Kind.FIRE_CONTROL && fortLost
                        && fact.holder() == Faction.DEFENDER && retakenTick < 0) retakenTick = tick;
            }
            CommanderSnapshot<?> snapshot = sim.getCommanderSnapshot(Faction.DEFENDER);
            if (snapshot == null) return;
            boolean fresh = snapshot.tick() != lastSummaryTick;
            if (fresh) {
                lastSummaryTick = snapshot.tick();
                for (String summary : snapshot.objectiveSummaries()) {
                    if (!summary.startsWith("counter-attack") || summary.startsWith("counter-attack fort ")) continue;
                    notes.add(new int[]{tick});
                    noteText.add(summary);
                }
            }
            Set<Integer> launching = new TreeSet<>();
            Set<Integer> mustering = new TreeSet<>();
            int counterNow = 0;
            int alive = 0;
            for (int id : pool) {
                Squad squad = sim.getSquad(id);
                if (squad == null || squad.aliveMembers <= 0) continue;
                alive++;
                CommandDirective directive = snapshot.directiveFor(id);
                String reason = directive == null ? "" : directive.reason();
                ObjectiveAssignment order = squad.assignedObjective;
                boolean counter = reason.startsWith("DEFENDER_COUNTERATTACK_");
                if (counter) {
                    counterNow++;
                    everCountering.add(id);
                    lastCounterTick = tick;
                }
                if (reason.equals("DEFENDER_COUNTERATTACK_LAUNCH")) launching.add(id);
                if (reason.equals("DEFENDER_COUNTERATTACK_MUSTER")) mustering.add(id);
                if ((reason.equals("DEFENDER_COUNTERATTACK_MUSTER") || reason.equals("DEFENDER_COUNTERATTACK_RESERVE"))
                        && order != null) {
                    // Arrived as the commander reckons it: the squad's leader within the rally radius.
                    long leader = sim.resolveUnit(squad.leaderId);
                    if (leader == 0L && sim.squadMemberCount(id) > 0) leader = sim.squadMemberAt(id, 0);
                    if (leader != 0L && Math.hypot(sim.world().cellX(leader) - order.targetCellX(),
                            sim.world().cellY(leader) - order.targetCellY()) <= FortCounterattack.RALLY_RADIUS_CELLS) {
                        stagedTick.putIfAbsent(id, tick);
                    }
                }
                if (order != null && order.targetCellX() >= 0 && nearFort(facts, order.targetCellX(), order.targetCellY())) {
                    // After the fort is given up, the counter-attack's and structure work's orders are what count; a track
                    // response to a believed contact is reported in the metrics, not judged here.
                    if (fresh && (counter || reason.equals("DEFENCE_STRUCTURE_RETAKE"))) atFort.add(new int[]{tick, id});
                    if (fresh && !counter && !reason.equals("DEFENCE_STRUCTURE_RETAKE")) otherAtFort++;
                    if (tick < firstAtFortTick) {
                        firstAtFortTick = tick;
                        firstAtFortReason = reason;
                    }
                }
            }
            counterSamples += counterNow;
            poolAlive = alive;
            if (fresh) {
                for (int i = noteText.size() - 1; i >= 0 && notes.get(i)[0] == tick; i--) {
                    String text = noteText.get(i);
                    Matcher m = LAUNCH.matcher(text);
                    if (m.find()) {
                        launches.add(new Launch(tick, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                                new TreeSet<>(launching), musteringBefore));
                    }
                    if (text.contains(" gives up (") && !text.contains("abandoned")) fellBackPending = tick;
                    if (text.contains("retook")) retakeNoted = tick;
                }
                if (fellBackPending >= 0 && fellBackTick < 0 && launching.isEmpty()) {
                    boolean allBack = true;
                    for (int id : pool) {
                        Squad squad = sim.getSquad(id);
                        if (squad == null || squad.aliveMembers <= 0 || !everCountering.contains(id)) continue;
                        CommandDirective directive = snapshot.directiveFor(id);
                        if (directive == null || !directive.reason().startsWith("DEFENDER_COUNTERATTACK_")) continue;
                        ObjectiveAssignment order = squad.assignedObjective;
                        if (order == null || order.kind() != AssignmentKind.DEFEND_AREA) allBack = false;
                    }
                    if (allBack) fellBackTick = tick;
                }
                if (retakeNoted >= 0 && releasedAfterRetakeTick < 0 && launching.isEmpty()) releasedAfterRetakeTick = tick;
                musteringBefore = mustering;
            }
            state = String.format(Locale.ROOT, "countering %d, launching %d, launches %d%s", counterNow, launching.size(),
                    launches.size(), retakenTick >= 0 ? ", bunker retaken" : "");
        }

        private int fellBackPending = -1;
        private int retakeNoted = -1;

        private static boolean nearFort(List<DefenceStructureFact> facts, int x, int y) {
            for (DefenceStructureFact fact : facts) {
                if (fact.ofFort() && Math.max(Math.abs(fact.cellX() - x), Math.abs(fact.cellY() - y)) <= AT_FORT_CELLS) return true;
            }
            return false;
        }
    }
}
