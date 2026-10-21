package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.battery.FireControl;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.mech.MechMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sally.SallyDoor;
import com.dillon.starsectormarines.battle.sally.SallyDoors;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * A fort's sally ports, the fort case of sally doors (`garrisonables.md`, sally doors; `forts.md`, sally ports): a door in
 * each gateless face of the works, closed as wall until the fort's holder unbars it for a sortie. A production battery fort
 * stamped on open ground, and each loop asks one thing.
 *
 * <ul>
 *   <li>{@code sortie}: the defender's Conquest commander with a pool of patrols and one lance in the works, and a raiding
 *       party, too tough to be shot down, put outside the belt beyond a gateless face. The stretch it presses is manned,
 *       the port on that face is unbarred within a port pulse of the order (and not while a hostile stands at its door),
 *       the garrison goes out through it and reaches its stretch within a bound set by the straight line, and the port on
 *       the other face stays barred. A guard the commander attaches to the garrison, if it attaches one, goes out of the
 *       works through the port.</li>
 *   <li>{@code sortie-barred}: the same with {@code -Dbattle.sallyDoors=false} in force for the loop: nothing is
 *       unbarred, and the walk round is measured for comparison.</li>
 *   <li>{@code enemy-at-the-door}: a defender squad in the works is ordered out to ground beyond the port while raiders
 *       stand just outside its door. The port stays barred while they are there and is unbarred within a pulse or two of
 *       their going.</li>
 *   <li>{@code attack-at-the-port}: a marine squad on the glacis is ordered into the works at the yard just behind a
 *       barred port, with the defender holding the fort and nobody of the defender's going out. The port is never
 *       unbarred for it and no marine ever stands in it: the attack goes round by a gate or not at all.</li>
 *   <li>{@code taken-fort}: the fort is handed to the marines, and a marine squad in the works is ordered to the belt
 *       beyond a port. The port serves its new holder: it is unbarred and the squad goes out through it.</li>
 * </ul>
 *
 * <p>Squads are placed rather than walked to their start, and the raiders are tough, so every verdict is a mechanic of the
 * port or the commander; who wins is not asked.
 */
public final class SallyPortScene implements BehaviorScene {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 160;
    private static final int PATROLS = 4;
    private static final int PATROL_SIZE = 6;
    private static final int RAIDERS = 6;
    private static final float TOUGH = 1_000_000f;
    private static final int ENTER_TICK = 30;
    /** The raiders at the door step away at this tick ({@code enemy-at-the-door}). */
    private static final int CLEAR_TICK = 900;
    /** Two port pulses: how long after it is wanted (and safe) a port may take to be unbarred. */
    private static final int UNBAR_BOUND_TICKS = 2 * 15 + 1;
    /**
     * A garrison or guard reaches its ground within this multiple of the march the commander prices for the straight
     * line ({@link GarrisonDirector#etaTicks}, itself a third over the octile distance), counted from its order. The walk
     * round a gateless face is two to four times the straight line. UNTUNED.
     */
    private static final float ARRIVAL_BOUND = 2.0f;
    /** How far outside the belt the raiders stand. */
    private static final int STANDOFF_CELLS = 6;

    @Override public String id() { return "sally-port"; }

