package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.ScriptedPlayer;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.bsp.FortStage;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Reactive manning of a fort's belt (`garrisonables.md`): a production battery fort with its belt of trenches and
 * sandbags, the defender's Conquest commander, and a pool of patrols in the works. A raiding party, too tough to be shot
 * down so the question stays about command, is put beside the stretch of belt by the main gate, and then each loop
 * changes one thing.
 *
 * <ul>
 *   <li>{@code quiet}: the raiders never come. Nothing is manned and the pool is not drawn down: a quiet belt is
 *       empty.</li>
 *   <li>{@code passing-attack}: the raiders press the gate stretch, then leave. The stretch is manned within a few
 *       command pulses of the commander believing it pressed, the garrison digs in, holds while the raiders are there,
 *       and is given back after they have gone and the fort's alarm has faded.</li>
 *   <li>{@code breach}: once the garrison is on its stretch and dug in, the raiders are in the trench beside it. The
 *       breached garrison falls back to the fort's works, and the stretch is not manned again straight away.</li>
 * </ul>
 *
 * <p>The raiders are placed rather than walked: the question is what the commander does about a stretch under
 * attack, not whether a party can cross a fire-swept field. Every verdict is a mechanic of the commander, timed from
 * its own belief; how long things took is in the metrics.
 */
public final class ReactiveBeltScene implements BehaviorScene {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 160;
    /** One command pulse in ticks (2.5 s at 30 Hz). */
    private static final int PULSE_TICKS = 75;
    /** "A few command pulses". */
    private static final int MAN_BOUND_TICKS = 6 * PULSE_TICKS;
    private static final int PATROLS = 6;
    private static final int PATROL_SIZE = 6;
    private static final int RAIDERS = 6;
    private static final float TOUGH = 1_000_000f;
    /** The raiders are put beside the gate stretch at this tick. */
    private static final int ENTER_TICK = 30;
    /** The raiders leave (passing-attack) at this tick. */
    private static final int LEAVE_TICK = 2400;
    /** The breach is made once the garrison has been on its stretch, dug in, for this long. */
    private static final int BREACH_AFTER_DUG_TICKS = 300;
    /** How far outside the belt the raiders stand. */
    private static final int STANDOFF_CELLS = 6;
    /** A fall-back post this near a fort aspect is a post at the works. */
    private static final int WORKS_CELLS = 16;

    @Override public String id() { return "reactive-belt"; }

