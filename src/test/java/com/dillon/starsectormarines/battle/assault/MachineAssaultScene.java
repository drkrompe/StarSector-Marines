package com.dillon.starsectormarines.battle.assault;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.mech.MechMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadBeliefs;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.ui.picking.TacticalOrderIntent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.HeldPlaces;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Does a machine ordered onto a held building assault it by fire, from outside, and stop when it is clear?</b>
 * The acceptance of the machine assault (`ai-nouns.md`, machine assault under fighting positions).
 *
 * <p>One building: a walled 13 by 13 shell with two windows on its west face and a door on its east, published as a
 * held place. Three defenders stand inside it, unarmed and stationary so that whether the machine survives is not
 * the question: one at a window, two deep inside where only a wall stands between them and the west. A scripted
 * observer reports every living defender to the marine squads every second, standing in for the spotter that found
 * the garrison; that is the side's belief the order reads, and nothing else is planted. The control's building is
 * empty, since a machine that sees a man at a window believes in him itself. The machine starts fifty cells
 * west and is given the player's click on the building's interior at tick 30.
 *
 * <p>Loops: {@code mech} (a Warden, whose cannon brings walls down), {@code tank} (a breakthrough tank), {@code
 * control} (the same Warden and click with nobody reported inside: the click is an ordinary move), and {@code
 * with-infantry} (the Warden and a rifle squad given the same click: the squad clears and holds, the machine
 * assaults).
 *
 * <p>Verdicts assert mechanics, never who won: the cursor said what the click did, the click became an assault, the
 * machine never stood in the footprint, it fired on the place, a named wall was hit by it, no heavy round landed on
 * a wall with a friendly at it, and once nobody was believed inside the order ended (conditional on that happening).
 * Kills, wall cells opened and ticks are metrics.
 */
public final class MachineAssaultScene implements BehaviorScene {

    public static final String MECH = "mech";
    public static final String TANK = "tank";
    public static final String CONTROL = "control";
    public static final String WITH_INFANTRY = "with-infantry";

    private static final int WIDTH = 112;
    private static final int HEIGHT = 72;
    private static final int LEFT = 66, TOP = 30, RIGHT = 78, BOTTOM = 42;
    private static final int DOOR_Y = 36;
    private static final int[][] WINDOWS = {{LEFT, 33}, {LEFT, 39}};
    private static final int WALL_HP = 120;
    private static final TacticalNode PLACE = new TacticalNode(TacticalNode.Kind.INNER_POSITION,
            72, 36, LEFT, TOP, RIGHT, BOTTOM, Faction.ALLY, HeldPlaces.PRIORITY, 0);
    private static final int[][] DEFENDERS = {{67, 33}, {72, 36}, {73, 40}};
    private static final int MACHINE_X = 20, MACHINE_Y = 36;
    private static final int CLICK_X = 72, CLICK_Y = 37;
    private static final int CLICK_TICK = 30;
    private static final int REPORT_EVERY_TICKS = 30;
    private static final int TICKS = 2400;
    /** A wall cell losing this much in one tick was hit by a machine's round, not a rifle's. */
    private static final int HEAVY_WALL_HIT = 10;

    private final int frameEvery;

    public MachineAssaultScene() { this(15); }

    public MachineAssaultScene(int frameEvery) { this.frameEvery = frameEvery; }

    @Override public String id() { return "machine-assault"; }

    @Override public String label() {
        return "Machine assault: a mech or tank ordered onto a held building the side believes is enemy-held fires on "
                + "it from a stand-off, never enters, and stops when it is clear; the control clicks an empty one";
    }

    @Override public int frameDelayMillis() { return 90; }

