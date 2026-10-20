package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.infantry.ApertureHold;
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
import com.dillon.starsectormarines.battle.squad.ReactiveManningService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
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
 * A compound the marines hold, and its building's envelope manned against a counter-attack (`garrisonables.md`,
 * building envelopes): one barracks the marines have taken, with firing windows either side of its south door (and,
 * for the commander, the sandbag apron in front of it), and no garrison of its own. A counter-attacking party, too tough to be shot down so the question
 * stays about command, is put in front of the apron.
 *
 * <ul>
 *   <li>{@code attacker}: the marines' Conquest commander and its lane pool. The taken building's envelope is manned
 *       from the pool against the believed counter-attack, within the share the attack keeps for holding ground, and
 *       the holders stand to at the windows.</li>
 *   <li>{@code player}: one marine squad beside the building in the GARRISON stance, with no commander, and no apron
 *       at the door (an apron, pressed alike and nearer, would be the place the stance takes). The squad holds the
 *       taken building by the click's own hold order and stands to at its windows.</li>
 * </ul>
 *
 * <p>Every verdict is a mechanic, timed from the side's own belief; how long things took is in the metrics.
 */
public final class TakenCompoundScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 120;
    private static final int PULSE_TICKS = 75;
    private static final int MAN_BOUND_TICKS = 6 * PULSE_TICKS;
    private static final int POOL = 8;
    private static final int SQUAD_SIZE = 6;
    private static final int RAIDERS = 6;
    private static final float TOUGH = 1_000_000f;
    private static final int ENTER_TICK = 30;
    private static final int TICKS = 3000;
    /** The barracks: interior box, wall ring one cell outside it, a two-cell door in the south wall. */
    private static final int LEFT = 70, TOP = 60, RIGHT = 89, BOTTOM = 74, DOOR_X = 79;
    private static final int[] WINDOWS_X = {72, 74, 85, 87};

    @Override public String id() { return "taken-compound"; }

    @Override public String label() {
        return "Taken compound: the marines man a taken building's envelope against a counter-attack, from the "
                + "commander's lane pool and by a squad in the GARRISON stance";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("attacker", "player")) reports.add(playOne(loop, frames));
        return reports;
    }

    private record Terrain(NavigationGrid grid, CellTopology topology, List<Doodad> doodads, TacticalNode barracks) {}

    private static Terrain stamp(boolean apron) {
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
        for (int x : WINDOWS_X) {
            grid.setSeeThrough(x, TOP - 1, true);
            topology.setWindow(x, TOP - 1, true);
        }
        for (int y = TOP; y <= BOTTOM; y++) for (int x = LEFT; x <= RIGHT; x++) topology.setGroundKind(x, y, GroundKind.INDOOR);
        GenContext ctx = new GenContext(grid, topology, new Random(7L), WIDTH, HEIGHT, 7L);
        // Guarded by the marines: the compound starts taken.
        TacticalNode barracks = new TacticalNode(TacticalNode.Kind.BARRACKS, (LEFT + RIGHT) / 2, (TOP + BOTTOM) / 2,
                LEFT, TOP, RIGHT, BOTTOM, Faction.MARINE, 60, 0);
        if (apron && CompoundPreparedGround.layFacing(ctx, barracks, 0, -1, (x, y) -> false) == null) {
            throw new IllegalStateException("the barracks' south door took no apron");
        }
        return new Terrain(grid, topology, List.copyOf(ctx.doodads), barracks);
    }

    private static void wall(NavigationGrid grid, CellTopology topology, int x, int y) {
        grid.setWalkable(x, y, false);
        topology.setGroundKind(x, y, GroundKind.INDOOR);
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        // The player's squad is given a building with no apron, so the place it holds is the building's envelope.
        Terrain terrain = stamp(loop.equals("attacker"));
        SceneBuilder builder = SceneBuilder.over(terrain.grid(), terrain.topology()).doodads(terrain.doodads())
                .missionCompletion(false);
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 3, HEIGHT - 3, spec -> spec.moveSpeed(0f).health(TOUGH));
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, WIDTH - 3, 3, spec -> spec.moveSpeed(0f).health(TOUGH));
        boolean attacker = loop.equals("attacker");
        int squads = attacker ? POOL : 1;
        for (int i = 0; i < squads; i++) {
            // East of the building, out of the counter-attack's line: where the assault left them.
            builder.squad("marines-" + i).faction(Faction.MARINE).size(SQUAD_SIZE)
                    .at(RIGHT + 6 + 3 * (i % 2), TOP + 2 * (i / 2)).primary("weapon.smg").health(TOUGH).done();
        }
        builder.squad("counter").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(RAIDERS)
                .at(DOOR_X, 6).primary("weapon.smg").health(TOUGH).stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.setTacticalMap(new TacticalMap(List.of(terrain.barracks())));
        sim.random().setSeed(20261020L);
        int[] marineIds = new int[squads];
        for (int i = 0; i < squads; i++) marineIds[i] = world.squadId("marines-" + i);
        if (attacker) {
            ConquestCommand marines = new ConquestCommand(
                    new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, WIDTH, HEIGHT));
            for (int id : marineIds) {
                sim.claimSquadCommand(id, CommandAuthority.MISSION_COMMAND, marines.strategyId(), "scene lane pool");
            }
            sim.setAutonomousCommander(Faction.MARINE, marines, ConquestCommandDisclosure.INSTANCE);
        } else {
            sim.getReactiveManning().setEnabled(marineIds[0], true);
        }

        Trace trace = new Trace(loop, marineIds, terrain.barracks());
        ScriptedPlayer player = ScriptedPlayer.on(world);
        long[] raiders = world.members("counter");
        int[] halt = {DOOR_X, 46};
        player.at(ENTER_TICK, s -> {
            for (int i = 0; i < raiders.length; i++) {
                s.world().setCellPos(raiders[i], halt[0] + (i % 3) - 1, halt[1] - (i / 3));
            }
        });
        SceneRun.of(world).player(player).observe(trace)
                .frames(frames, loop, 60, tick -> String.format(Locale.ROOT, "%s / tick %d / %s", loop, tick, trace.caption()))
                .run(TICKS);
        return report(loop, trace);
    }

    private SceneReport report(String loop, Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("taken-envelope-is-a-garrisonable", t.envelope >= 0 && t.posts > 0,
                "the taken barracks' envelope is place " + t.envelope + " with " + t.posts + " posts"));
        verdicts.add(Verdict.of("taken-building-manned-within-a-few-pulses",
                t.hotTick >= 0 && t.firstManTick >= t.hotTick && t.firstManTick - t.hotTick <= MAN_BOUND_TICKS,
                "believed counter-attack pressure on the building from tick " + t.hotTick + ", first held at tick "
                        + t.firstManTick + " (bound " + MAN_BOUND_TICKS + " ticks)"));
        verdicts.add(Verdict.of("holders-stand-to-at-the-windows", t.firstStoodTo >= 0,
                "a holder first stood to at the barracks' apertures at tick " + t.firstStoodTo));
        if (loop.equals("attacker")) {
            int cap = HeldGround.cap(POOL);
            verdicts.add(Verdict.of("momentum-kept", t.maxHolding <= cap,
                    "most pool squads holding taken ground at once " + t.maxHolding + " (share " + cap + " of " + POOL + ")"));
        } else {
            verdicts.add(Verdict.of("held-by-the-click-order", t.byClickOrder,
                    "the stance held the building with the player's hold order: " + t.byClickOrder));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("posts", t.posts);
        metrics.put("pressedTick", t.hotTick);
        metrics.put("firstManTick", t.firstManTick);
        metrics.put("firstStoodToTick", t.firstStoodTo);
        metrics.put("maxHolding", t.maxHolding);
        metrics.put("apronHolders", t.maxApron);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        final String loop;
        final int[] marines;
        final TacticalNode barracks;
        int envelope = -1;
        int posts;
        int hotTick = -1;
        int firstManTick = -1;
        int firstStoodTo = -1;
        int maxHolding;
        int maxApron;
        boolean byClickOrder;
        private String state = "";

        Trace(String loop, int[] marines, TacticalNode barracks) {
            this.loop = loop;
            this.marines = marines;
            this.barracks = barracks;
        }

        String caption() { return state; }

        @Override public void observe(BattleSimulation sim, int tick) {
            GarrisonableRegistry places = sim.garrisonables();
            if (envelope < 0) {
                envelope = places.envelopeOf(barracks);
                if (envelope >= 0) posts = places.size(envelope);
            }
            if (envelope < 0) return;
            CommanderInfluenceSnapshot picture = sim.peekCommanderInfluence(Faction.MARINE);
            if (picture != null && hotTick < 0) {
                int bw = places.blockWidth();
                for (int block : places.coveredBlocks(envelope)) {
                    if (picture.hostileAt(block % bw, block / bw) >= GarrisonDirector.START_PRESSURE) hotTick = tick;
                }
            }
            int holding = 0;
            int apron = 0;
            int onBuilding = 0;
            for (int id : marines) {
                Squad squad = sim.getSquad(id);
                if (squad == null || squad.aliveMembers <= 0) continue;
                ObjectiveAssignment order = squad.assignedObjective;
                boolean holds = loop.equals("attacker")
                        ? order != null && order.isEnvelope() && GarrisonableRegistry.sameBuilding(order.targetNode(), barracks)
                        : order != null && order.kind() == AssignmentKind.HOLD_NODE
                        && GarrisonableRegistry.sameBuilding(order.targetNode(), barracks);
                if (order != null && order.mansPlace()) holding++;
                if (order != null && order.isGarrison()) apron++;
                if (!holds) continue;
                onBuilding++;
                if (firstManTick < 0) firstManTick = tick;
                if (loop.equals("player")) {
                    ReactiveManningService.View view = sim.getReactiveManning().view(id);
                    if (view.status() == ReactiveManningService.Status.HOLDING_BUILDING
                            && sim.getSquadMoveOrderService().activeOrder(id) != null) byClickOrder = true;
                }
                if (firstStoodTo < 0 && standsTo(squad)) firstStoodTo = tick;
            }
            maxHolding = Math.max(maxHolding, holding);
            maxApron = Math.max(maxApron, apron);
            state = String.format(Locale.ROOT, "holding the barracks %d, the apron %d", onBuilding, apron);
        }

        private static boolean standsTo(Squad squad) {
            SquadPlan plan = squad.currentPlan;
            if (plan == null || plan.isComplete()) return false;
            SquadPlan.Step step = plan.currentStep();
            return step != null && step.action instanceof ApertureHold;
        }
    }
}