    @Override public String label() {
        return "Reactive belt garrisons: a quiet belt stays empty, a pressed stretch is manned, dug in and given back "
                + "after the attack passes, and a breached garrison falls back to the works";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("quiet", "passing-attack", "breach")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private record Terrain(NavigationGrid grid, CellTopology topology, List<DefensePost> posts, List<Doodad> doodads,
                           int[] centre) {}

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
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL.withDeclaredDefences(
                new GroundDefences(0, false).withForts(FortKind.BATTERY_FORT, 1)));
        FortStage stage = new FortStage();
        stage.run(ctx);
        int[] centre = stage.placed().get(0).placement().cell(BatteryFort.OX + BatteryFort.WORKS_W / 2,
                BatteryFort.OY + BatteryFort.WORKS_H / 2);
        return new Terrain(grid, topology, List.copyOf(ctx.defensePosts), List.copyOf(ctx.doodads), centre);
    }

    /** Where things stand: the gate stretch, where the raiders press it from, where they wait off it, the pool's cells. */
    private record Layout(int runA, int[] haltA, int[] field, int[][] patrolCells) {}

    private static Layout layout(BattleSimulation sim, int[] centre) {
        Fort fort = sim.forts().forts().get(0);
        GarrisonableRegistry places = sim.garrisonables();
        // The gate stretch: the run of this fort nearest the field's south edge, the side the main gate faces.
        int runA = -1;
        for (int g = 0; g < places.count(); g++) {
            if (places.fortId(g) != fort.id() || places.size(g) < 6) continue;
            if (runA < 0 || places.centreY(g) > places.centreY(runA)
                    || (places.centreY(g) == places.centreY(runA) && places.centreX(g) < places.centreX(runA))) runA = g;
        }
        int[] haltA = outside(sim, places.centreX(runA), places.centreY(runA), centre[0], centre[1]);
        int[] field = walkable(sim, centre[0] - 70, HEIGHT - 6);
        // The pool waits in the works, the fort's own garrison, within a march of its belt.
        int[][] patrolCells = new int[PATROLS][];
        for (int i = 0; i < PATROLS; i++) patrolCells[i] = walkable(sim, centre[0] - 6 + 3 * i, centre[1]);
        return new Layout(runA, haltA, field, patrolCells);
    }

    /**
     * Where raiders press a run from: the walkable open cell nearest the run's centre, at least {@link #STANDOFF_CELLS}
     * from it and farther from the fort's centre than the run is, so they stand outside the belt facing it.
     */
    private static int[] outside(BattleSimulation sim, int cx, int cy, float fx, float fy) {
        double runOut = Math.hypot(cx - fx, cy - fy);
        int[] best = null;
        long bestGap = Long.MAX_VALUE;
        for (int dy = -STANDOFF_CELLS * 3; dy <= STANDOFF_CELLS * 3; dy++) {
            for (int dx = -STANDOFF_CELLS * 3; dx <= STANDOFF_CELLS * 3; dx++) {
                int x = cx + dx;
                int y = cy + dy;
                if (x < 2 || y < 2 || x >= WIDTH - 2 || y >= HEIGHT - 2) continue;
                if (Math.max(Math.abs(dx), Math.abs(dy)) < STANDOFF_CELLS) continue;
                if (Math.hypot(x - fx, y - fy) <= runOut + 2) continue;
                if (!sim.getGrid().isWalkable(x, y) || sim.fightingPositions().isFightingPositionCell(x, y)) continue;
                long gap = (long) dx * dx + (long) dy * dy;
                if (gap < bestGap) {
                    bestGap = gap;
                    best = new int[]{x, y};
                }
            }
        }
        return best != null ? best : walkable(sim, cx, cy);
    }

    /** The nearest walkable cell to the one asked for that is not prepared ground. */
    private static int[] walkable(BattleSimulation sim, int x, int y) {
        x = Math.max(2, Math.min(WIDTH - 3, x));
        y = Math.max(2, Math.min(HEIGHT - 3, y));
        for (int r = 0; r < 20; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int cx = x + dx;
                    int cy = y + dy;
                    if (cx < 1 || cy < 1 || cx >= WIDTH - 1 || cy >= HEIGHT - 1) continue;
                    if (sim.fightingPositions().isFightingPositionCell(cx, cy)) continue;
                    if (sim.getGrid().isWalkable(cx, cy)) return new int[]{cx, cy};
                }
            }
        }
        return new int[]{x, y};
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        Terrain terrain = stamp();
        SceneBuilder builder = SceneBuilder.over(terrain.grid(), terrain.topology()).posts(terrain.posts())
                .doodads(terrain.doodads()).missionCompletion(false);
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 3, HEIGHT - 3, spec -> spec.moveSpeed(0f).health(TOUGH));
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, WIDTH - 3, 3, spec -> spec.moveSpeed(0f).health(TOUGH));
        // A throwaway build of the same stamp, to read the fort's geometry before the squads are placed on it.
        Terrain probe = stamp();
        Layout layout = layout(SceneBuilder.over(probe.grid(), probe.topology()).posts(probe.posts())
                .doodads(probe.doodads()).missionCompletion(false).build().sim(), probe.centre());
        for (int i = 0; i < PATROLS; i++) {
            builder.squad("patrol-" + i).faction(Faction.DEFENDER).type(UnitType.MILITIA).size(PATROL_SIZE)
                    .at(layout.patrolCells()[i][0], layout.patrolCells()[i][1]).primary("weapon.smg").health(TOUGH).done();
        }
        boolean raid = !loop.equals("quiet");
        if (raid) {
            builder.squad("raiders").faction(Faction.MARINE).size(RAIDERS).at(layout.field()[0], layout.field()[1])
                    .primary("weapon.smg").health(TOUGH).stationary().done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261020L);
        ConquestDefenderCommand defenders = new ConquestDefenderCommand(
                new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, WIDTH, HEIGHT));
        int[] patrolIds = new int[PATROLS];
        for (int i = 0; i < PATROLS; i++) {
            patrolIds[i] = world.squadId("patrol-" + i);
            sim.claimSquadCommand(patrolIds[i], CommandAuthority.MISSION_COMMAND, defenders.strategyId(), "scene patrol pool");
        }
        sim.setAutonomousCommander(Faction.DEFENDER, defenders, ConquestCommandDisclosure.INSTANCE);

        Trace trace = new Trace(loop, layout, patrolIds, raid ? world.members("raiders") : new long[0]);
        ScriptedPlayer player = ScriptedPlayer.on(world);
        if (raid) {
            long[] raiders = world.members("raiders");
            player.at(ENTER_TICK, s -> place(s, raiders, layout.haltA()));
            if (loop.equals("passing-attack")) player.at(LEAVE_TICK, s -> place(s, raiders, layout.field()));
        }
        int ticks = switch (loop) {
            case "passing-attack" -> 9000;
            case "breach" -> 4800;
            default -> 3000;
        };
        SceneRun.of(world).player(player).observe(trace)
                .frames(frames, loop, 60, tick -> String.format(Locale.ROOT, "%s / tick %d / %s", loop, tick, trace.caption()))
                .run(ticks);
        return report(loop, ticks, trace);
    }

    /** Stands the raiders in a small block on and beside {@code at}. */
    private static void place(BattleSimulation sim, long[] raiders, int[] at) {
        for (int i = 0; i < raiders.length; i++) {
            int[] cell = walkable(sim, at[0] + (i % 3) - 1, at[1] + (i / 3));
            sim.world().setCellPos(raiders[i], cell[0], cell[1]);
        }
    }

    private SceneReport report(String loop, int ticks, Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        int runA = t.layout.runA();
        switch (loop) {
            case "quiet" -> {
                verdicts.add(Verdict.of("no-stretch-manned-while-quiet", t.manOrders.isEmpty(),
                        t.manOrders.size() + " manning orders"));
                verdicts.add(Verdict.of("pool-not-drawn-down", t.maxCommitted == 0,
                        "most patrols at once carrying a garrison or staging order: " + t.maxCommitted));
            }
            case "passing-attack" -> {
                manned(verdicts, t, runA);
                verdicts.add(Verdict.of("garrison-digs-in", t.firstDugInOnA >= 0,
                        "first member dug in on the stretch, the squad on it, at tick " + t.firstDugInOnA));
                verdicts.add(Verdict.of("held-while-pressed", t.firstManTick(runA) >= 0 && t.releaseBeforeLeave < 0,
                        "first garrison given back before the raiders left at tick " + t.releaseBeforeLeave));
                verdicts.add(Verdict.of("given-back-after-the-attack-passes", t.firstManTick(runA) >= 0
                                && t.lastGarrisonTick >= LEAVE_TICK && t.lastGarrisonTick < ticks - PULSE_TICKS,
                        "last tick any patrol carried a garrison order " + t.lastGarrisonTick + " of " + ticks
                                + "; raiders left at " + LEAVE_TICK));
                verdicts.add(Verdict.of("reserve-kept", t.maxCommitted < PATROLS,
                        "most patrols manning or staging at once " + t.maxCommitted + " of " + PATROLS));
            }
            default -> {
                manned(verdicts, t, runA);
                verdicts.add(Verdict.of("breach-made", t.breachTick >= 0 && t.breachInsideTick >= 0,
                        "breach made at tick " + t.breachTick + " (garrison dug in from tick " + t.firstDugInOnA
                                + "), raiders in the trench from tick " + t.breachInsideTick));
                boolean fellBack = t.fallBackTick >= 0 && t.breachInsideTick >= 0
                        && t.fallBackTick - t.breachInsideTick <= MAN_BOUND_TICKS && t.fallBackNearWorks;
                verdicts.add(Verdict.of("breached-garrison-falls-back-to-the-works", fellBack,
                        "fall back ordered at tick " + t.fallBackTick + " (" + (t.fallBackNearWorks ? "at" : "not at")
                                + " the works), raiders inside from tick " + t.breachInsideTick));
                int again = t.breachInsideTick < 0 ? -1 : t.firstManTickAfter(runA, t.breachInsideTick + 1);
                verdicts.add(Verdict.of("breached-stretch-not-manned-again-at-once", t.breachInsideTick >= 0
                                && (again < 0 || again > t.breachInsideTick + GarrisonDirector.BREACH_LOCKOUT_PULSES * PULSE_TICKS / 2),
                        "stretch next manned at tick " + again + " (lockout " + GarrisonDirector.BREACH_LOCKOUT_PULSES + " pulses)"));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("pressedTick", t.hotATick);
        metrics.put("peakPressure", Math.round(t.peakA * 100f) / 100f);
        metrics.put("firstManTick", t.firstManTick(runA));
        metrics.put("firstDugInTick", t.firstDugInOnA);
        metrics.put("lastGarrisonTick", t.lastGarrisonTick);
        metrics.put("heldAfterLeaveTicks", loop.equals("passing-attack") && t.lastGarrisonTick >= 0
                ? Math.max(0, t.lastGarrisonTick - LEAVE_TICK) : -1);
        metrics.put("manOrders", t.manOrders.size());
        metrics.put("maxCommitted", t.maxCommitted);
        metrics.put("breachInsideTick", t.breachInsideTick);
        metrics.put("fallBackTick", t.fallBackTick);
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    private static void manned(List<Verdict> verdicts, Trace t, int run) {
        int man = t.firstManTick(run);
        verdicts.add(Verdict.of("pressed-stretch-manned-within-a-few-pulses",
                t.hotATick >= 0 && man >= t.hotATick && man - t.hotATick <= MAN_BOUND_TICKS,
                "believed pressure on the stretch at the manning level from tick " + t.hotATick
                        + ", first manned at tick " + man + " (bound " + MAN_BOUND_TICKS + " ticks)"));
    }

    /** Reads the commander's orders, its belief and the squads off the simulation every tick. */
    private static final class Trace implements TickObserver {
        final String loop;
        final Layout layout;
        final int[] patrols;
        final long[] raiders;
        /** Every manning order as first seen: tick, squad, run. */
        final List<int[]> manOrders = new ArrayList<>();
        final Map<Integer, Integer> garrisonRun = new LinkedHashMap<>();
        /** First tick the commander's belief on the gate stretch reached the manning level, and the most it reached. */
        int hotATick = -1;
        float peakA;
        int firstDugInOnA = -1;
        int releaseBeforeLeave = -1;
        int lastGarrisonTick = -1;
        int maxCommitted;
        int breachTick = -1;
        int breachInsideTick = -1;
        int breachSquad = -1;
        int fallBackTick = -1;
        boolean fallBackNearWorks;
        private String state = "";

        Trace(String loop, Layout layout, int[] patrols, long[] raiders) {
            this.loop = loop;
            this.layout = layout;
            this.patrols = patrols;
            this.raiders = raiders;
        }

        String caption() { return state; }

        int firstManTick(int run) {
            return firstManTickAfter(run, 0);
        }

        int firstManTickAfter(int run, int from) {
            for (int[] order : manOrders) if (order[2] == run && order[0] >= from) return order[0];
            return -1;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            GarrisonableRegistry places = sim.garrisonables();
            int runA = layout.runA();
            CommanderInfluenceSnapshot picture = sim.peekCommanderInfluence(Faction.DEFENDER);
            if (picture != null) {
                float a = beliefOn(picture, places, runA);
                peakA = Math.max(peakA, a);
                if (hotATick < 0 && a >= GarrisonDirector.START_PRESSURE) hotATick = tick;
            }
            CommanderSnapshot<?> snapshot = sim.getCommanderSnapshot(Faction.DEFENDER);
            int committed = 0;
            int onRunA = 0;
            for (int id : patrols) {
                Squad squad = sim.getSquad(id);
                if (squad == null || squad.aliveMembers <= 0) continue;
                ObjectiveAssignment order = squad.assignedObjective;
                CommandDirective directive = snapshot == null ? null : snapshot.directiveFor(id);
                String reason = directive == null ? "" : directive.reason();
                if (reason.equals("DEFENDER_GARRISONABLE_STAGE")) committed++;
                if (order != null && order.isGarrison()) {
                    committed++;
                    lastGarrisonTick = tick;
                    int run = places.idAt(order.targetCellX(), order.targetCellY());
                    if (run < 0) run = order.occupyRunId();
                    Integer before = garrisonRun.put(id, run);
                    if (before == null || before != run) manOrders.add(new int[]{tick, id, run});
                    if (run == runA) {
                        onRunA++;
                        // On its place as the commander reckons it (centroid within the run's reach of its centre) and dug in.
                        int reach = places.radius(runA);
                        boolean arrived = Math.abs(squad.centroidX - places.centreX(runA)) <= reach
                                && Math.abs(squad.centroidY - places.centreY(runA)) <= reach;
                        for (int i = 0, n = sim.squadMemberCount(id); i < n && arrived && firstDugInOnA < 0; i++) {
                            if (sim.isDugIn(sim.squadMemberAt(id, i))) {
                                firstDugInOnA = tick;
                                breachSquad = id;
                            }
                        }
                    }
                } else if (garrisonRun.remove(id) != null && tick < LEAVE_TICK && releaseBeforeLeave < 0
                        && loop.equals("passing-attack")) {
                    releaseBeforeLeave = tick;
                }
                if (id == breachSquad && breachInsideTick >= 0 && fallBackTick < 0
                        && reason.equals("DEFENDER_GARRISONABLE_FALL_BACK") && order != null) {
                    fallBackTick = tick;
                    for (Fort.Place place : sim.forts().forts().get(0).places()) {
                        if (Math.max(Math.abs(place.x() - order.targetCellX()), Math.abs(place.y() - order.targetCellY())) <= WORKS_CELLS) {
                            fallBackNearWorks = true;
                        }
                    }
                }
            }
            maxCommitted = Math.max(maxCommitted, committed);
            if (loop.equals("breach") && breachTick < 0 && breachSquad >= 0 && tick >= firstDugInOnA + BREACH_AFTER_DUG_TICKS) {
                Squad held = sim.getSquad(breachSquad);
                breachTick = tick;
                int[] into = intoRun(sim, places, runA, held.centroidX, held.centroidY);
                for (long raider : raiders) sim.world().setCellPos(raider, into[0], into[1]);
            }
            if (breachTick >= 0 && breachInsideTick < 0) {
                for (long raider : raiders) {
                    if (places.idAt(sim.world().cellX(raider), sim.world().cellY(raider)) == runA) {
                        breachInsideTick = tick;
                        break;
                    }
                }
            }
            state = String.format(Locale.ROOT, "manning orders %d, on the gate stretch %d", manOrders.size(), onRunA);
        }

        private static float beliefOn(CommanderInfluenceSnapshot picture, GarrisonableRegistry places, int run) {
            float best = 0f;
            int bw = places.blockWidth();
            for (int block : places.coveredBlocks(run)) best = Math.max(best, picture.hostileAt(block % bw, block / bw));
            return best;
        }

        /** A cell of the run a few cells from the garrison: where a breach puts the raiders. */
        private static int[] intoRun(BattleSimulation sim, GarrisonableRegistry places, int run, float gx, float gy) {
            int best = -1;
            int bx = 0;
            int by = 0;
            int cx = Math.round(gx);
            int cy = Math.round(gy);
            for (int dy = -12; dy <= 12; dy++) {
                for (int dx = -12; dx <= 12; dx++) {
                    int x = cx + dx;
                    int y = cy + dy;
                    if (places.idAt(x, y) != run || !sim.getGrid().isWalkable(x, y)) continue;
                    int d = Math.max(Math.abs(dx), Math.abs(dy));
                    if (d < 3) continue;
                    if (best < 0 || d < best) {
                        best = d;
                        bx = x;
                        by = y;
                    }
                }
            }
            return best < 0 ? new int[]{cx, cy} : new int[]{bx, by};
        }
    }
}