    @Override public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(run(MECH, frames));
        reports.add(run(TANK, frames));
        reports.add(run(CONTROL, frames));
        reports.add(run(WITH_INFANTRY, frames));
        return reports;
    }

    private SceneReport run(String loop, FrameSink frames) {
        boolean tankLoop = loop.equals(TANK);
        boolean reported = !loop.equals(CONTROL);
        boolean infantry = loop.equals(WITH_INFANTRY);

        NavigationGrid field = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology ground = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) field.setWalkableFloor(x, y);
        stampBuilding(field, ground);
        SceneBuilder builder = SceneBuilder.over(field, ground).missionCompletion(false);
        // Bare bodies carry no weapon and, held at zero speed, stay on their posts. The control's building is
        // empty, so nobody, the machine included, can come to believe anybody is inside it.
        int defenders = reported ? DEFENDERS.length : 0;
        for (int i = 0; i < defenders; i++) {
            builder.unit("defender-" + i, Faction.DEFENDER, UnitType.MILITIA, DEFENDERS[i][0], DEFENDERS[i][1],
                    spec -> spec.moveSpeed(0f));
        }
        if (!tankLoop) {
            builder.squad("lance").faction(Faction.MARINE).size(1).at(MACHINE_X + 2, MACHINE_Y)
                    .mech(MechVariant.WARDEN, MechRole.BALANCED)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, MACHINE_X, MACHINE_Y))
                    .done();
        }
        if (infantry) {
            builder.squad("rifles").faction(Faction.MARINE).size(6).primary("weapon.field-rifle")
                    .inFile(MACHINE_X + 4, MACHINE_Y + 12, 1, 0)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, MACHINE_X + 6, MACHINE_Y + 12))
                    .done();
        }
        // The building's own (it never reaches the far defender) keeps the battle going: a holdout in a sealed box.
        builder.wall(100, 60, 108, 60).wall(100, 68, 108, 68).wall(100, 60, 100, 68).wall(108, 60, 108, 68)
                .unit("holdout", Faction.DEFENDER, UnitType.MARINE, 104, 64);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.setTacticalMap(new TacticalMap(List.of(PLACE)));
        long[] garrison = new long[defenders];
        for (int i = 0; i < garrison.length; i++) garrison[i] = world.unit("defender-" + i);

        long machine;
        int observerSquad;
        if (tankLoop) {
            machine = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.MARINE,
                    VehicleMission.deployed(MACHINE_X + .5f, MACHINE_Y + .5f));
            sim.convoy().body(machine).teleport(MACHINE_X + .5f, MACHINE_Y + .5f, 0f);
            observerSquad = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        } else {
            machine = world.members("lance")[0];
            observerSquad = world.squadId("lance");
        }
        int rifles = infantry ? world.squadId("rifles") : -1;

        Map<Long, Integer> wallHp = new LinkedHashMap<>();
        for (int y = TOP; y <= BOTTOM; y++) {
            for (int x = LEFT; x <= RIGHT; x++) {
                if (!field.isWalkable(x, y)) wallHp.put(pack(x, y), field.getWallHp(x, y));
            }
        }

        TacticalOrderIntent intent = TacticalOrderIntent.NONE;
        TacticalOrderIntent infantryIntent = TacticalOrderIntent.NONE;
        boolean acceptedAssault = false, acceptedMove = false;
        int minGapWhileOrdered = Integer.MAX_VALUE, ticksInside = 0;
        int placeShots = 0, wallNamedTicks = 0, heavyWallHits = 0, dangerCloseHeavyHits = 0, firstFireTick = -1;
        int allDeadTick = -1, releaseTick = -1, standoffX = -1, standoffY = -1;
        NavigationGrid grid = sim.getGrid();
        for (int tick = 0; tick < TICKS; tick++) {
            if (reported && tick % REPORT_EVERY_TICKS == 0) {
                report(sim, observerSquad, garrison);
                if (infantry) report(sim, rifles, garrison);
            }
            if (tick == CLICK_TICK) {
                Selection selection = new Selection();
                if (tankLoop) selection.selectVehicle(machine);
                else selection.selectUnit(world.squadId("lance"), machine);
                intent = TacticalOrderIntent.resolve(sim, selection, CLICK_X, CLICK_Y);
                if (tankLoop) sim.getVehicleMoveOrderService().requestMove(machine, CLICK_X, CLICK_Y);
                else sim.getMechMoveOrderService().requestMove(machine, CLICK_X, CLICK_Y);
                if (infantry) {
                    Selection squad = new Selection();
                    squad.selectSquad(rifles);
                    infantryIntent = TacticalOrderIntent.resolve(sim, squad, CLICK_X, CLICK_Y);
                    sim.getSquadMoveOrderService().requestMove(rifles, CLICK_X, CLICK_Y);
                }
            }
            sim.advance(BattleSimulation.TICK_DT);

            MachineAssault assault = assault(sim, machine, tankLoop);
            if (tick == CLICK_TICK) {
                acceptedAssault = assault != null;
                acceptedMove = !tankLoop && sim.getMechMoveOrderService().activeOrder(machine) != null
                        && assault == null;
                if (assault != null) {
                    standoffX = assault.standoffX();
                    standoffY = assault.standoffY();
                }
            }
            float mx = tankLoop ? sim.convoy().body(machine).x : sim.world().x(machine);
            float my = tankLoop ? sim.convoy().body(machine).y : sim.world().y(machine);
            int gap = AssaultTarget.place(PLACE).gap((int) Math.floor(mx), (int) Math.floor(my));
            if (gap == 0) ticksInside++;
            if (assault != null) {
                minGapWhileOrdered = Math.min(minGapWhileOrdered, gap);
                if (assault.hasWall()) wallNamedTicks++;
            } else if (tick > CLICK_TICK && releaseTick < 0 && acceptedAssault) {
                releaseTick = tick;
            }
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId != machine) continue;
                if (AssaultTarget.place(PLACE).gap((int) Math.floor(shot.toX), (int) Math.floor(shot.toY)) <= 1) {
                    placeShots++;
                    if (firstFireTick < 0) firstFireTick = tick;
                }
            }
            for (Map.Entry<Long, Integer> entry : wallHp.entrySet()) {
                int x = (int) (long) entry.getKey(), y = (int) (entry.getKey() >>> 32);
                int now = grid.isWalkable(x, y) ? 0 : grid.getWallHp(x, y);
                if (entry.getValue() - now >= HEAVY_WALL_HIT) {
                    heavyWallHits++;
                    if (friendlyWithin(sim, x, y, MachineAssaults.DANGER_CLOSE_CELLS)) dangerCloseHeavyHits++;
                }
                entry.setValue(now);
            }
            if (allDeadTick < 0 && garrison.length > 0 && allDead(sim, garrison)) allDeadTick = tick;
            if (tick % frameEvery == 0) {
                frames.frame(loop, sim, tick, loop + " / tick " + tick + (assault != null ? " / assaulting" : ""));
            }
        }

        int opened = 0;
        for (long key : wallHp.keySet()) {
            if (grid.isWalkable((int) key, (int) (key >>> 32))) opened++;
        }
        int killed = 0;
        for (long defender : garrison) if (!sim.world().isAlive(defender)) killed++;

        List<Verdict> verdicts = new ArrayList<>();
        if (reported) {
            verdicts.add(Verdict.of("cursor-assault", intent == TacticalOrderIntent.ASSAULT && intent.assault(),
                    "the cursor over the building read " + intent + " with the garrison reported inside"));
            verdicts.add(Verdict.of("order-is-assault", acceptedAssault,
                    "the click was accepted as an assault from stand-off " + standoffX + "," + standoffY));
            verdicts.add(Verdict.of("stands-off", acceptedAssault
                            && minGapWhileOrdered >= MachineAssaults.STANDOFF_MARGIN_CELLS - 1 && ticksInside == 0,
                    "while the order stood the machine came no nearer the footprint than " + minGapWhileOrdered
                            + " cells, and stood inside it on " + ticksInside + " ticks"));
            verdicts.add(Verdict.of("fires-on-place", placeShots > 0,
                    placeShots + " of its rounds ended on the building, the first at tick " + firstFireTick));
            if (wallNamedTicks > 0) {
                verdicts.add(Verdict.of("wall-fire", heavyWallHits > 0,
                        "a sheltering wall was named on " + wallNamedTicks + " ticks and took " + heavyWallHits
                                + " heavy hits"));
            }
            verdicts.add(Verdict.of("no-danger-close-wall-fire", dangerCloseHeavyHits == 0,
                    dangerCloseHeavyHits + " heavy wall hits landed with a marine within "
                            + MachineAssaults.DANGER_CLOSE_CELLS + " cells"));
            if (allDeadTick >= 0 && allDeadTick + MachineAssaults.RELEASE_TICKS + 30 < TICKS) {
                verdicts.add(Verdict.of("released-when-clear", releaseTick >= 0
                                && releaseTick <= allDeadTick + MachineAssaults.RELEASE_TICKS + 30,
                        "the last defender fell at tick " + allDeadTick + "; the order ended at tick " + releaseTick));
            }
            if (infantry) {
                verdicts.add(Verdict.of("infantry-clears", infantryIntent == TacticalOrderIntent.CLEAR,
                        "the rifle squad's cursor read " + infantryIntent + ": infantry still clears and holds"));
            }
        } else {
            verdicts.add(Verdict.of("cursor-move", intent == TacticalOrderIntent.MOVE,
                    "with nobody reported inside the cursor read " + intent));
            verdicts.add(Verdict.of("order-is-move", acceptedMove,
                    "the click was accepted as an ordinary move, not an assault"));
        }

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("standoffX", standoffX);
        metrics.put("standoffY", standoffY);
        metrics.put("minGapWhileOrdered", minGapWhileOrdered == Integer.MAX_VALUE ? -1 : minGapWhileOrdered);
        metrics.put("ticksInside", ticksInside);
        metrics.put("placeShots", placeShots);
        metrics.put("firstFireTick", firstFireTick);
        metrics.put("wallNamedTicks", wallNamedTicks);
        metrics.put("heavyWallHits", heavyWallHits);
        metrics.put("wallCellsOpened", opened);
        metrics.put("defendersKilled", killed);
        metrics.put("allDeadTick", allDeadTick);
        metrics.put("releaseTick", releaseTick);
        sim.close();
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    /** The shell: walls round the footprint, two windows on the west face, a door on the east. */
    private static void stampBuilding(NavigationGrid grid, CellTopology topology) {
        for (int y = TOP; y <= BOTTOM; y++) {
            for (int x = LEFT; x <= RIGHT; x++) {
                if (x != LEFT && x != RIGHT && y != TOP && y != BOTTOM) continue;
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, WALL_HP);
                topology.setTag(x, y, CellTopology.Tag.WALL, true);
            }
        }
        for (int[] window : WINDOWS) grid.setTag(window[0], window[1], NavigationGrid.CellTag.SEE_THROUGH, true);
        grid.setWalkableFloor(RIGHT, DOOR_Y);
        grid.setDoorway(RIGHT, DOOR_Y, true);
        topology.setTag(RIGHT, DOOR_Y, CellTopology.Tag.WALL, false);
    }

    /** The scripted observer: every living defender reported where he stands. */
    private static void report(BattleSimulation sim, int squadId, long[] garrison) {
        for (long defender : garrison) {
            if (!sim.world().isAlive(defender)) continue;
            SquadBeliefs.sighted(sim.getSquad(squadId), defender, sim.world().cellX(defender),
                    sim.world().cellY(defender), sim.getSimTickIndex());
        }
    }

    private static MachineAssault assault(BattleSimulation sim, long machine, boolean vehicle) {
        if (vehicle) return sim.getVehicleMoveOrderService().assault(machine);
        ActiveOrder order = sim.getMechMoveOrderService().activeOrder(machine);
        return order == null ? null : order.assault();
    }

    private static boolean friendlyWithin(BattleSimulation sim, int x, int y, float radius) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || sim.world().hasMechLoadout(unit)) continue;
            if (Math.hypot(sim.world().x(unit) - (x + .5f), sim.world().y(unit) - (y + .5f)) <= radius) return true;
        }
        return false;
    }

    private static boolean allDead(BattleSimulation sim, long[] garrison) {
        for (long defender : garrison) if (sim.world().isAlive(defender)) return false;
        return true;
    }

    private static long pack(int x, int y) {
        return ((long) y << 32) | (x & 0xffffffffL);
    }
}
