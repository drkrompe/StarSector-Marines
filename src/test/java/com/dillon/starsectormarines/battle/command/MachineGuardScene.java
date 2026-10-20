package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.assault.AssaultTarget;
import com.dillon.starsectormarines.battle.assault.MachineGuard;
import com.dillon.starsectormarines.battle.assault.MachineGuards;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.mech.MechLocomotion;
import com.dillon.starsectormarines.battle.mech.MechMoveOrderService.ActiveOrder;
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
import com.dillon.starsectormarines.battle.squad.SquadBeliefs;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.TurretAim;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.ui.picking.TacticalOrderIntent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.HeldPlaces;
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
 * <b>Does a machine sent to its own side's garrison stand beside it as heavy support, and does a commander send one
 * only within its bound?</b> The acceptance of the machine guard (`garrisonables.md`, machine guard; `MachineGuards`,
 * {@link MachineGuardDirector}).
 *
 * <p>Player loops play on one building: a walled 13 by 13 shell with two windows on its east face and a door on its
 * west, published as a held place, manned by four stationary riflemen. A militia squad attack-moves on it from the far
 * east; a scripted observer reports it to the garrison every second, standing in for the lookout. The machine starts
 * west of the building and is given the player's click on its interior at tick 30. At {@link #LEAVE_TICK} the garrison
 * is lifted out to the far west corner, so the place is no longer manned.
 * <ul>
 *   <li>{@code mech}: a Sirocco lance of one.</li>
 *   <li>{@code tank}: a breakthrough tank.</li>
 *   <li>{@code control}: the Sirocco and the same click on the building with nobody in it: an ordinary move.</li>
 * </ul>
 *
 * <p>{@code defender-lances}: the reactive-belt production fort, the defender's Conquest commander with four patrols and
 * two mech lances in its pool, and a tough raiding party put beside the gate stretch at tick 30. The commander mans the
 * stretch; the loop asserts that it attaches no more lances at once than its bound, that any guard it gives stands
 * outside the run it guards and never on it, and that a guard is attached once a run is manned under pressure (when a
 * free lance can reach it).
 *
 * <p>Verdicts assert mechanics, never who won: the cursor, the order, the cell outside and off the garrison's lanes,
 * never inside, facing the threat while nothing is in its sights, firing at the approach, and the stand-down once the
 * garrison has gone (conditional on that happening in the loop). Times and shot counts are metrics.
 */
public final class MachineGuardScene implements BehaviorScene {

    public static final String MECH = "mech";
    public static final String TANK = "tank";
    public static final String CONTROL = "control";
    public static final String DEFENDER_LANCES = "defender-lances";

    private static final int WIDTH = 170;
    private static final int HEIGHT = 72;
    private static final int LEFT = 66, TOP = 30, RIGHT = 78, BOTTOM = 42;
    private static final int DOOR_Y = 36;
    private static final int[][] WINDOWS = {{RIGHT, 33}, {RIGHT, 39}};
    private static final TacticalNode PLACE = new TacticalNode(TacticalNode.Kind.INNER_POSITION,
            72, 36, LEFT, TOP, RIGHT, BOTTOM, Faction.ALLY, HeldPlaces.PRIORITY, 0);
    private static final int MACHINE_X = 40, MACHINE_Y = 36;
    private static final int CLICK_X = 72, CLICK_Y = 37;
    private static final int CLICK_TICK = 30;
    private static final int REPORT_EVERY_TICKS = 30;
    private static final int LEAVE_TICK = 2100;
    private static final int TICKS = 2700;
    /** Ticks after arriving before facing is judged: long enough for a hull to turn, a gun to come round. */
    private static final int SETTLE_TICKS = 150;
    /** How far off the threat axis an idle machine may face and still count as facing it. */
    private static final float FACING_TOLERANCE_DEG = 30f;

    private static final int FORT_WIDTH = 200;
    private static final int FORT_HEIGHT = 160;
    private static final int PATROLS = 6;
    private static final int LANCES = 2;
    private static final float TOUGH = 1_000_000f;
    private static final int FORT_TICKS = 3600;
    private static final int STANDOFF_CELLS = 6;

    private final int frameEvery;

    public MachineGuardScene() { this(15); }

    public MachineGuardScene(int frameEvery) { this.frameEvery = frameEvery; }

    @Override public String id() { return "machine-guard"; }

