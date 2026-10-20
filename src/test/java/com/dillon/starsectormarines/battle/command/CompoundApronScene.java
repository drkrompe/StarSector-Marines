package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
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
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.CompoundPreparedGround;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
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
 * A compound's prepared ground manned under pressure (`compound-prepared-ground.md`): one barracks with a sandbag apron
 * laid at its south door by the production stamper, the defender's Conquest commander and a pool of patrols behind the
 * building. A raiding party, too tough to be shot down so the question stays about command, is put in front of the
 * apron, and each loop changes one thing.
 *
 * <ul>
 *   <li>{@code quiet}: the raiders never come. The apron is a garrisonable like a belt and stays empty.</li>
 *   <li>{@code pressed}: the raiders stand off the door. The apron is manned within a few command pulses of the
 *       commander believing it pressed, and the garrison digs in behind the bags.</li>
 *   <li>{@code breach}: once the garrison is dug in, the raiders are on the apron beside it. The garrison falls back
 *       into the building it fronts and holds it: the apron is the layer in front of the barracks.</li>
 * </ul>
 *
 * <p>Every verdict is a mechanic of the commander, timed from its own belief; how long things took is in the metrics.
 */
public final class CompoundApronScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 120;
    private static final int PULSE_TICKS = 75;
    private static final int MAN_BOUND_TICKS = 6 * PULSE_TICKS;
    private static final int PATROLS = 4;
    private static final int PATROL_SIZE = 6;
    private static final int RAIDERS = 6;
    private static final float TOUGH = 1_000_000f;
    private static final int ENTER_TICK = 30;
    private static final int BREACH_AFTER_DUG_TICKS = 300;
    /** The barracks: interior box, wall ring one cell outside it, a two-cell door in the south wall. */
    private static final int LEFT = 70, TOP = 60, RIGHT = 89, BOTTOM = 74, DOOR_X = 79;

    @Override public String id() { return "compound-apron"; }

    @Override public String label() {
        return "Compound prepared ground: a quiet apron stays empty, a pressed apron is manned and dug in, and a breached "
                + "garrison falls back into the building it fronts";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("quiet", "pressed", "breach")) reports.add(playOne(loop, frames));
        return reports;
    }

    private record Terrain(NavigationGrid grid, CellTopology topology, List<Doodad> doodads, TacticalNode barracks,
                           CompoundPreparedGround.Apron apron) {}

    private static Terrain stamp() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        for (int x = LEFT - 1; x <= RIGHT + 1; x++) {
            wall(grid, topology, x, TOP - 1);
            wall(grid, topology, x, BOTTOM + 1);
        }
        for (int y = TOP - 1; y <= BOTTOM + 1; y++) {
            wall(grid, topology, LEFT - 1, y);
            wall(grid, topology, RIGHT + 1, y);
        }
        for (int x = DOOR_X; x <= DOOR_X + 1; x++) {
            grid.setWalkableFloor(x, TOP - 1);
            grid.setDoorway(x, TOP - 1, true);
        }
        for (int y = TOP; y <= BOTTOM; y++) for (int x = LEFT; x <= RIGHT; x++) topology.setGroundKind(x, y, GroundKind.INDOOR);
        GenContext ctx = new GenContext(grid, topology, new Random(7L), WIDTH, HEIGHT, 7L);
        TacticalNode barracks = new TacticalNode(TacticalNode.Kind.BARRACKS, (LEFT + RIGHT) / 2, (TOP + BOTTOM) / 2,
                LEFT, TOP, RIGHT, BOTTOM, Faction.DEFENDER, 60, 0);
        CompoundPreparedGround.Apron apron = CompoundPreparedGround.layFacing(ctx, barracks, 0, -1, (x, y) -> false);
        if (apron == null) throw new IllegalStateException("the barracks' south door took no apron");
        return new Terrain(grid, topology, List.copyOf(ctx.doodads), barracks, apron);
    }

    private static void wall(NavigationGrid grid, CellTopology topology, int x, int y) {
        grid.setWalkable(x, y, false);
        topology.setGroundKind(x, y, GroundKind.INDOOR);
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        Terrain terrain = stamp();
        SceneBuilder builder = SceneBuilder.over(terrain.grid(), terrain.topology()).doodads(terrain.doodads())
                .missionCompletion(false);
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 3, 3, spec -> spec.moveSpeed(0f).health(TOUGH));
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, WIDTH - 3, HEIGHT - 3,
                spec -> spec.moveSpeed(0f).health(TOUGH));
        for (int i = 0; i < PATROLS; i++) {
            builder.squad("patrol-" + i).faction(Faction.DEFENDER).type(UnitType.MILITIA).size(PATROL_SIZE)
                    .at(LEFT + 2 + 5 * i, BOTTOM + 8).primary("weapon.smg").health(TOUGH).done();
        }
        boolean raid = !loop.equals("quiet");
        int apronY = terrain.apron().bags().get(0).y();
        int[] field = {DOOR_X, 6};
        int[] halt = {DOOR_X, apronY - 7};
        if (raid) {
            builder.squad("raiders").faction(Faction.MARINE).size(RAIDERS).at(field[0], field[1])
                    .primary("weapon.smg").health(TOUGH).stationary().done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.setTacticalMap(new TacticalMap(List.of(terrain.barracks())));
        sim.random().setSeed(20261020L);
        ConquestDefenderCommand defenders = new ConquestDefenderCommand(
                new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, WIDTH, HEIGHT));
        int[] patrolIds = new int[PATROLS];
        for (int i = 0; i < PATROLS; i++) {
            patrolIds[i] = world.squadId("patrol-" + i);
            sim.claimSquadCommand(patrolIds[i], CommandAuthority.MISSION_COMMAND, defenders.strategyId(), "scene patrol pool");
        }
        sim.setAutonomousCommander(Faction.DEFENDER, defenders, ConquestCommandDisclosure.INSTANCE);

        int[] door = {DOOR_X, TOP - 1};
        Trace trace = new Trace(loop, door, patrolIds, raid ? world.members("raiders") : new long[0]);
        ScriptedPlayer player = ScriptedPlayer.on(world);
        if (raid) {
            long[] raiders = world.members("raiders");
            player.at(ENTER_TICK, s -> place(s, raiders, halt));
        }
        int ticks = loop.equals("quiet") ? 2400 : 4200;
        SceneRun.of(world).player(player).observe(trace)
                .frames(frames, loop, 60, tick -> String.format(Locale.ROOT, "%s / tick %d / %s", loop, tick, trace.caption()))
                .run(ticks);
        return report(loop, ticks, trace);
    }

    private static void place(BattleSimulation sim, long[] raiders, int[] at) {
        for (int i = 0; i < raiders.length; i++) {
            sim.world().setCellPos(raiders[i], at[0] + (i % 3) - 1, at[1] - (i / 3));
        }
    }

    private SceneReport report(String loop, int ticks, Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "quiet" -> {
                verdicts.add(Verdict.of("apron-is-a-garrisonable", t.apron >= 0,
                        "the apron's run is place " + t.apron + " in the registry"));
                verdicts.add(Verdict.of("quiet-apron-left-empty", t.manOrders == 0, t.manOrders + " manning orders"));
            }
            case "pressed" -> {
                manned(verdicts, t);
                verdicts.add(Verdict.of("garrison-digs-in-behind-the-bags", t.firstDugIn >= 0,
                        "first member dug in on the apron at tick " + t.firstDugIn));
                verdicts.add(Verdict.of("reserve-kept", t.maxCommitted < PATROLS,
                        "most patrols manning or staging at once " + t.maxCommitted + " of " + PATROLS));
            }
            default -> {
                manned(verdicts, t);
                verdicts.add(Verdict.of("breach-made", t.breachInsideTick >= 0,
                        "raiders on the apron from tick " + t.breachInsideTick));
                boolean fellBack = t.fallBackTick >= 0 && t.breachInsideTick >= 0
                        && t.fallBackTick - t.breachInsideTick <= MAN_BOUND_TICKS && t.fallBackIntoBuilding;
                verdicts.add(Verdict.of("breached-garrison-falls-back-into-the-building", fellBack,
                        "fall back ordered at tick " + t.fallBackTick + " ("
                                + (t.fallBackIntoBuilding ? "a hold of the barracks" : "not into the barracks")
                                + "), raiders on the apron from tick " + t.breachInsideTick));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("apronCells", t.apronCells);
        metrics.put("pressedTick", t.hotTick);
        metrics.put("firstManTick", t.firstManTick);
        metrics.put("firstDugInTick", t.firstDugIn);
        metrics.put("manOrders", t.manOrders);
        metrics.put("maxCommitted", t.maxCommitted);
        metrics.put("breachInsideTick", t.breachInsideTick);
        metrics.put("fallBackTick", t.fallBackTick);
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    private static void manned(List<Verdict> verdicts, Trace t) {
        verdicts.add(Verdict.of("pressed-apron-manned-within-a-few-pulses",
                t.hotTick >= 0 && t.firstManTick >= t.hotTick && t.firstManTick - t.hotTick <= MAN_BOUND_TICKS,
                "believed pressure on the apron at the manning level from tick " + t.hotTick
                        + ", first manned at tick " + t.firstManTick + " (bound " + MAN_BOUND_TICKS + " ticks)"));
    }

    private static final class Trace implements TickObserver {
        final String loop;
        final int[] door;
        final int[] patrols;
        final long[] raiders;
        int apron = -1;
        int apronCells;
        int hotTick = -1;
        int firstManTick = -1;
        int manOrders;
        int firstDugIn = -1;
        int maxCommitted;
        int garrisonSquad = -1;
        int breachTick = -1;
        int breachInsideTick = -1;
        int fallBackTick = -1;
        boolean fallBackIntoBuilding;
        private final Map<Integer, Integer> manning = new LinkedHashMap<>();
        private String state = "";

        Trace(String loop, int[] door, int[] patrols, long[] raiders) {
            this.loop = loop;
            this.door = door;
            this.patrols = patrols;
            this.raiders = raiders;
        }

        String caption() { return state; }

        @Override public void observe(BattleSimulation sim, int tick) {
            GarrisonableRegistry places = sim.garrisonables();
            if (apron < 0) {
                // The apron's run: the place nearest the door.
                long best = Long.MAX_VALUE;
                for (int g = 0; g < places.count(); g++) {
                    long dx = places.centreX(g) - door[0];
                    long dy = places.centreY(g) - door[1];
                    if (dx * dx + dy * dy < best) {
                        best = dx * dx + dy * dy;
                        apron = g;
                    }
                }
                if (apron >= 0) apronCells = places.size(apron);
            }
            if (apron < 0) return;
            CommanderInfluenceSnapshot picture = sim.peekCommanderInfluence(Faction.DEFENDER);
            if (picture != null && hotTick < 0) {
                int bw = places.blockWidth();
                for (int block : places.coveredBlocks(apron)) {
                    if (picture.hostileAt(block % bw, block / bw) >= GarrisonDirector.START_PRESSURE) hotTick = tick;
                }
            }
            CommanderSnapshot<?> snapshot = sim.getCommanderSnapshot(Faction.DEFENDER);
            int committed = 0;
            for (int id : patrols) {
                Squad squad = sim.getSquad(id);
                if (squad == null || squad.aliveMembers <= 0) continue;
                ObjectiveAssignment order = squad.assignedObjective;
                CommandDirective directive = snapshot == null ? null : snapshot.directiveFor(id);
                String reason = directive == null ? "" : directive.reason();
                if (reason.equals("DEFENDER_GARRISONABLE_STAGE")) committed++;
                if (order != null && order.isGarrison()) {
                    committed++;
                    int run = places.idAt(order.targetCellX(), order.targetCellY());
                    if (run < 0) run = order.occupyRunId();
                    Integer before = manning.put(id, run);
                    if (run == apron && (before == null || before != run)) {
                        manOrders++;
                        if (firstManTick < 0) firstManTick = tick;
                    }
                    if (run == apron && firstDugIn < 0) {
                        for (int i = 0, n = sim.squadMemberCount(id); i < n; i++) {
                            long member = sim.squadMemberAt(id, i);
                            if (sim.isDugIn(member) && places.idAt(sim.world().cellX(member), sim.world().cellY(member)) == apron) {
                                firstDugIn = tick;
                                garrisonSquad = id;
                                break;
                            }
                        }
                    }
                } else {
                    manning.remove(id);
                }
                if (id == garrisonSquad && breachInsideTick >= 0 && fallBackTick < 0
                        && reason.equals("DEFENDER_GARRISONABLE_FALL_BACK") && order != null) {
                    fallBackTick = tick;
                    fallBackIntoBuilding = order.targetCellX() >= LEFT && order.targetCellX() <= RIGHT
                            && order.targetCellY() >= TOP && order.targetCellY() <= BOTTOM;
                }
            }
            maxCommitted = Math.max(maxCommitted, committed);
            if (loop.equals("breach") && breachTick < 0 && garrisonSquad >= 0 && tick >= firstDugIn + BREACH_AFTER_DUG_TICKS) {
                breachTick = tick;
                Squad held = sim.getSquad(garrisonSquad);
                int[] into = intoApron(sim, places, Math.round(held.centroidX), Math.round(held.centroidY));
                for (long raider : raiders) sim.world().setCellPos(raider, into[0], into[1]);
            }
            if (breachTick >= 0 && breachInsideTick < 0) {
                for (long raider : raiders) {
                    if (places.idAt(sim.world().cellX(raider), sim.world().cellY(raider)) == apron) {
                        breachInsideTick = tick;
                        break;
                    }
                }
            }
            state = String.format(Locale.ROOT, "apron manning orders %d%s", manOrders,
                    fallBackTick >= 0 ? ", fell back into the barracks" : "");
        }

        /** A cell of the apron a few cells from the garrison: where a breach puts the raiders. */
        private int[] intoApron(BattleSimulation sim, GarrisonableRegistry places, int cx, int cy) {
            int best = -1, bx = cx, by = cy;
            for (int dy = -10; dy <= 10; dy++) {
                for (int dx = -10; dx <= 10; dx++) {
                    int x = cx + dx, y = cy + dy;
                    if (places.idAt(x, y) != apron || !sim.getGrid().isWalkable(x, y)) continue;
                    int d = Math.max(Math.abs(dx), Math.abs(dy));
                    if (d < 2) continue;
                    if (best < 0 || d < best) {
                        best = d;
                        bx = x;
                        by = y;
                    }
                }
            }
            return new int[]{bx, by};
        }
    }
}
