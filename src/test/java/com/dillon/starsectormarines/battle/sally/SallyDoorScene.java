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
 *   <li>{@code taken-yard}: marines hold the yard, and the player orders a squad and a Sirocco out north. The door serves
 *       its new holder, and the chassis goes out through it and arrives within twice its straight-line march.</li>
 *   <li>{@code contested-yard}: marines are ordered out north while defenders are in the yard with them. Nobody holds a
 *       contested place, so no door opens.</li>
 * </ul>
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

    @Override public String id() { return "sally-door"; }

    @Override public String label() {
        return "Sally doors in a walled yard: the holder opens the door on the face it sorties from, never in the enemy's "
                + "face, never for an attacker and never while the yard is contested; infantry and a mech go out through it";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("sortie", "sortie-closed", "enemy-at-the-door", "attack-at-the-door", "taken-yard",
                "contested-yard")) {
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
        Faction holder = loop.equals("taken-yard") || loop.equals("contested-yard") ? Faction.MARINE : Faction.DEFENDER;
        if (!loop.equals("attack-at-the-door")) {
            var squad = builder.squad("holders").faction(holder)
                    .type(holder == Faction.MARINE ? UnitType.MARINE : UnitType.MILITIA)
                    .size(SQUAD).at(SQUAD_AT[0], SQUAD_AT[1]).primary("weapon.smg").health(TOUGH);
            // The player's ground click is the marines' order; the defenders' is their commander's, from the first tick.
            if (holder == Faction.DEFENDER) {
                squad.assigned(id -> ObjectiveAssignment.attackMove(id, SQUAD_GOAL[0], SQUAD_GOAL[1]));
            }
            squad.done();
        }
        boolean lance = loop.equals("taken-yard");
        if (lance) {
            builder.squad("lance").faction(Faction.MARINE).size(1).at(MECH_AT[0], MECH_AT[1])
                    .mech(MechVariant.SIROCCO, MechRole.BALANCED).done();
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
        if (loop.equals("enemy-at-the-door")) {
            long[] raiders = world.members("raiders");
            player.at(CLEAR_TICK, s -> {
                for (int i = 0; i < raiders.length; i++) s.world().setCellPos(raiders[i], 100 + i, 10);
            });
        }
        int orderTick = holder == Faction.DEFENDER && !loop.equals("attack-at-the-door") ? 0 : ORDER_TICK;
        Trace trace = new Trace(world, holder, lance, orderTick, loop.equals("attack-at-the-door") ? world.members("attack") : new long[0]);
        int ticks = loop.equals("attack-at-the-door") ? 1500 : 1800;
        try {
            SceneRun.of(world).player(player).observe(trace)
                    .frames(frames, loop, 60, tick -> String.format(Locale.ROOT, "%s / tick %d / %s", loop, tick, trace.state))
                    .run(ticks);
        } finally {
            sim.close();
        }
        return report(loop, ticks, trace);
    }

    private SceneReport report(String loop, int ticks, Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("the-yard-has-a-door-on-each-gateless-face", t.doorsFound == 3 && t.north >= 0,
                t.doorsFound + " doors found; north door " + (t.north >= 0 ? "found" : "missing")));
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
                        "the Sirocco stood in the door at tick " + t.mechThrough));
                verdicts.add(Verdict.of("mech-arrives-within-the-bound", t.mechArrived >= 0
                                && t.mechArrived - ORDER_TICK <= t.mechBound,
                        "arrived " + (t.mechArrived < 0 ? "never" : (t.mechArrived - ORDER_TICK) + " ticks")
                                + " after its order; bound " + t.mechBound));
            }
            default -> verdicts.add(Verdict.of("no-door-while-contested", t.northOpen < 0 && t.otherOpen < 0,
                    "doors opened at ticks " + t.northOpen + ", " + t.otherOpen));
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
        final int squadBound, mechBound;
        String state = "";

        Trace(SceneWorld world, Faction holder, boolean lance, int orderTick, long[] attackers) {
            this.world = world;
            this.orderTick = orderTick;
            this.holder = holder;
            this.lance = lance;
            this.attackers = attackers;
            squadBound = bound(SQUAD_AT, SQUAD_GOAL, (holder == Faction.MARINE ? UnitType.MARINE : UnitType.MILITIA).moveSpeed);
            mechBound = bound(MECH_AT, MECH_GOAL, MechVariant.SIROCCO.moveSpeed);
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
            Squad squad = attackers.length == 0 ? world.squad("holders") : null;
            if (squad != null) {
                for (long member : world.members("holders")) {
                    if (!sim.world().isAlive(member)) continue;
                    if (squadThrough < 0 && inDoor(all, north, sim.world().cellX(member), sim.world().cellY(member))) squadThrough = tick;
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
