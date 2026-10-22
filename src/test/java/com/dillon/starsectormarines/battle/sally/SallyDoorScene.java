package com.dillon.starsectormarines.battle.sally;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
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
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sally doors in a walled place that is not a fort (`garrisonables.md`, sally doors): a walled yard, forty cells by fifty,
 * with one gate in the middle of its south face, the shape of a checkpoint or a depot's yard. It is a tactical node with
 * no capture record, so whoever is inside it and unopposed holds it. Its north, west and east faces each get a door from
 * {@code SallyDoorSurvey}; the holder's orders say which, if any, opens. Each loop asks one thing.
 *
 * <ul>
 *   <li>{@code sortie}: a defender squad in the north of the yard is ordered to ground beyond the north wall. The north
 *       door opens within two door pulses, the squad goes out through it and arrives within twice the straight-line
 *       march, and the west and east doors stay closed.</li>
 *   <li>{@code sortie-closed}: the same with {@code -Dbattle.sallyDoors=false} in force: nothing opens, and the walk round
 *       by the gate is measured for comparison.</li>
 *   <li>{@code enemy-at-the-door}: the same order, with raiders standing just outside the north door. It stays closed
 *       while they are there and opens within two pulses of their going.</li>
 *   <li>{@code attack-at-the-door}: defenders hold the yard and a marine squad outside the north wall is ordered into it.
 *       No door opens for it and no marine ever stands in a door's cells.</li>
 *   <li>{@code taken-yard}: marines hold the yard, and the player orders a squad and a Warden out north. The door serves
 *       its new holder, and the chassis goes out through it and arrives within twice its straight-line march.</li>
 *   <li>{@code contested-yard}: marines are ordered out north while defenders are in the yard with them. Nobody holds a
 *       contested place, so no door opens.</li>
 *   <li>{@code shut-behind}: the sortie, then the door: once the squad is out and nobody of the holder's is near it, the
 *       north door shuts behind it, the sortie passed.</li>
 *   <li>{@code enemy-approaches}: the defenders hold a post just outside the open north door, and raiders march on the
 *       yard from the north. The door shuts in their face before any reaches it, nobody ever stands in it after, and no
 *       raider's route still runs through it once it has shut.</li>
 *   <li>{@code barred-on-a-body}: marines hold the yard and sortie north; one of them stands in the open door when the
 *       player bars it. It stays open while he stands there, shuts within two pulses of his leaving, stays shut while
 *       barred, and opens at once when the player unbars it.</li>
 *   <li>{@code round-trip}: the defenders sortie, the door shuts behind them, and their commander calls them home: the door
 *       opens again for the return, they come in through it, and it shuts behind them.</li>
 * </ul>
 *
 * <p>Every loop also asks that no door ever shuts on a body.
 *
 * <p>Bodies are placed rather than walked to their start and the raiders are tough, so every verdict is a mechanic of the
 * doors; who wins is not asked.
 */
public final class SallyDoorScene implements BehaviorScene {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 110;
    /** The yard's wall ring, inclusive. */
    private static final int L = 40, T = 30, R = 79, B = 79;
    private static final int GATE_X = 59;
    private static final int SQUAD = 6;
    private static final float TOUGH = 1_000_000f;
    private static final int ORDER_TICK = 5;
    private static final int CLEAR_TICK = 600;
    /** Two door pulses. */
    private static final int OPEN_BOUND_TICKS = 2 * SallyDoors.PULSE_TICKS + 1;
    /** Arrival within this multiple of the straight-line march (the commander's price, a third over the octile). UNTUNED. */
    private static final float ARRIVAL_BOUND = 2.0f;
    /** Where the holders start, the north of the yard, and where they are sent, twelve cells beyond its north wall. */
    private static final int[] SQUAD_AT = {62, 38};
    private static final int[] SQUAD_GOAL = {62, 18};
    private static final int[] MECH_AT = {52, 40};
    private static final int[] MECH_GOAL = {52, 18};
    /** The post just outside the north door that {@code enemy-approaches} holds: within linger reach, so it stays open. */
    private static final int[] POST = {61, 26};
    private static final int APPROACH_TICK = 300;
    private static final int DOORMAN_TICK = 60;
    private static final int BAR_TICK = 200;
    private static final int LEAVE_TICK = 500;
    private static final int UNBAR_TICK = 800;
    private static final int RECALL_TICK = 900;
    private static final int[] HOME = {62, 45};

    @Override public String id() { return "sally-door"; }