    @Override public String label() {
        return "Machine guard: a mech or tank sent to its own side's garrison stands beside it off its lanes, faces the "
                + "threat, fires at the approach and stands down when the garrison leaves; a commander attaches lances "
                + "within its bound";
    }

    @Override public int frameDelayMillis() { return 90; }

    @Override public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(playerLoop(MECH, frames));
        reports.add(playerLoop(TANK, frames));
        reports.add(playerLoop(CONTROL, frames));
        reports.add(defenderLoop(frames));
        return reports;
    }

    // ----- the player's guard ---------------------------------------------------------------

    private SceneReport playerLoop(String loop, FrameSink frames) {
        boolean tankLoop = loop.equals(TANK);
        boolean manned = !loop.equals(CONTROL);

        NavigationGrid field = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology ground = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) field.setWalkableFloor(x, y);
        stampBuilding(field, ground);
        SceneBuilder builder = SceneBuilder.over(field, ground).missionCompletion(false);
        if (manned) {
            builder.squad("garrison").faction(Faction.MARINE).size(4).primary("weapon.field-rifle")
                    .inFile(76, 33, 0, 2).stationary()
                    .assigned(id -> ObjectiveAssignment.defendArea(id, 76, 36)).done();
        }
        builder.squad("attackers").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(6).primary("weapon.field-rifle")
                .inFile(WIDTH - 12, 33, 0, 1)
                .assigned(id -> ObjectiveAssignment.attackMove(id, CLICK_X, CLICK_Y)).done();
        if (!tankLoop) {
            builder.squad("lance").faction(Faction.MARINE).size(1).at(MACHINE_X, MACHINE_Y)
                    .mech(MechVariant.SIROCCO, MechRole.BALANCED)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, MACHINE_X, MACHINE_Y)).done();
        }
        // Keeps the battle going whatever happens at the building: a holdout in a sealed box.
        builder.wall(150, 60, 158, 60).wall(150, 68, 158, 68).wall(150, 60, 150, 68).wall(158, 60, 158, 68)
                .unit("holdout", Faction.DEFENDER, UnitType.MARINE, 154, 64);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.setTacticalMap(new TacticalMap(List.of(PLACE)));
        long[] garrison = manned ? world.members("garrison") : new long[0];
        int garrisonSquad = manned ? world.squadId("garrison") : sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long[] attackers = world.members("attackers");

        long machine;
        if (tankLoop) {
            machine = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.MARINE,
                    VehicleMission.deployed(MACHINE_X + .5f, MACHINE_Y + .5f));
            sim.convoy().body(machine).teleport(MACHINE_X + .5f, MACHINE_Y + .5f, 0f);
        } else {
            machine = world.members("lance")[0];
        }
        AssaultTarget place = AssaultTarget.place(PLACE);

        PlayerTrace trace = new PlayerTrace(tankLoop, machine, place);
        ScriptedPlayer player = ScriptedPlayer.on(world);
        player.at(CLICK_TICK, s -> {
            Selection selection = new Selection();
            if (tankLoop) selection.selectVehicle(machine);
            else selection.selectUnit(world.squadId("lance"), machine);
            trace.intent = TacticalOrderIntent.resolve(s, selection, CLICK_X, CLICK_Y);
            if (tankLoop) s.getVehicleMoveOrderService().requestMove(machine, CLICK_X, CLICK_Y);
            else s.getMechMoveOrderService().requestMove(machine, CLICK_X, CLICK_Y);
        });
        if (manned) {
            player.at(LEAVE_TICK, s -> {
                for (int i = 0; i < garrison.length; i++) {
                    if (s.world().isAlive(garrison[i])) s.world().setCellPos(garrison[i], 4 + 2 * i, HEIGHT - 4);
                }
                trace.leftTick = LEAVE_TICK;
            });
        }
        TickObserver observer = (s, tick) -> {
            if (tick % REPORT_EVERY_TICKS == 0) {
                for (long attacker : attackers) {
                    if (!s.world().isAlive(attacker)) continue;
                    SquadBeliefs.sighted(s.getSquad(garrisonSquad), attacker, s.world().cellX(attacker),
                            s.world().cellY(attacker), s.getSimTickIndex());
                }
            }
            trace.observe(s, tick, garrison, attackers);
        };
        SceneRun.of(world).player(player).observe(observer)
                .frames(frames, loop, frameEvery, tick -> loop + " / tick " + tick + " / " + trace.caption())
                .run(TICKS);

        List<Verdict> verdicts = new ArrayList<>();
        if (manned) {
            verdicts.add(Verdict.of("cursor-guard", trace.intent == TacticalOrderIntent.GUARD && trace.intent.guard()
                            && !trace.intent.assault(),
                    "the cursor over the manned building read " + trace.intent));
            verdicts.add(Verdict.of("order-is-guard", trace.acceptedGuard,
                    "the click was accepted as a guard at " + trace.guardX + "," + trace.guardY));
            verdicts.add(Verdict.of("guard-cell-outside", trace.acceptedGuard
                            && trace.guardGap >= MachineGuards.GUARD_NEAR_CELLS && trace.guardGap <= MachineGuards.GUARD_FAR_CELLS,
                    "the guard cell stands " + trace.guardGap + " cells outside the footprint"));
            verdicts.add(Verdict.of("off-the-garrisons-lanes", trace.acceptedGuard && !trace.guardInLane,
                    "the guard cell is " + (trace.guardInLane ? "" : "not ") + "in front of the garrison along the axis "
                            + String.format(Locale.ROOT, "(%.2f, %.2f)", trace.axisX, trace.axisY)));
            verdicts.add(Verdict.of("never-enters", trace.acceptedGuard && trace.ticksInside == 0
                            && trace.minGapWhileGuarding >= 1,
                    "while the guard stood the machine came no nearer the footprint than " + trace.minGapWhileGuarding
                            + " cells, and stood inside it on " + trace.ticksInside + " ticks"));
            if (trace.idleSamples > 0) {
                verdicts.add(Verdict.of("faces-the-threat", trace.facingSamples * 10 >= trace.idleSamples * 9,
                        trace.facingSamples + " of " + trace.idleSamples + " settled idle ticks faced within "
                                + FACING_TOLERANCE_DEG + " degrees of the threat axis"));
            }
            if (trace.attackersSeenTick >= 0) {
                verdicts.add(Verdict.of("engages-the-approach", trace.approachShots > 0,
                        trace.approachShots + " of its rounds went at attackers outside the building, the first at tick "
                                + trace.firstApproachShotTick));
            }
            int goneTick = trace.goneTick;
            if (goneTick >= 0 && goneTick + MachineGuards.RELEASE_TICKS + 30 < TICKS) {
                verdicts.add(Verdict.of("stands-down-when-the-garrison-leaves", trace.releaseTick >= goneTick
                                && trace.releaseTick <= goneTick + MachineGuards.RELEASE_TICKS + 30,
                        "the garrison was gone from tick " + goneTick + "; the guard ended at tick " + trace.releaseTick));
            }
            verdicts.add(Verdict.of("holds-while-manned", trace.endedWhileManned < 0,
                    "the guard ended with the garrison still in place at tick " + trace.endedWhileManned));
        } else {
            verdicts.add(Verdict.of("cursor-move", trace.intent == TacticalOrderIntent.MOVE,
                    "with nobody in the building the cursor read " + trace.intent));
            verdicts.add(Verdict.of("order-is-move", !trace.acceptedGuard && trace.acceptedMove,
                    "the click was accepted as an ordinary move, not a guard"));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("guardX", trace.guardX);
        metrics.put("guardY", trace.guardY);
        metrics.put("guardGap", trace.guardGap);
        metrics.put("arrivedTick", trace.arrivedTick);
        metrics.put("minGapWhileGuarding", trace.minGapWhileGuarding == Integer.MAX_VALUE ? -1 : trace.minGapWhileGuarding);
        metrics.put("idleSamples", trace.idleSamples);
        metrics.put("facingSamples", trace.facingSamples);
        metrics.put("approachShots", trace.approachShots);
        metrics.put("firstApproachShotTick", trace.firstApproachShotTick);
        metrics.put("goneTick", trace.goneTick);
        metrics.put("releaseTick", trace.releaseTick);
        int killed = 0;
        for (long attacker : attackers) if (!sim.world().isAlive(attacker)) killed++;
        metrics.put("attackersKilled", killed);
        sim.close();
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    /** What the machine did under the player's order, read off the simulation every tick. */
    private static final class PlayerTrace {
        final boolean tank;
        final long machine;
        final AssaultTarget place;
        TacticalOrderIntent intent = TacticalOrderIntent.NONE;
        boolean acceptedGuard, acceptedMove, guardInLane;
        int guardX = -1, guardY = -1, guardGap = -1;
        float axisX, axisY;
        int ticksInside, minGapWhileGuarding = Integer.MAX_VALUE;
        int arrivedTick = -1, idleSamples, facingSamples;
        int attackersSeenTick = -1, approachShots, firstApproachShotTick = -1;
        int leftTick = -1, goneTick = -1, releaseTick = -1, endedWhileManned = -1;
        boolean guarding;

        PlayerTrace(boolean tank, long machine, AssaultTarget place) {
            this.tank = tank;
            this.machine = machine;
            this.place = place;
        }

        String caption() {
            return guarding ? "guarding" : acceptedGuard ? "stood down" : "";
        }

        void observe(BattleSimulation sim, int tick, long[] garrison, long[] attackers) {
            MachineGuard guard = guard(sim);
            boolean holding;
            if (tank) {
                holding = guard != null && sim.getVehicleMoveOrderService().activeOrder(machine) == null;
            } else {
                ActiveOrder order = sim.getMechMoveOrderService().activeOrder(machine);
                holding = guard != null && sim.movement().atCell(machine, order.destinationX(), order.destinationY());
                if (guard == null && order != null) acceptedMove = true;
            }
            if (guard != null && !acceptedGuard) {
                acceptedGuard = true;
                guardX = guard.guardX();
                guardY = guard.guardY();
                guardGap = place.gap(guardX, guardY);
                axisX = guard.axisX();
                axisY = guard.axisY();
                float rx = guardX + .5f - MachineGuards.centreX(place), ry = guardY + .5f - MachineGuards.centreY(place);
                float halfW = (place.right - place.left + 1) / 2f, halfH = (place.bottom - place.top + 1) / 2f;
                float halfWidth = Math.abs(axisY) * halfW + Math.abs(axisX) * halfH;
                guardInLane = MachineGuards.inLane(rx * axisX + ry * axisY, Math.abs(rx * axisY - ry * axisX), halfWidth);
            }
            float mx = tank ? sim.convoy().body(machine).x : sim.world().x(machine);
            float my = tank ? sim.convoy().body(machine).y : sim.world().y(machine);
            int gap = place.gap((int) Math.floor(mx), (int) Math.floor(my));
            if (guard != null) {
                minGapWhileGuarding = Math.min(minGapWhileGuarding, gap);
                if (gap == 0) ticksInside++;
            }
            if (holding && arrivedTick < 0) arrivedTick = tick;
            if (holding && arrivedTick >= 0 && tick - arrivedTick >= SETTLE_TICKS) {
                float want = tank ? TurretAim.bearingTo(0f, 0f, guard.axisX(), guard.axisY())
                        : MechLocomotion.continuousFacing(guard.axisX(), guard.axisY());
                Float have = null;
                if (tank) {
                    GroundTurret turret = sim.convoy().turret(machine);
                    if (turret != null && turret.targetId == 0L) have = turret.facingDeg;
                } else if (sim.world().targetId(machine) == 0L) {
                    have = sim.world().mechHipFacingDegrees(machine);
                }
                if (have != null) {
                    idleSamples++;
                    if (Math.abs(MechLocomotion.deltaDegrees(have, want)) <= FACING_TOLERANCE_DEG) facingSamples++;
                }
            }
            if (attackersSeenTick < 0) {
                for (long attacker : attackers) {
                    if (sim.world().isAlive(attacker) && place.gap(sim.world().cellX(attacker), sim.world().cellY(attacker)) <= 40) {
                        attackersSeenTick = tick;
                        break;
                    }
                }
            }
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId != machine) continue;
                if (place.gap((int) Math.floor(shot.toX), (int) Math.floor(shot.toY)) > 1) {
                    approachShots++;
                    if (firstApproachShotTick < 0) firstApproachShotTick = tick;
                }
            }
            boolean inside = false;
            for (long member : garrison) {
                if (sim.world().isAlive(member) && place.gap(sim.world().cellX(member), sim.world().cellY(member)) == 0) {
                    inside = true;
                }
            }
            if (acceptedGuard && !inside && goneTick < 0) goneTick = tick;
            if (guarding && guard == null) {
                if (releaseTick < 0) releaseTick = tick;
                if (inside && endedWhileManned < 0) endedWhileManned = tick;
            }
            guarding = guard != null;
        }

        private MachineGuard guard(BattleSimulation sim) {
            if (tank) return sim.getVehicleMoveOrderService().guard(machine);
            ActiveOrder order = sim.getMechMoveOrderService().activeOrder(machine);
            return order == null ? null : order.guard();
        }
    }

    /** The shell: walls round the footprint, two windows on the east face, a door on the west. */
    private static void stampBuilding(NavigationGrid grid, CellTopology topology) {
        for (int y = TOP; y <= BOTTOM; y++) {
            for (int x = LEFT; x <= RIGHT; x++) {
                if (x != LEFT && x != RIGHT && y != TOP && y != BOTTOM) continue;
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, 120);
                topology.setTag(x, y, CellTopology.Tag.WALL, true);
            }
        }
        for (int[] window : WINDOWS) grid.setTag(window[0], window[1], NavigationGrid.CellTag.SEE_THROUGH, true);
        grid.setWalkableFloor(LEFT, DOOR_Y);
        grid.setDoorway(LEFT, DOOR_Y, true);
        topology.setTag(LEFT, DOOR_Y, CellTopology.Tag.WALL, false);
    }

    // ----- the commander's guard ------------------------------------------------------------

    private record Terrain(NavigationGrid grid, CellTopology topology, List<DefensePost> posts, List<Doodad> doodads,
                           int[] centre) {}

    /** The reactive-belt stamp: open ground with one production battery fort and its belt. */
    private static Terrain stampFort() {
        NavigationGrid grid = new NavigationGrid(FORT_WIDTH, FORT_HEIGHT);
        CellTopology topology = new CellTopology(FORT_WIDTH, FORT_HEIGHT);
        for (int y = 0; y < FORT_HEIGHT; y++) {
            for (int x = 0; x < FORT_WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L), FORT_WIDTH, FORT_HEIGHT, 7L);
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

    private SceneReport defenderLoop(FrameSink frames) throws Exception {
        Terrain terrain = stampFort();
        Terrain probe = stampFort();
        BattleSimulation probeSim = SceneBuilder.over(probe.grid(), probe.topology()).posts(probe.posts())
                .doodads(probe.doodads()).missionCompletion(false).build().sim();
        int[] centre = probe.centre();
        GarrisonableRegistry probePlaces = probeSim.garrisonables();
        int fortId = probeSim.forts().forts().get(0).id();
        // The gate stretch: of the fort's run nearest the field's south edge, the stretch at the run's middle. Runs are
        // measured whole so that where the raiders stand does not depend on how runs are cut.
        GarrisonableRegistry whole = GarrisonableRegistry.build(probeSim.fightingPositions(), FORT_WIDTH, FORT_HEIGHT,
                probePlaces.blockSize(), new int[0], false);
        int gateRun = -1;
        for (int g = 0; g < probePlaces.count(); g++) {
            int r = probePlaces.runOf(g);
            if (probePlaces.fortId(g) != fortId || whole.size(r) < 6) continue;
            if (gateRun < 0 || whole.centreY(r) > whole.centreY(gateRun)
                    || (whole.centreY(r) == whole.centreY(gateRun) && whole.centreX(r) < whole.centreX(gateRun))) gateRun = r;
        }
        int runA = probePlaces.idAt(whole.centreX(gateRun), whole.centreY(gateRun));
        int[] halt = outside(probeSim, whole.centreX(gateRun), whole.centreY(gateRun), centre[0], centre[1]);
        int[] fieldCell = walkable(probeSim, centre[0] - 70, FORT_HEIGHT - 6);

        SceneBuilder builder = SceneBuilder.over(terrain.grid(), terrain.topology()).posts(terrain.posts())
                .doodads(terrain.doodads()).missionCompletion(false);
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 3, FORT_HEIGHT - 3, spec -> spec.moveSpeed(0f).health(TOUGH));
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, FORT_WIDTH - 3, 3, spec -> spec.moveSpeed(0f).health(TOUGH));
        for (int i = 0; i < PATROLS; i++) {
            int[] cell = walkable(probeSim, centre[0] - 6 + 3 * i, centre[1]);
            builder.squad("patrol-" + i).faction(Faction.DEFENDER).type(UnitType.MILITIA).size(6)
                    .at(cell[0], cell[1]).primary("weapon.smg").health(TOUGH).done();
        }
        for (int i = 0; i < LANCES; i++) {
            // Outside the works on the open ground north-west of the fort: free to walk to the belt, and out of sight of
            // the raiders at the south gate, so the lances are idle in the pool rather than already in contact.
            int[] cell = walkable(probeSim, centre[0] - 45 + 8 * i, centre[1] - 30);
            builder.squad("lance-" + i).faction(Faction.DEFENDER).size(1).at(cell[0], cell[1])
                    .mech(MechVariant.SIROCCO, MechRole.BALANCED).done();
        }
        builder.squad("raiders").faction(Faction.MARINE).size(6).at(fieldCell[0], fieldCell[1])
                .primary("weapon.smg").health(TOUGH).stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261020L);
        ConquestDefenderCommand defenders = new ConquestDefenderCommand(
                new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, FORT_WIDTH, FORT_HEIGHT));
        for (int i = 0; i < PATROLS; i++) {
            sim.claimSquadCommand(world.squadId("patrol-" + i), CommandAuthority.MISSION_COMMAND,
                    defenders.strategyId(), "scene patrol pool");
        }
        int[] lanceIds = new int[LANCES];
        for (int i = 0; i < LANCES; i++) {
            lanceIds[i] = world.squadId("lance-" + i);
            sim.claimSquadCommand(lanceIds[i], CommandAuthority.MISSION_COMMAND, defenders.strategyId(), "scene lance pool");
        }
        sim.setAutonomousCommander(Faction.DEFENDER, defenders, ConquestCommandDisclosure.INSTANCE);
        long[] raiders = world.members("raiders");

        DefenderTrace trace = new DefenderTrace(world, lanceIds, defenders, runA);
        ScriptedPlayer player = ScriptedPlayer.on(world);
        player.at(CLICK_TICK, s -> {
            for (int i = 0; i < raiders.length; i++) {
                int[] cell = walkable(s, halt[0] + (i % 3) - 1, halt[1] + (i / 3));
                s.world().setCellPos(raiders[i], cell[0], cell[1]);
            }
        });
        SceneRun.of(world).player(player).observe(trace)
                .frames(frames, DEFENDER_LANCES, 60, tick -> DEFENDER_LANCES + " / tick " + tick + " / " + trace.caption())
                .run(FORT_TICKS);

        int cap = MachineGuardDirector.cap(LANCES);
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("within-the-bound", trace.maxGuarding <= cap && trace.maxDirectorGuards <= cap,
                "most lances on guard at once " + trace.maxGuarding + " (the director counted " + trace.maxDirectorGuards
                        + "), bound " + cap + " of " + LANCES));
        if (trace.mannedPressedTick >= 0) {
            verdicts.add(Verdict.of("guard-attached-to-a-pressed-garrison", trace.firstGuardTick >= 0,
                    "a run was manned (the commander mans only under pressure) from tick " + trace.mannedPressedTick
                            + "; the first guard stood at tick " + trace.firstGuardTick));
        }
        if (trace.firstGuardTick >= 0) {
            verdicts.add(Verdict.of("guard-outside-its-run", trace.guardCellsInside == 0
                            && trace.minGuardCellGap >= MachineGuards.GUARD_NEAR_CELLS,
                    "nearest guard cell " + trace.minGuardCellGap + " cells from its run's footprint; "
                            + trace.guardCellsInside + " guard cells on the run"));
            verdicts.add(Verdict.of("never-stands-on-the-run", trace.ticksOnRun == 0,
                    "guarding chassis stood guard on the guarded run on " + trace.ticksOnRun + " ticks (crossed it on "
                            + trace.ticksCrossing + " ticks of the walk)"));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("mannedPressedTick", trace.mannedPressedTick);
        metrics.put("firstGuardTick", trace.firstGuardTick);
        metrics.put("maxGuarding", trace.maxGuarding);
        metrics.put("guardTicks", trace.guardTicks);
        metrics.put("garrisonTicks", trace.garrisonTicks);
        metrics.put("ticksCrossingRun", trace.ticksCrossing);
        metrics.put("peakPressure", Math.round(trace.peakPressure * 100f) / 100f);
        metrics.put("minGuardCellGap", trace.minGuardCellGap == Integer.MAX_VALUE ? -1 : trace.minGuardCellGap);
        sim.close();
        probeSim.close();
        return new SceneReport(id(), DEFENDER_LANCES, FORT_TICKS, verdicts, metrics);
    }

    /** The defender's lances and garrisons, read off the simulation every tick. */
    private static final class DefenderTrace implements TickObserver {
        final SceneWorld world;
        final int[] lances;
        final ConquestDefenderCommand command;
        int garrisonTicks;
        int mannedPressedTick = -1, firstGuardTick = -1, maxGuarding, maxDirectorGuards, guardTicks;
        int guardCellsInside, minGuardCellGap = Integer.MAX_VALUE, ticksOnRun, ticksCrossing;
        private String state = "";

        final int run;
        float peakPressure;

        DefenderTrace(SceneWorld world, int[] lances, ConquestDefenderCommand command, int run) {
            this.run = run;
            this.world = world;
            this.lances = lances;
            this.command = command;
        }

        String caption() { return state; }

        @Override public void observe(BattleSimulation sim, int tick) {
            GarrisonableRegistry places = sim.garrisonables();
            var picture = sim.peekCommanderInfluence(Faction.DEFENDER);
            if (picture != null && run >= 0 && run < places.count()) {
                int bw = places.blockWidth();
                for (int block : places.coveredBlocks(run)) {
                    peakPressure = Math.max(peakPressure, picture.hostileAt(block % bw, block / bw));
                }
            }
            boolean manning = false;
            for (var squad : sim.getSquads()) {
                if (squad.faction != Faction.DEFENDER || squad.isMechSquad() || squad.aliveMembers <= 0) continue;
                ObjectiveAssignment order = squad.assignedObjective;
                if (order != null && order.isGarrison()) manning = true;
            }
            if (manning) {
                garrisonTicks++;
                if (mannedPressedTick < 0) mannedPressedTick = tick;
            }
            int guarding = 0;
            for (int lance : lances) {
                boolean any = false;
                for (int i = 0, n = sim.squadMemberCount(lance); i < n; i++) {
                    long mech = sim.squadMemberAt(lance, i);
                    ActiveOrder order = sim.getMechMoveOrderService().activeOrder(mech);
                    if (order == null || order.guard() == null || !order.commanded()) continue;
                    any = true;
                    AssaultTarget target = order.guard().target();
                    int cellGap = target.gap(order.destinationX(), order.destinationY());
                    minGuardCellGap = Math.min(minGuardCellGap, cellGap);
                    if (target.contains(order.destinationX(), order.destinationY())) guardCellsInside++;
                    if (target.contains(sim.world().cellX(mech), sim.world().cellY(mech))) {
                        // Crossing a belt on the walk is walking; standing guard on it would be manning it.
                        if (sim.movement().atCell(mech, order.destinationX(), order.destinationY())) ticksOnRun++;
                        else ticksCrossing++;
                    }
                }
                if (any) guarding++;
            }
            if (guarding > 0) {
                guardTicks++;
                if (firstGuardTick < 0) firstGuardTick = tick;
            }
            maxGuarding = Math.max(maxGuarding, guarding);
            maxDirectorGuards = Math.max(maxDirectorGuards, command.machineGuardCount());
            state = "lances on guard " + guarding;
        }
    }

    private static int[] outside(BattleSimulation sim, int cx, int cy, float fx, float fy) {
        double runOut = Math.hypot(cx - fx, cy - fy);
        int[] best = null;
        long bestGap = Long.MAX_VALUE;
        for (int dy = -STANDOFF_CELLS * 3; dy <= STANDOFF_CELLS * 3; dy++) {
            for (int dx = -STANDOFF_CELLS * 3; dx <= STANDOFF_CELLS * 3; dx++) {
                int x = cx + dx;
                int y = cy + dy;
                if (x < 2 || y < 2 || x >= FORT_WIDTH - 2 || y >= FORT_HEIGHT - 2) continue;
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
        x = Math.max(2, Math.min(FORT_WIDTH - 3, x));
        y = Math.max(2, Math.min(FORT_HEIGHT - 3, y));
        for (int r = 0; r < 20; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int cx = x + dx;
                    int cy = y + dy;
                    if (cx < 1 || cy < 1 || cx >= FORT_WIDTH - 1 || cy >= FORT_HEIGHT - 1) continue;
                    if (sim.fightingPositions().isFightingPositionCell(cx, cy)) continue;
                    if (sim.getGrid().isWalkable(cx, cy)) return new int[]{cx, cy};
                }
            }
        }
        return new int[]{x, y};
    }
}