    @Override public String label() {
        return "Fort sally ports: the holder unbars the port on the face it sorties from, never in the enemy's face, the "
                + "garrison goes out through it, an attacker is never given it, and a taken fort's ports serve the taker";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("sortie", "sortie-barred", "enemy-at-the-door", "attack-at-the-port", "taken-fort")) {
            boolean was = SallyDoors.enabled();
            SallyDoors.setEnabledForEvidence(!loop.equals("sortie-barred") && was);
            try {
                reports.add(playOne(loop, frames));
            } finally {
                SallyDoors.setEnabledForEvidence(was);
            }
        }
        return reports;
    }

    /** The world cells of port {@code port} of the fort, and then its mouth. */
    static int[][] portCells(BattleSimulation sim, Fort fort, int port) {
        SallyDoor door = sim.sallyDoors().doors(sim).get(sim.sallyDoors().fortDoor(sim, fort, port));
        int[][] out = Arrays.copyOf(door.cells(), door.cells().length + 1);
        out[door.cells().length] = new int[]{door.mouthX(), door.mouthY()};
        return out;
    }

    /** Whether port {@code port} of the fort serves an order to the cell. */
    static boolean serves(BattleSimulation sim, Fort fort, int port, int x, int y) {
        return sim.sallyDoors().serves(sim, sim.sallyDoors().fortDoor(sim, fort, port), x, y);
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

    /**
     * Where things stand: the port the loop is about and the other one, the stretch beyond it nearest its mouth, where
     * raiders press that stretch from, the yard cell behind the port, a glacis cell outside it, and the pool's cells.
     */
    private record Layout(int port, int other, int stretch, int[] stretchCell, int[] halt, int[] door, int[] yard,
                          int[] glacis, int[][] pool, int[] lanceCell, int[] field, int[] lookout) {}

    private static Layout layout(BattleSimulation sim, int[] centre) {
        Fort fort = sim.forts().forts().get(0);
        GarrisonableRegistry places = sim.garrisonables();
        int port = 0;
        int other = 1;
        int[][] cells = portCells(sim, fort, port);
        int[] mouth = cells[cells.length - 1];
        int stretch = -1;
        long best = Long.MAX_VALUE;
        for (int g = 0; g < places.count(); g++) {
            if (places.fortId(g) != fort.id() || places.size(g) < 6) continue;
            if (!serves(sim, fort, port, places.centreX(g), places.centreY(g))) continue;
            long dx = places.centreX(g) - mouth[0];
            long dy = places.centreY(g) - mouth[1];
            if (dx * dx + dy * dy < best) {
                best = dx * dx + dy * dy;
                stretch = g;
            }
        }
        int[] stretchCell = {places.centreX(stretch), places.centreY(stretch)};
        int[] halt = outside(sim, stretchCell[0], stretchCell[1], centre[0], centre[1]);
        int outX = Integer.signum(mouth[0] - cells[0][0]);
        int outY = Integer.signum(mouth[1] - cells[0][1]);
        int[] door = walkable(sim, mouth[0] + 2 * outX, mouth[1] + 2 * outY);
        int[] yard = walkable(sim, cells[0][0] - 3 * outX, cells[0][1] - 3 * outY);
        int[] glacis = walkable(sim, mouth[0] + 5 * outX, mouth[1] + 5 * outY);
        int[][] pool = new int[PATROLS][];
        for (int i = 0; i < PATROLS; i++) pool[i] = walkable(sim, centre[0] - 6 + 3 * i, centre[1]);
        int[] lanceCell = mechStand(sim, centre[0], centre[1] + 4);
        int[] field = walkable(sim, 6, 6);
        // Off the stretch toward the works, where it sees the raiders across the trench.
        int[] lookout = walkable(sim, (2 * stretchCell[0] + mouth[0]) / 3, (2 * stretchCell[1] + mouth[1]) / 3);
        return new Layout(port, other, stretch, stretchCell, halt, door, yard, glacis, pool, lanceCell, field, lookout);
    }

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

    private static int[] mechStand(BattleSimulation sim, int x, int y) {
        for (int r = 0; r < 20; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int cx = x + dx;
                    int cy = y + dy;
                    if (!sim.getGrid().inBounds(cx, cy) || sim.fightingPositions().isFightingPositionCell(cx, cy)) continue;
                    if (ManualTerrainMotion.canStand(sim.getGrid(), cx + 0.5f, cy + 0.5f, MechVariant.SIROCCO.radius)) {
                        return new int[]{cx, cy};
                    }
                }
            }
        }
        return new int[]{x, y};
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        Terrain terrain = stamp();
        Terrain probe = stamp();
        Layout layout;
        try (BattleSimulation probeSim = SceneBuilder.over(probe.grid(), probe.topology()).posts(probe.posts())
                .doodads(probe.doodads()).missionCompletion(false).build().sim()) {
            layout = layout(probeSim, probe.centre());
        }
        boolean commanded = loop.startsWith("sortie");
        SceneBuilder builder = SceneBuilder.over(terrain.grid(), terrain.topology()).posts(terrain.posts())
                .doodads(terrain.doodads()).missionCompletion(false);
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 3, HEIGHT - 3, spec -> spec.moveSpeed(0f).health(TOUGH));
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, WIDTH - 3, 3, spec -> spec.moveSpeed(0f).health(TOUGH));
        int[] stretch = layout.stretchCell();
        switch (loop) {
            case "sortie", "sortie-barred" -> {
                for (int i = 0; i < PATROLS; i++) {
                    builder.squad("patrol-" + i).faction(Faction.DEFENDER).type(UnitType.MILITIA).size(PATROL_SIZE)
                            .at(layout.pool()[i][0], layout.pool()[i][1]).primary("weapon.smg").health(TOUGH).done();
                }
                builder.squad("lance").faction(Faction.DEFENDER).size(1).at(layout.lanceCell()[0], layout.lanceCell()[1])
                        .mech(MechVariant.SIROCCO, MechRole.BALANCED).done();
                builder.squad("raiders").faction(Faction.MARINE).size(RAIDERS).at(layout.field()[0], layout.field()[1])
                        .primary("weapon.smg").health(TOUGH).stationary().done();
                // A lookout on the belt, so the attack on a face with no gate and no nest looking along it is seen: a
                // commander mans only what it believes pressed.
                builder.squad("lookout").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(1)
                        .at(layout.lookout()[0], layout.lookout()[1]).primary("weapon.smg").health(TOUGH).stationary().done();
            }
            case "enemy-at-the-door" -> {
                builder.squad("sortie").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(PATROL_SIZE)
                        .at(layout.pool()[0][0], layout.pool()[0][1]).primary("weapon.smg").health(TOUGH)
                        .assigned(id -> ObjectiveAssignment.attackMove(id, stretch[0], stretch[1])).done();
                builder.squad("raiders").faction(Faction.MARINE).size(RAIDERS).at(layout.door()[0], layout.door()[1])
                        .primary("weapon.smg").health(TOUGH).stationary().done();
            }
            case "attack-at-the-port" -> {
                builder.squad("picket").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(PATROL_SIZE)
                        .at(layout.pool()[0][0], layout.pool()[0][1]).primary("weapon.smg").health(TOUGH).stationary().done();
                int[] yard = layout.yard();
                builder.squad("attack").faction(Faction.MARINE).size(RAIDERS).at(layout.glacis()[0], layout.glacis()[1])
                        .primary("weapon.smg").health(TOUGH)
                        .assigned(id -> ObjectiveAssignment.attackMove(id, yard[0], yard[1])).done();
            }
            default -> builder.squad("holders").faction(Faction.MARINE).size(RAIDERS)
                    .at(layout.pool()[0][0], layout.pool()[0][1]).primary("weapon.smg").health(TOUGH)
                    .assigned(id -> ObjectiveAssignment.attackMove(id, stretch[0], stretch[1])).done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261020L);
        List<Integer> outgoing = new ArrayList<>();
        int lance = -1;
        if (commanded) {
            ConquestDefenderCommand defenders = new ConquestDefenderCommand(
                    new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, WIDTH, HEIGHT));
            for (int i = 0; i < PATROLS; i++) {
                int id = world.squadId("patrol-" + i);
                outgoing.add(id);
                sim.claimSquadCommand(id, CommandAuthority.MISSION_COMMAND, defenders.strategyId(), "scene patrol pool");
            }
            lance = world.squadId("lance");
            sim.claimSquadCommand(lance, CommandAuthority.MISSION_COMMAND, defenders.strategyId(), "scene lance pool");
            sim.setAutonomousCommander(Faction.DEFENDER, defenders, ConquestCommandDisclosure.INSTANCE);
        } else if (loop.equals("enemy-at-the-door")) {
            outgoing.add(world.squadId("sortie"));
        } else if (loop.equals("taken-fort")) {
            outgoing.add(world.squadId("holders"));
        }
        Trace trace = new Trace(loop, layout, outgoing, lance,
                loop.equals("attack-at-the-port") ? world.members("attack") : new long[0]);
        ScriptedPlayer player = ScriptedPlayer.on(world);
        switch (loop) {
            case "sortie", "sortie-barred" -> {
                long[] raiders = world.members("raiders");
                player.at(ENTER_TICK, s -> place(s, raiders, layout.halt()));
            }
            case "enemy-at-the-door" -> {
                long[] raiders = world.members("raiders");
                player.at(CLEAR_TICK, s -> place(s, raiders, layout.field()));
            }
            case "taken-fort" -> {
                player.at(1, SallyPortScene::takeFort);
            }
            default -> { }
        }
        int ticks = switch (loop) {
            case "sortie", "sortie-barred" -> 3600;
            case "attack-at-the-port" -> 3000;
            default -> 2400;
        };
        try {
            SceneRun.of(world).player(player).observe(trace)
                    .frames(frames, loop, 60, tick -> String.format(Locale.ROOT, "%s / tick %d / %s", loop, tick, trace.caption()))
                    .run(ticks);
        } finally {
            sim.close();
        }
        return report(loop, ticks, trace);
    }

    private static void place(BattleSimulation sim, long[] raiders, int[] at) {
        for (int i = 0; i < raiders.length; i++) {
            int[] cell = walkable(sim, at[0] + (i % 3) - 1, at[1] + (i / 3));
            sim.world().setCellPos(raiders[i], cell[0], cell[1]);
        }
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

    private SceneReport report(String loop, int ticks, Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "sortie" -> {
                verdicts.add(Verdict.of("stretch-beyond-the-port-manned", t.orderTick >= 0,
                        "first order to ground beyond the port at tick " + t.orderTick + " (squad " + t.orderSquad + ")"));
                if (t.orderTick >= 0) {
                    verdicts.add(Verdict.of("port-unbarred-for-the-sortie", t.unbarTick >= t.orderTick - 1
                                    && t.unbarTick - t.orderTick <= UNBAR_BOUND_TICKS,
                            "port unbarred at tick " + t.unbarTick + ", " + (t.unbarTick - t.orderTick)
                                    + " ticks after the order (bound " + UNBAR_BOUND_TICKS + ")"));
                    verdicts.add(Verdict.of("garrison-goes-out-through-the-port", t.throughPortTick >= 0,
                            "a member of the squad stood in the port at tick " + t.throughPortTick));
                    verdicts.add(Verdict.of("garrison-arrives-within-the-bound", t.arrivedTick >= 0
                                    && t.arrivedTick - t.orderTick <= t.arrivalBound,
                            "arrived " + (t.arrivedTick < 0 ? "never" : (t.arrivedTick - t.orderTick) + " ticks")
                                    + " after its order; bound " + t.arrivalBound + " ticks, " + ARRIVAL_BOUND
                                    + "x the straight-line march"));
                }
                verdicts.add(Verdict.of("never-in-the-face-of-the-enemy", t.unbarTick < 0 || t.hostileAtUnbar > SallyDoors.SAFE_CELLS,
                        String.format(Locale.ROOT, "nearest hostile %.1f cells from the door when it was unbarred (safe %.0f)",
                                t.hostileAtUnbar, SallyDoors.SAFE_CELLS)));
                verdicts.add(Verdict.of("other-port-stays-barred", t.otherUnbarTick < 0,
                        "the port on the other face was unbarred at tick " + t.otherUnbarTick));
                if (t.guardTick >= 0) {
                    // Where the guard then stands is the machine guard's question (scene machine-guard); this one asks
                    // that the works let it out, and by the port rather than round by a gate.
                    verdicts.add(Verdict.of("guard-goes-out-through-the-port", t.guardThroughPortTick >= 0
                                    && t.guardLeftWorksTick >= 0,
                            "guard attached at tick " + t.guardTick + ", stood in the port at tick " + t.guardThroughPortTick
                                    + ", out of the works at tick " + t.guardLeftWorksTick));
                }
            }
            case "sortie-barred" -> verdicts.add(Verdict.of("no-port-unbarred-under-the-switch",
                    t.unbarTick < 0 && t.otherUnbarTick < 0, "ports unbarred at ticks " + t.unbarTick + ", " + t.otherUnbarTick));
            case "enemy-at-the-door" -> {
                verdicts.add(Verdict.of("barred-while-the-enemy-is-at-the-door", t.unbarTick < 0 || t.unbarTick >= CLEAR_TICK,
                        "port unbarred at tick " + t.unbarTick + "; the raiders stood at its door until tick " + CLEAR_TICK));
                verdicts.add(Verdict.of("unbarred-once-the-door-is-clear", t.unbarTick >= CLEAR_TICK
                                && t.unbarTick - CLEAR_TICK <= UNBAR_BOUND_TICKS,
                        "port unbarred at tick " + t.unbarTick + ", " + (t.unbarTick - CLEAR_TICK) + " ticks after the door cleared"));
            }
            case "attack-at-the-port" -> {
                verdicts.add(Verdict.of("never-unbarred-for-the-attack", t.unbarTick < 0 && t.otherUnbarTick < 0,
                        "ports unbarred at ticks " + t.unbarTick + ", " + t.otherUnbarTick));
                verdicts.add(Verdict.of("no-attacker-in-the-port", t.attackerInPortTick < 0,
                        "an attacker stood in a port at tick " + t.attackerInPortTick));
            }
            default -> {
                verdicts.add(Verdict.of("port-serves-the-taker", t.unbarTick >= 1 && t.unbarTick <= 1 + UNBAR_BOUND_TICKS,
                        "port unbarred at tick " + t.unbarTick + "; the fort was handed to the marines, whose squad in "
                                + "the works was ordered out, at tick 1"));
                verdicts.add(Verdict.of("taker-goes-out-through-the-port", t.throughPortTick >= 0,
                        "a member of the squad stood in the port at tick " + t.throughPortTick));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("orderTick", t.orderTick);
        metrics.put("unbarTick", t.unbarTick);
        metrics.put("otherUnbarTick", t.otherUnbarTick);
        metrics.put("throughPortTick", t.throughPortTick);
        metrics.put("arrivedTick", t.arrivedTick);
        metrics.put("marchTicks", t.orderTick >= 0 && t.arrivedTick >= 0 ? t.arrivedTick - t.orderTick : -1);
        metrics.put("arrivalBoundTicks", t.arrivalBound);
        metrics.put("guardTick", t.guardTick);
        metrics.put("guardArrivedTick", t.guardArrivedTick);
        metrics.put("guardMarchTicks", t.guardTick >= 0 && t.guardArrivedTick >= 0 ? t.guardArrivedTick - t.guardTick : -1);
        metrics.put("guardThroughPortTick", t.guardThroughPortTick);
        metrics.put("hostileAtUnbar", Math.round(t.hostileAtUnbar * 10f) / 10f);
        metrics.put("attackerInsideWorksTick", t.attackerInsideTick);
        metrics.put("guardLeftWorksTick", t.guardLeftWorksTick);
        metrics.put("guardNearestToItsCell", t.guardNearest == Float.MAX_VALUE ? -1f : Math.round(t.guardNearest * 10f) / 10f);
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    /** Reads the ports, the orders and the squads off the simulation every tick. */
    private static final class Trace implements TickObserver {
        final String loop;
        final Layout layout;
        final List<Integer> outgoing;
        final int lance;
        final long[] attackers;
        int orderTick = -1, orderSquad = -1, arrivedTick = -1, throughPortTick = -1, arrivalBound = -1;
        int unbarTick = -1, otherUnbarTick = -1;
        float hostileAtUnbar = Float.MAX_VALUE;
        int guardTick = -1, guardArrivedTick = -1, guardThroughPortTick = -1, guardBound = -1;
        int attackerInPortTick = -1, attackerInsideTick = -1;
        int guardLeftWorksTick = -1;
        float guardNearest = Float.MAX_VALUE;
        private String state = "";

        Trace(String loop, Layout layout, List<Integer> outgoing, int lance, long[] attackers) {
            this.loop = loop;
            this.layout = layout;
            this.outgoing = outgoing;
            this.lance = lance;
            this.attackers = attackers;
        }

        String caption() { return state; }

        @Override public void observe(BattleSimulation sim, int tick) {
            Fort fort = sim.forts().forts().get(0);
            int[][] port = portCells(sim, fort, layout.port());
            int[] mouth = port[port.length - 1];
            if (unbarTick < 0 && fort.sallyPortOpen(layout.port())) {
                unbarTick = tick;
                hostileAtUnbar = nearestHostile(sim, mouth);
            }
            if (otherUnbarTick < 0 && fort.sallyPortOpen(layout.other())) otherUnbarTick = tick;
            for (int id : outgoing) {
                Squad squad = sim.getSquad(id);
                if (squad == null || squad.aliveMembers <= 0) continue;
                int[] goal = destination(sim, squad);
                if (orderSquad < 0 && goal != null && serves(sim, fort, layout.port(), goal[0], goal[1])) {
                    orderSquad = id;
                    orderTick = tick;
                    int[] from = {Math.round(squad.centroidX), Math.round(squad.centroidY)};
                    arrivalBound = Math.round(ARRIVAL_BOUND * GarrisonDirector.etaTicks(from[0], from[1], goal[0], goal[1]));
                }
                if (id != orderSquad) continue;
                for (int i = 0, n = sim.squadMemberCount(id); i < n && throughPortTick < 0; i++) {
                    long member = sim.squadMemberAt(id, i);
                    if (sim.world().isAlive(member) && inPort(port, sim.world().cellX(member), sim.world().cellY(member))) {
                        throughPortTick = tick;
                    }
                }
                if (arrivedTick < 0 && goal != null) {
                    int reach = Math.max(4, sim.garrisonables().radius(layout.stretch()));
                    if (Math.abs(squad.centroidX - goal[0]) <= reach && Math.abs(squad.centroidY - goal[1]) <= reach) {
                        arrivedTick = tick;
                    }
                }
            }
            if (lance >= 0) {
                for (int i = 0, n = sim.squadMemberCount(lance); i < n; i++) {
                    long mech = sim.squadMemberAt(lance, i);
                    if (!sim.world().isAlive(mech)) continue;
                    if (guardThroughPortTick < 0 && inPort(port, sim.world().cellX(mech), sim.world().cellY(mech))) {
                        guardThroughPortTick = tick;
                    }
                    ActiveOrder order = sim.getMechMoveOrderService().activeOrder(mech);
                    if (order == null || order.guard() == null) continue;
                    if (guardTick < 0) {
                        guardTick = tick;
                        guardBound = Math.round(ARRIVAL_BOUND * GarrisonDirector.etaTicks(sim.world().cellX(mech),
                                sim.world().cellY(mech), order.destinationX(), order.destinationY())
                                * UnitType.MARINE.moveSpeed / MechVariant.SIROCCO.moveSpeed);
                    }
                    guardNearest = Math.min(guardNearest, (float) Math.hypot(sim.world().x(mech) - order.destinationX() - 0.5f,
                            sim.world().y(mech) - order.destinationY() - 0.5f));
                    if (guardLeftWorksTick < 0 && !fort.frame().isWorks(sim.world().cellX(mech), sim.world().cellY(mech))) {
                        guardLeftWorksTick = tick;
                    }
                    if (guardArrivedTick < 0 && sim.movement().atCell(mech, order.destinationX(), order.destinationY())) {
                        guardArrivedTick = tick;
                    }
                }
            }
            for (long attacker : attackers) {
                if (!sim.world().isAlive(attacker)) continue;
                int x = sim.world().cellX(attacker);
                int y = sim.world().cellY(attacker);
                if (attackerInPortTick < 0 && inPort(port, x, y)) attackerInPortTick = tick;
                if (attackerInsideTick < 0 && fort.frame().isWorks(x, y)) attackerInsideTick = tick;
            }
            state = String.format(Locale.ROOT, "port %s, order %d, through %d, arrived %d",
                    fort.sallyPortOpen(layout.port()) ? "open" : "barred", orderTick, throughPortTick, arrivedTick);
        }

        private static int[] destination(BattleSimulation sim, Squad squad) {
            var played = sim.getSquadMoveOrderService().activeOrder(squad.id);
            if (played != null) return new int[]{played.destinationX(), played.destinationY()};
            ObjectiveAssignment order = squad.assignedObjective;
            if (order == null || order.targetCellX() < 0) return null;
            return new int[]{order.targetCellX(), order.targetCellY()};
        }

        private static boolean inPort(int[][] port, int x, int y) {
            for (int i = 0; i < port.length - 1; i++) if (port[i][0] == x && port[i][1] == y) return true;
            return false;
        }

        private static float nearestHostile(BattleSimulation sim, int[] mouth) {
            float best = Float.MAX_VALUE;
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                if (!Faction.DEFENDER.hostileTo(sim.identity().faction(unit)) && !Faction.MARINE.hostileTo(sim.identity().faction(unit))) continue;
                Fort fort = sim.forts().forts().get(0);
                Faction holder = fort.wholeHolder();
                if (holder == null || !holder.hostileTo(sim.identity().faction(unit))) continue;
                if (sim.identity().type(unit).isStatic()) continue;
                best = Math.min(best, (float) Math.hypot(sim.world().x(unit) - mouth[0] - 0.5f, sim.world().y(unit) - mouth[1] - 0.5f));
            }
            return best;
        }
    }
}