    @Override public String label() {
        return "Sally doors in a walled yard: the holder opens the door on the face it sorties from, never in the enemy's "
                + "face, never for an attacker and never while the yard is contested; infantry and a mech go out through it";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("sortie", "sortie-closed", "enemy-at-the-door", "attack-at-the-door", "taken-yard",
                "contested-yard", "shut-behind", "enemy-approaches", "barred-on-a-body", "round-trip")) {
            boolean was = SallyDoors.enabled();
            SallyDoors.setEnabledForEvidence(!loop.equals("sortie-closed") && was);
            try {
                reports.add(playOne(loop, frames));
            } finally {
                SallyDoors.setEnabledForEvidence(was);
            }
        }
        return reports;
    }

    private static SceneBuilder ground() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        for (int x = L; x <= R; x++) {
            wall(grid, topology, x, T);
            wall(grid, topology, x, B);
        }
        for (int y = T; y <= B; y++) {
            wall(grid, topology, L, y);
            wall(grid, topology, R, y);
        }
        for (int x = GATE_X; x <= GATE_X + 1; x++) {
            grid.setWalkableFloor(x, B);
            grid.setDoorway(x, B, true);
            topology.setWall(x, B, false);
        }
        return SceneBuilder.over(grid, topology).missionCompletion(false);
    }

    private static void wall(NavigationGrid grid, CellTopology topology, int x, int y) {
        grid.setWalkable(x, y, false);
        grid.setWallHp(x, y, 400);
        topology.setWall(x, y, true);
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        SceneBuilder builder = ground();
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 3, HEIGHT - 3, spec -> spec.moveSpeed(0f).health(TOUGH));
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, WIDTH - 3, HEIGHT - 3,
                spec -> spec.moveSpeed(0f).health(TOUGH));
        Faction holder = loop.equals("taken-yard") || loop.equals("contested-yard") || loop.equals("barred-on-a-body")
                ? Faction.MARINE : Faction.DEFENDER;
        int[] goal = loop.equals("enemy-approaches") ? POST : SQUAD_GOAL;
        if (!loop.equals("attack-at-the-door")) {
            var squad = builder.squad("holders").faction(holder)
                    .type(holder == Faction.MARINE ? UnitType.MARINE : UnitType.MILITIA)
                    .size(SQUAD).at(SQUAD_AT[0], SQUAD_AT[1]).primary("weapon.smg").health(TOUGH);
            // The player's ground click is the marines' order; the defenders' is their commander's, from the first tick.
            if (holder == Faction.DEFENDER) {
                squad.assigned(id -> ObjectiveAssignment.attackMove(id, goal[0], goal[1]));
            }
            squad.done();
        }
        boolean lance = loop.equals("taken-yard");
        if (lance) {
            builder.squad("lance").faction(Faction.MARINE).size(1).at(MECH_AT[0], MECH_AT[1])
                    .mech(MechVariant.WARDEN, MechRole.BALANCED).done();
        }
        if (loop.equals("enemy-at-the-door")) {
            builder.squad("raiders").faction(Faction.MARINE).size(SQUAD).at(61, T - 6).primary("weapon.smg").health(TOUGH)
                    .stationary().done();
        }
        if (loop.equals("attack-at-the-door")) {
            builder.squad("picket").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(SQUAD).at(60, 60)
                    .primary("weapon.smg").health(TOUGH).stationary().done();
            builder.squad("attack").faction(Faction.MARINE).size(SQUAD).at(SQUAD_GOAL[0], SQUAD_GOAL[1])
                    .primary("weapon.smg").health(TOUGH).done();
        }
        if (loop.equals("enemy-approaches")) {
            builder.squad("raiders").faction(Faction.MARINE).size(SQUAD).at(61, 2).primary("weapon.smg").health(TOUGH)
                    .done();
        }
        if (loop.equals("barred-on-a-body")) {
            builder.squad("doorman").faction(Faction.MARINE).size(1).at(62, 40).primary("weapon.smg").health(TOUGH)
                    .stationary().done();
        }
        if (loop.equals("contested-yard")) {
            builder.squad("intruders").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(SQUAD).at(60, 70)
                    .primary("weapon.smg").health(TOUGH).stationary().done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261020L);
        sim.setTacticalMap(new TacticalMap(List.of(new TacticalNode(TacticalNode.Kind.INNER_POSITION, 60, 40, L, T, R, B,
                Faction.DEFENDER, 1, SQUAD))));

        ScriptedPlayer player = ScriptedPlayer.on(world);
        switch (loop) {
            case "attack-at-the-door" -> player.moveSquad(ORDER_TICK, "attack", 60, 40);
            default -> {
                if (holder == Faction.MARINE) player.moveSquad(ORDER_TICK, "holders", SQUAD_GOAL[0], SQUAD_GOAL[1]);
            }
        }
        if (lance) player.moveMech(ORDER_TICK, "lance", 0, MECH_GOAL[0], MECH_GOAL[1]);
        if (loop.equals("enemy-approaches")) player.moveSquad(APPROACH_TICK, "raiders", 60, 45);
        if (loop.equals("barred-on-a-body")) {
            long doorman = world.members("doorman")[0];
            player.at(DOORMAN_TICK, s -> {
                SallyDoor north = northDoor(s);
                if (north != null) s.world().setCellPos(doorman, north.cells()[0][0], north.cells()[0][1]);
            });
            player.at(BAR_TICK, s -> s.sallyDoors().order(s, northIndex(s), Faction.MARINE));
            player.at(LEAVE_TICK, s -> s.world().setCellPos(doorman, 60, 60));
            player.at(UNBAR_TICK, s -> s.sallyDoors().order(s, northIndex(s), Faction.MARINE));
        }
        if (loop.equals("round-trip")) {
            player.at(RECALL_TICK, s -> {
                Squad squad = world.squad("holders");
                squad.assignedObjective = ObjectiveAssignment.attackMove(squad.id, HOME[0], HOME[1]);
            });
        }
        if (loop.equals("enemy-at-the-door")) {
            long[] raiders = world.members("raiders");
            player.at(CLEAR_TICK, s -> {
                for (int i = 0; i < raiders.length; i++) s.world().setCellPos(raiders[i], 100 + i, 10);
            });
        }
        int orderTick = holder == Faction.DEFENDER && !loop.equals("attack-at-the-door") ? 0 : ORDER_TICK;
        long[] hostiles = loop.equals("attack-at-the-door") ? world.members("attack")
                : loop.equals("enemy-approaches") ? world.members("raiders") : new long[0];
        Trace trace = new Trace(world, holder, lance, orderTick, hostiles, !loop.equals("attack-at-the-door"));
        int ticks = switch (loop) {
            case "attack-at-the-door", "enemy-approaches" -> 1500;
            case "shut-behind", "barred-on-a-body" -> 1200;
            case "round-trip" -> 2100;
            default -> 1800;
        };
        try {
            SceneRun.of(world).player(player).observe(trace)
                    .frames(frames, loop, 60, tick -> String.format(Locale.ROOT, "%s / tick %d / %s", loop, tick, trace.state))
                    .run(ticks);
        } finally {
            sim.close();
        }
        return report(loop, ticks, trace);
    }

    /** The north door of the yard, or null. */
    private static SallyDoor northDoor(BattleSimulation sim) {
        int index = northIndex(sim);
        return index < 0 ? null : sim.sallyDoors().doors(sim).get(index);
    }

    private static int northIndex(BattleSimulation sim) {
        List<SallyDoor> all = sim.sallyDoors().doors(sim);
        for (int i = 0; i < all.size(); i++) if (all.get(i).outY() < 0) return i;
        return -1;
    }

    private SceneReport report(String loop, int ticks, Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("the-yard-has-a-door-on-each-gateless-face", t.doorsFound == 3 && t.north >= 0,
                t.doorsFound + " doors found; north door " + (t.north >= 0 ? "found" : "missing")));
        verdicts.add(Verdict.of("never-shuts-on-a-body", t.shutOnBody < 0,
                "a door shut with a body in it at tick " + t.shutOnBody));
        switch (loop) {
            case "sortie" -> {
                verdicts.add(Verdict.of("north-door-opens-for-the-sortie", t.northOpen >= t.orderTick
                                && t.northOpen - t.orderTick <= OPEN_BOUND_TICKS,
                        "north door opened at tick " + t.northOpen + " for an order at tick " + t.orderTick
                                + " (bound " + OPEN_BOUND_TICKS + " ticks)"));
                verdicts.add(Verdict.of("other-doors-stay-closed", t.otherOpen < 0,
                        "another door opened at tick " + t.otherOpen));
                verdicts.add(Verdict.of("squad-goes-out-through-the-door", t.squadThrough >= 0,
                        "a member stood in the door at tick " + t.squadThrough));
                verdicts.add(Verdict.of("squad-arrives-within-the-bound", t.squadArrived >= 0
                                && t.squadArrived - t.orderTick <= t.squadBound,
                        "arrived " + (t.squadArrived < 0 ? "never" : (t.squadArrived - t.orderTick) + " ticks")
                                + " after its order; bound " + t.squadBound));
            }
            case "sortie-closed" -> verdicts.add(Verdict.of("no-door-opens-under-the-switch",
                    t.northOpen < 0 && t.otherOpen < 0, "doors opened at ticks " + t.northOpen + ", " + t.otherOpen));
            case "enemy-at-the-door" -> {
                verdicts.add(Verdict.of("closed-while-the-enemy-is-at-the-door", t.northOpen < 0 || t.northOpen >= CLEAR_TICK,
                        "north door opened at tick " + t.northOpen + "; raiders stood at it until tick " + CLEAR_TICK));
                verdicts.add(Verdict.of("opens-once-the-door-is-clear", t.northOpen >= CLEAR_TICK
                                && t.northOpen - CLEAR_TICK <= OPEN_BOUND_TICKS,
                        "north door opened at tick " + t.northOpen));
            }
            case "attack-at-the-door" -> {
                verdicts.add(Verdict.of("never-opened-for-the-attack", t.northOpen < 0 && t.otherOpen < 0,
                        "doors opened at ticks " + t.northOpen + ", " + t.otherOpen));
                verdicts.add(Verdict.of("no-attacker-in-a-door", t.attackerInDoor < 0,
                        "an attacker stood in a door at tick " + t.attackerInDoor));
            }
            case "taken-yard" -> {
                verdicts.add(Verdict.of("door-serves-the-new-holder", t.northOpen >= ORDER_TICK
                                && t.northOpen - ORDER_TICK <= OPEN_BOUND_TICKS,
                        "north door opened at tick " + t.northOpen));
                verdicts.add(Verdict.of("holder-goes-out-through-the-door", t.squadThrough >= 0,
                        "a member stood in the door at tick " + t.squadThrough));
                verdicts.add(Verdict.of("mech-goes-out-through-the-door", t.mechThrough >= 0,
                        "the Warden stood in the door at tick " + t.mechThrough));
                verdicts.add(Verdict.of("mech-arrives-within-the-bound", t.mechArrived >= 0
                                && t.mechArrived - ORDER_TICK <= t.mechBound,
                        "arrived " + (t.mechArrived < 0 ? "never" : (t.mechArrived - ORDER_TICK) + " ticks")
                                + " after its order; bound " + t.mechBound));
            }
            case "contested-yard" -> verdicts.add(Verdict.of("no-door-while-contested", t.northOpen < 0 && t.otherOpen < 0,
                    "doors opened at ticks " + t.northOpen + ", " + t.otherOpen));
            case "shut-behind" -> {
                verdicts.add(Verdict.of("north-door-opens-for-the-sortie", t.northOpen >= 0, "opened at tick " + t.northOpen));
                verdicts.add(Verdict.of("squad-goes-out-through-the-door", t.squadThrough >= 0,
                        "a member stood in the door at tick " + t.squadThrough));
                verdicts.add(Verdict.of("shuts-behind-the-sortie", t.firstShut > t.squadThrough && t.squadThrough >= 0
                                && "SORTIE_PASSED".equals(t.firstShutWhy),
                        "first shut at tick " + t.firstShut + " (" + t.firstShutWhy + "); last in the door at tick "
                                + t.squadThrough));
                verdicts.add(Verdict.of("stays-shut-with-nobody-wanting-it", t.reopened < 0,
                        "opened again at tick " + t.reopened));
            }
            case "enemy-approaches" -> {
                verdicts.add(Verdict.of("north-door-opens-for-the-sortie", t.northOpen >= 0 && t.northOpen < APPROACH_TICK,
                        "opened at tick " + t.northOpen));
                verdicts.add(Verdict.of("open-while-the-post-holds-it", t.firstShut < 0 || t.firstShut >= APPROACH_TICK,
                        "first shut at tick " + t.firstShut + ", before the raiders marched at " + APPROACH_TICK));
                verdicts.add(Verdict.of("shuts-in-the-enemys-face", t.firstShut >= APPROACH_TICK
                                && "ENEMY".equals(t.firstShutWhy) && t.hostileAtShut <= SallyDoors.SAFE_CELLS + 1f,
                        "first shut at tick " + t.firstShut + " (" + t.firstShutWhy + "), nearest raider "
                                + String.format(Locale.ROOT, "%.1f", t.hostileAtShut) + " cells from the mouth"));
                verdicts.add(Verdict.of("no-raider-in-a-door", t.attackerInDoor < 0,
                        "a raider stood in a door at tick " + t.attackerInDoor));
                verdicts.add(Verdict.of("no-route-through-the-shut-door", t.routeThroughShut < 0,
                        "a raider's route ran through the shut door at tick " + t.routeThroughShut));
            }
            case "barred-on-a-body" -> {
                verdicts.add(Verdict.of("north-door-opens-for-the-sortie", t.northOpen >= 0 && t.northOpen < BAR_TICK,
                        "opened at tick " + t.northOpen));
                verdicts.add(Verdict.of("open-while-the-doorman-stands-in-it", t.firstShut < 0 || t.firstShut >= LEAVE_TICK,
                        "first shut at tick " + t.firstShut + "; the doorman left at " + LEAVE_TICK));
                verdicts.add(Verdict.of("bar-shuts-once-the-door-is-clear", t.firstShut >= LEAVE_TICK
                                && t.firstShut - LEAVE_TICK <= OPEN_BOUND_TICKS && "BARRED".equals(t.firstShutWhy),
                        "first shut at tick " + t.firstShut + " (" + t.firstShutWhy + ")"));
                verdicts.add(Verdict.of("unbar-opens-at-once", t.reopened >= UNBAR_TICK && t.reopened - UNBAR_TICK <= 1,
                        "opened again at tick " + t.reopened + " for an unbar at " + UNBAR_TICK));
            }
            case "round-trip" -> {
                verdicts.add(Verdict.of("shuts-behind-the-sortie", t.firstShut >= 0 && t.firstShut < RECALL_TICK,
                        "first shut at tick " + t.firstShut + " (" + t.firstShutWhy + ")"));
                verdicts.add(Verdict.of("opens-for-the-way-home", t.reopened >= RECALL_TICK
                                && t.reopened - RECALL_TICK <= OPEN_BOUND_TICKS,
                        "opened again at tick " + t.reopened + " for a recall at " + RECALL_TICK));
                verdicts.add(Verdict.of("comes-home-through-the-door", t.homeThrough >= RECALL_TICK,
                        "a member stood in the door at tick " + t.homeThrough + " after the recall"));
                verdicts.add(Verdict.of("shuts-behind-the-return", t.secondShut > t.homeThrough && t.homeThrough >= 0,
                        "shut again at tick " + t.secondShut));
            }
            default -> throw new IllegalArgumentException(loop);
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("doorsFound", t.doorsFound);
        metrics.put("northOpenTick", t.northOpen);
        metrics.put("otherOpenTick", t.otherOpen);
        metrics.put("squadThroughTick", t.squadThrough);
        metrics.put("squadMarchTicks", t.squadArrived >= 0 ? t.squadArrived - t.orderTick : -1);
        metrics.put("squadBoundTicks", t.squadBound);
        metrics.put("mechThroughTick", t.mechThrough);
        metrics.put("mechMarchTicks", t.mechArrived >= 0 ? t.mechArrived - ORDER_TICK : -1);
        metrics.put("mechBoundTicks", t.mechBound);
        metrics.put("firstShutTick", t.firstShut);
        metrics.put("reopenedTick", t.reopened);
        metrics.put("secondShutTick", t.secondShut);
        metrics.put("homeThroughTick", t.homeThrough);
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    /** The march the commander prices for the straight line, at a speed, in ticks. */
    private static int bound(int[] from, int[] to, float speed) {
        int dx = Math.abs(from[0] - to[0]);
        int dy = Math.abs(from[1] - to[1]);
        float octile = Math.max(dx, dy) + 0.41f * Math.min(dx, dy);
        return Math.round(ARRIVAL_BOUND * 1.35f * octile / (speed * BattleSimulation.TICK_DT));
    }

    private static final class Trace implements TickObserver {
        final SceneWorld world;
        final boolean lance;
        final long[] attackers;
        final Faction holder;
        final int orderTick;
        int doorsFound = -1, north = -1;
        int northOpen = -1, otherOpen = -1, squadThrough = -1, squadArrived = -1, mechThrough = -1, mechArrived = -1;
        int attackerInDoor = -1;
        int firstShut = -1, reopened = -1, secondShut = -1, homeThrough = -1, shutOnBody = -1, routeThroughShut = -1;
        String firstShutWhy = "";
        float hostileAtShut = Float.POSITIVE_INFINITY;
        private boolean northWasOpen;
        final boolean holders;
        final int squadBound, mechBound;
        String state = "";

        Trace(SceneWorld world, Faction holder, boolean lance, int orderTick, long[] attackers, boolean holders) {
            this.world = world;
            this.holders = holders;
            this.orderTick = orderTick;
            this.holder = holder;
            this.lance = lance;
            this.attackers = attackers;
            squadBound = bound(SQUAD_AT, SQUAD_GOAL, (holder == Faction.MARINE ? UnitType.MARINE : UnitType.MILITIA).moveSpeed);
            mechBound = bound(MECH_AT, MECH_GOAL, MechVariant.WARDEN.moveSpeed);
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            SallyDoors doors = sim.sallyDoors();
            List<SallyDoor> all = doors.doors(sim);
            if (doorsFound < 0) {
                doorsFound = all.size();
                for (int i = 0; i < all.size(); i++) if (all.get(i).outY() < 0) north = i;
            }
            for (int i = 0; i < all.size(); i++) {
                if (!doors.isOpen(i)) continue;
                if (i == north && northOpen < 0) northOpen = tick;
                if (i != north && otherOpen < 0) otherOpen = tick;
            }
            boolean northOpenNow = north >= 0 && doors.isOpen(north);
            if (northWasOpen && !northOpenNow) {
                if (SallyDoors.bodyInDoor(sim, all.get(north)) && shutOnBody < 0) shutOnBody = tick;
                if (firstShut < 0) {
                    firstShut = tick;
                    List<int[]> closings = doors.closings();
                    firstShutWhy = SallyDoors.Shut.values()[closings.get(closings.size() - 1)[3]].name();
                    SallyDoor door = all.get(north);
                    for (long hostile : attackers) {
                        if (!sim.world().isAlive(hostile)) continue;
                        hostileAtShut = Math.min(hostileAtShut, (float) Math.hypot(sim.world().x(hostile)
                                - (door.mouthX() + 0.5f), sim.world().y(hostile) - (door.mouthY() + 0.5f)));
                    }
                } else if (reopened >= 0 && secondShut < 0) {
                    secondShut = tick;
                }
            }
            if (!northWasOpen && northOpenNow && firstShut >= 0 && reopened < 0) reopened = tick;
            northWasOpen = northOpenNow;
            if (firstShut >= 0 && !northOpenNow && routeThroughShut < 0) {
                float[] box = SallyDoors.box(all.get(north));
                for (long hostile : attackers) {
                    if (sim.world().isAlive(hostile) && SallyDoors.routeCrosses(sim, hostile, box, 0f)) routeThroughShut = tick;
                }
            }
            Squad squad = holders ? world.squad("holders") : null;
            if (squad != null) {
                for (long member : world.members("holders")) {
                    if (!sim.world().isAlive(member)) continue;
                    boolean in = inDoor(all, north, sim.world().cellX(member), sim.world().cellY(member));
                    if (in && (firstShut < 0 || reopened < 0)) squadThrough = tick;
                    if (in && reopened >= 0 && tick >= RECALL_TICK) homeThrough = tick;
                }
                if (squadArrived < 0 && tick > orderTick && Math.abs(squad.centroidX - SQUAD_GOAL[0]) <= 4
                        && Math.abs(squad.centroidY - SQUAD_GOAL[1]) <= 4) squadArrived = tick;
            }
            if (lance) {
                long mech = world.members("lance")[0];
                if (sim.world().isAlive(mech)) {
                    int x = sim.world().cellX(mech), y = sim.world().cellY(mech);
                    if (mechThrough < 0 && inDoor(all, north, x, y)) mechThrough = tick;
                    if (mechArrived < 0 && tick > ORDER_TICK && Math.abs(x - MECH_GOAL[0]) <= 2 && Math.abs(y - MECH_GOAL[1]) <= 2) {
                        mechArrived = tick;
                    }
                }
            }
            for (long attacker : attackers) {
                if (!sim.world().isAlive(attacker)) continue;
                for (int i = 0; i < all.size() && attackerInDoor < 0; i++) {
                    if (inDoor(all, i, sim.world().cellX(attacker), sim.world().cellY(attacker))) attackerInDoor = tick;
                }
            }
            state = String.format(Locale.ROOT, "north door %s, through %d, arrived %d",
                    north >= 0 && doors.isOpen(north) ? "open" : "closed", squadThrough, squadArrived);
        }

        private static boolean inDoor(List<SallyDoor> all, int index, int x, int y) {
            if (index < 0) return false;
            for (int[] c : all.get(index).cells()) if (c[0] == x && c[1] == y) return true;
            return false;
        }
    }
}
