package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
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
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The missile carriers' acceptance: an {@link VehicleType#LRM_CARRIER} arcs its anti-armor salvos over a wall onto a
 * tank it cannot see when a scout car is looking at it, and holds when nobody is; an {@link VehicleType#SRM_TECHNICAL}
 * parked behind a building corner waits for a tank driving down the road to come into its reach and then fires.
 * Both racks are {@code hardenedTargetsOnly}, so each loop also stands Defender infantry in the launcher's own sight
 * and reach, and the launcher must never spend a warhead on them.
 *
 * <p>Three loops. {@code lrm-spotted}: the carrier behind a solid wall, the tank about 38 cells beyond it, a scout car
 * off the wall's south end with the tank in its sight and outside the tank's 36-cell reach. {@code lrm-no-spotter}:
 * the same world without the scout. {@code srm-ambush}: the technical tucked into a building's west face, the tank
 * ordered west along a road that passes about ten cells from it.
 *
 * <p>In the LRM loops the tank is <em>pinned</em>: the scene gives the Defender hull a paused authored route, which
 * keeps {@link VehicleEngagement} off it (a Defender hull never follows an authored route, so it simply brakes). Its
 * turret stays armed and autonomous. Without the pin the tank drives at the unarmed scout car and the question
 * becomes a chase. Nothing on the Marine side but the launcher is armed, so every point of tank integrity lost is the
 * launcher's.
 */
public final class MissileCarrierScene implements BehaviorScene {

    public static final String LRM_SPOTTED = "lrm-spotted";
    public static final String LRM_NO_SPOTTER = "lrm-no-spotter";
    public static final String SRM_AMBUSH = "srm-ambush";

    // ---- LRM world ----
    static final int LRM_WIDTH = 104;
    static final int LRM_HEIGHT = 60;
    public static final float CARRIER_X = 20.5f;
    public static final float CARRIER_Y = 30.5f;
    /** The wall between carrier and tank: x = 28, y 16..44. */
    public static final int LRM_WALL_X = 28;
    public static final int LRM_WALL_Y0 = 16;
    public static final int LRM_WALL_Y1 = 44;
    public static final float LRM_TANK_X = 58.5f;
    public static final float LRM_TANK_Y = 30.5f;
    /** The scout: about 42 cells from the tank, past the wall's south end. */
    public static final float SCOUT_X = 22.5f;
    public static final float SCOUT_Y = 52.5f;
    /** Defender riflemen in the open north of the carrier: about 25 cells, in its sight, outside a rifle's 22. */
    public static final int LRM_MEN_X = 18;
    public static final int LRM_MEN_Y = 5;

    // ---- SRM world ----
    static final int SRM_WIDTH = 110;
    static final int SRM_HEIGHT = 50;
    public static final float TECH_X = 38.5f;
    public static final float TECH_Y = 25.5f;
    /** The building the technical hides behind: x 41..56, y 20..28. */
    public static final int BLD_X0 = 41;
    public static final int BLD_X1 = 56;
    public static final int BLD_Y0 = 20;
    public static final int BLD_Y1 = 28;
    /** The road: y = 34, the tank driving west from x = 100 to x = 6. */
    public static final int ROAD_Y = 34;
    public static final float SRM_TANK_START_X = 100.5f;
    public static final int SRM_TANK_GOAL_X = 6;
    /** Unarmed Defender infantry west of the technical, about 12 cells off, in its sight. */
    public static final int SRM_MEN_X = 27;
    public static final int SRM_MEN_Y = 19;

    private static final int LRM_TICKS = 1500;
    private static final int SRM_TICKS = 1500;
    private static final String RIFLE = "weapon.field-rifle";
    /** Rounds from one launcher closer together than this are one salvo (a ripple of both racks). */
    private static final int SALVO_GAP_TICKS = 30;

    private final int frameEvery;

    public MissileCarrierScene() {
        this(15);
    }

    public MissileCarrierScene(int frameEvery) {
        this.frameEvery = frameEvery;
    }

    @Override public String id() { return "missile-carriers"; }

    @Override public String label() {
        return "Missile carriers: an LRM carrier arcs salvos over a wall onto a tank a scout car is watching and holds "
                + "with no spotter; an SRM technical behind a building corner holds until a passing tank is in reach; "
                + "neither spends a warhead on infantry";
    }

    @Override public int frameDelayMillis() { return 90; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        Trace spotted = runLrm(LRM_SPOTTED, true, frames);
        Trace blind = runLrm(LRM_NO_SPOTTER, false, frames);
        Trace ambush = runSrm(frames);
        List<SceneReport> reports = new ArrayList<>();
        reports.add(reportSpotted(spotted));
        reports.add(reportNoSpotter(blind));
        reports.add(reportAmbush(ambush));
        return reports;
    }

    private static SceneBuilder holdouts(SceneBuilder builder, int width, int height) {
        // Sealed boxes in the far corners, one per side, so neither side is ever absent and nobody can see into them.
        return builder
                .wall(width - 10, height - 10, width - 2, height - 10).wall(width - 10, height - 2, width - 2, height - 2)
                .wall(width - 10, height - 10, width - 10, height - 2).wall(width - 2, height - 10, width - 2, height - 2)
                .unit("defender-holdout", Faction.DEFENDER, UnitType.MARINE, width - 6, height - 6)
                .wall(width - 10, 1, width - 2, 1).wall(width - 10, 9, width - 2, 9)
                .wall(width - 10, 1, width - 10, 9).wall(width - 2, 1, width - 2, 9)
                .unit("marine-holdout", Faction.MARINE, UnitType.MARINE, width - 6, 5);
    }

    private Trace runLrm(String loop, boolean scout, FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(LRM_WIDTH, LRM_HEIGHT)
                .missionCompletion(false)
                .wall(LRM_WALL_X, LRM_WALL_Y0, LRM_WALL_X, LRM_WALL_Y1)
                .squad("men").faction(Faction.DEFENDER).size(3).inFile(LRM_MEN_X, LRM_MEN_Y, 2, 0)
                        .primary(RIFLE).stationary().done();
        holdouts(builder, LRM_WIDTH, LRM_HEIGHT);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();

        long carrier = sim.convoy().spawn(VehicleType.LRM_CARRIER, Faction.MARINE,
                VehicleMission.deployed(CARRIER_X, CARRIER_Y));
        sim.convoy().body(carrier).teleport(CARRIER_X, CARRIER_Y, 0f);
        long tank = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.DEFENDER,
                VehicleMission.deployed(LRM_TANK_X, LRM_TANK_Y));
        sim.convoy().body(tank).teleport(LRM_TANK_X, LRM_TANK_Y, 180f);
        pin(sim, tank);
        long car = 0L;
        if (scout) {
            car = sim.convoy().spawn(VehicleType.SCOUT_CAR, Faction.MARINE, VehicleMission.deployed(SCOUT_X, SCOUT_Y));
            sim.convoy().body(car).teleport(SCOUT_X, SCOUT_Y, 0f);
        }

        Trace trace = new Trace(world, carrier, tank, car);
        SceneRun run = SceneRun.of(world).observe(trace)
                .frames(frames, loop, frameEvery, tick -> loop + " / tick " + tick + " / " + trace.caption());
        run.run(LRM_TICKS);
        sim.close();
        return trace;
    }

    /** Holds a Defender hull where it stands: a paused authored route keeps engagement off it; its turret still works. */
    private static void pin(BattleSimulation sim, long id) {
        VehicleMission mission = sim.convoy().mission(id);
        GroundBody body = sim.convoy().body(id);
        mission.authoredRouteX = new float[] {body.x};
        mission.authoredRouteY = new float[] {body.y};
        mission.authoredRoutePaused = true;
    }

    private Trace runSrm(FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(SRM_WIDTH, SRM_HEIGHT)
                .missionCompletion(false);
        for (int y = BLD_Y0; y <= BLD_Y1; y++) builder.wall(BLD_X0, y, BLD_X1, y);
        // Unarmed and with a two-cell reach (an unarmed body still shoots with its type's stock gun), so they cannot
        // wreck the technical before the tank arrives: the question is what the technical shoots at.
        builder.squad("men").faction(Faction.DEFENDER).size(3).inFile(SRM_MEN_X, SRM_MEN_Y, 2, 0)
                .unarmed().each(spec -> spec.attackRange(2f)).stationary().done();
        holdouts(builder, SRM_WIDTH, SRM_HEIGHT);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();

        long tech = sim.convoy().spawn(VehicleType.SRM_TECHNICAL, Faction.MARINE, VehicleMission.deployed(TECH_X, TECH_Y));
        sim.convoy().body(tech).teleport(TECH_X, TECH_Y, 90f);
        // A Defender hull takes no player move order, so the tank drives the road as an ordinary delivery with no
        // passengers: inbound along the road to a drop point at its west end, then off the map.
        float roadY = ROAD_Y + 0.5f;
        float[] inX = {SRM_TANK_START_X, 70.5f, 40.5f, SRM_TANK_GOAL_X + 0.5f};
        float[] inY = {roadY, roadY, roadY, roadY};
        float[] outX = {SRM_TANK_GOAL_X + 0.5f, -8f};
        float[] outY = {roadY, roadY};
        long tank = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.DEFENDER,
                new VehicleMission(inX, inY, outX, outY, 0f, 0));

        Trace trace = new Trace(world, tech, tank, 0L);
        SceneRun run = SceneRun.of(world).observe(trace)
                .frames(frames, SRM_AMBUSH, frameEvery,
                        tick -> SRM_AMBUSH + " / tick " + tick + " / " + trace.caption());
        run.run(SRM_TICKS);
        sim.close();
        return trace;
    }

    // ---- reports ----

    private SceneReport reportSpotted(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("fired-unseen", t.roundsAtTankUnseen > 0,
                t.roundsAtTankUnseen + " of " + t.rounds + " carrier rounds flew at the tank on ticks the carrier had "
                        + "no grid line of sight to it (" + t.roundsAtTankSeen + " with sight); scout spotted the tank on "
                        + t.scoutSawTankTicks + " ticks"));
        boolean fired = t.roundsAtTank > 0;
        verdicts.add(Verdict.of("warheads-landed", !fired || t.tankLost > 0f,
                fired ? "tank lost " + round(t.tankLost) + " integrity after " + t.roundsAtTank
                        + " rounds at it; nothing else on the Marine side is armed"
                        : "precondition unmet: the carrier never fired at the tank, so nothing could land"));
        verdicts.add(heldAtMen(t, "carrier"));
        verdicts.add(Verdict.of("scout-survived", t.scoutAlive,
                "the scout car, about 42 cells from a tank of 36-cell reach, ended " + (t.scoutAlive ? "alive" : "wrecked")));
        return new SceneReport(id(), LRM_SPOTTED, LRM_TICKS, verdicts, readings(t));
    }

    private SceneReport reportNoSpotter(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("held-without-spotter", t.rounds == 0 && t.tankLost == 0f,
                "no spotter: carrier fired " + t.rounds + " rounds; the tank lost " + round(t.tankLost)
                        + " integrity; carrier had line of sight to it on " + t.launcherSawTankTicks + " ticks"));
        verdicts.add(heldAtMen(t, "carrier"));
        return new SceneReport(id(), LRM_NO_SPOTTER, LRM_TICKS, verdicts, readings(t));
    }

    private SceneReport reportAmbush(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        boolean fired = t.firstShotTick >= 0;
        verdicts.add(Verdict.of("held-position", !fired || t.movedBeforeFirstShot < 1f,
                fired ? "technical moved " + round(t.movedBeforeFirstShot) + " cells before its first shot (tick "
                        + t.firstShotTick + ")"
                        : "precondition unmet: the technical never fired; it moved " + round(t.launcherMoved)
                        + " cells in all"));
        boolean reached = t.closestTank <= 18f;
        verdicts.add(Verdict.of("fired-in-reach", t.roundsBeyondReach == 0 && (!reached || t.roundsAtTank > 0),
                t.roundsBeyondReach + " rounds while the tank was beyond 18 cells; " + t.roundsAtTank
                        + " at it once within; first shot at " + round(t.firstShotDistance) + " cells; closest "
                        + round(t.closestTank) + (reached ? "" : " (precondition unmet: the tank never came in reach)")
));
        verdicts.add(Verdict.of("warheads-landed", !fired || t.tankLost > 0f,
                fired ? "tank lost " + round(t.tankLost) + " integrity after " + t.roundsAtTank
                        + " rounds at it; nothing else on the Marine side is armed"
                        : "precondition unmet: the technical never fired"));
        verdicts.add(heldAtMen(t, "technical"));
        return new SceneReport(id(), SRM_AMBUSH, SRM_TICKS, verdicts, readings(t));
    }

    private static Verdict heldAtMen(Trace t, String what) {
        return Verdict.of("held-at-men", t.roundsAtMen == 0 && t.menLost == 0f,
                t.roundsAtMen + " " + what + " rounds flew at the infantry; they lost " + round(t.menLost)
                        + " durability; they stood in its own sight and reach on " + t.menExposedTicks + " ticks"
                        + (t.menExposedTicks == 0 ? " (precondition unmet: never exposed, so this asks nothing)" : ""));
    }

    private static Map<String, Number> readings(Trace t) {
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("salvos", t.salvos);
        m.put("rounds", t.rounds);
        m.put("roundsAtTank", t.roundsAtTank);
        m.put("roundsAtTankUnseen", t.roundsAtTankUnseen);
        m.put("roundsAtMen", t.roundsAtMen);
        m.put("firstShotTick", t.firstShotTick);
        m.put("firstShotDistance", round(t.firstShotDistance));
        m.put("tankIntegrityStart", round(t.tankStart));
        m.put("tankIntegrityLost", round(t.tankLost));
        m.put("tankWreckedTick", t.tankWreckedTick);
        m.put("tankClosest", round(t.closestTank));
        m.put("tankShots", t.tankShots);
        m.put("launcherIntegrityLost", round(t.launcherLost));
        m.put("launcherSurvived", t.launcherAlive ? 1 : 0);
        m.put("launcherMoved", round(t.launcherMoved));
        m.put("launcherAmmoLeft", t.launcherAmmo);
        m.put("menExposedTicks", t.menExposedTicks);
        m.put("scoutSawTankTicks", t.scoutSawTankTicks);
        return m;
    }

    private static float round(float v) {
        return Math.round(v * 100f) / 100f;
    }

    private static float integrity(BattleSimulation sim, long id) {
        if (!sim.world().isAlive(id)) return 0f;
        return Math.max(0f, sim.world().hp(id)) + (sim.world().hasArmor(id) ? Math.max(0f, sim.world().armor(id)) : 0f);
    }

    private static final class Trace implements TickObserver {
        final long launcher;
        final long tank;
        final long scout;
        final long[] men;
        final float[] menFull;
        final Set<Long> menSet = new HashSet<>();
        final float startX;
        final float startY;
        final float range;
        int rounds;
        int salvos;
        int lastRoundTick = Integer.MIN_VALUE / 2;
        int roundsAtTank;
        int roundsAtTankSeen;
        int roundsAtTankUnseen;
        int roundsAtMen;
        int roundsBeyondReach;
        int firstShotTick = -1;
        float firstShotDistance = -1f;
        float movedBeforeFirstShot;
        float launcherMoved;
        float tankStart = -1f;
        float tankLost;
        int tankWreckedTick = -1;
        float closestTank = Float.MAX_VALUE;
        int tankShots;
        float launcherStart = -1f;
        float launcherLost;
        boolean launcherAlive = true;
        int launcherAmmo = -1;
        float menLost;
        int menExposedTicks;
        int scoutSawTankTicks;
        int launcherSawTankTicks;
        boolean scoutAlive = true;
        private float lastLx;
        private float lastLy;
        private float lastTx;
        private float lastTy;
        private String caption = "";

        Trace(SceneWorld world, long launcher, long tank, long scout) {
            this.launcher = launcher;
            this.tank = tank;
            this.scout = scout;
            BattleSimulation sim = world.sim();
            GroundBody body = sim.convoy().body(launcher);
            this.startX = body.x;
            this.startY = body.y;
            this.lastLx = body.x;
            this.lastLy = body.y;
            GroundBody tankBody = sim.convoy().body(tank);
            this.lastTx = tankBody.x;
            this.lastTy = tankBody.y;
            this.range = sim.convoy().vehicleType(launcher).turretStructure().mount.weapon.range;
            this.men = world.members("men");
            this.menFull = new float[men.length];
            for (int i = 0; i < men.length; i++) {
                menSet.add(men[i]);
                menFull[i] = integrity(sim, men[i]);
            }
        }

        String caption() {
            return caption;
        }

        private static boolean present(BattleSimulation sim, long id) {
            return id != 0L && sim.convoy().isVehicle(id) && sim.convoy().isTargetable(id)
                    && sim.convoy().structure(id) > 0f;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            NavigationGrid grid = sim.getGrid();
            boolean launcherUp = present(sim, launcher);
            boolean tankUp = present(sim, tank);
            GroundBody lb = sim.convoy().body(launcher);
            GroundBody tb = sim.convoy().body(tank);
            // A hull reaped off the map edge or wrecked has no body; keep its last place.
            if (lb != null) {
                lastLx = lb.x;
                lastLy = lb.y;
            }
            if (tb != null) {
                lastTx = tb.x;
                lastTy = tb.y;
            } else {
                tankUp = false;
            }
            if (lb == null) launcherUp = false;
            float lx = lastLx;
            float ly = lastLy;
            float tx = lastTx;
            float ty = lastTy;
            float distance = (float) Math.hypot(tx - lx, ty - ly);
            boolean sight = grid.hasLineOfSight((int) Math.floor(lx), (int) Math.floor(ly),
                    (int) Math.floor(tx), (int) Math.floor(ty));
            if (tankUp && sight && distance <= range) launcherSawTankTicks++;
            if (tankUp) closestTank = Math.min(closestTank, distance);
            if (scout != 0L) {
                scoutAlive = present(sim, scout);
                if (scoutAlive && tankUp) {
                    GroundBody sb = sim.convoy().body(scout);
                    if (sb != null && HullSightings.sees(grid, sb.x, sb.y, tx, ty, VehicleType.SCOUT_CAR.sightCells())) {
                        scoutSawTankTicks++;
                    }
                }
            }
            launcherMoved = (float) Math.hypot(lx - startX, ly - startY);

            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId == tank) tankShots++;
                if (shot.shooterId != launcher) continue;
                rounds++;
                if (tick - lastRoundTick > SALVO_GAP_TICKS) salvos++;
                lastRoundTick = tick;
                if (firstShotTick < 0) {
                    firstShotTick = tick;
                    firstShotDistance = distance;
                    movedBeforeFirstShot = launcherMoved;
                }
                // Ground turret rounds carry no target id; the launcher's own lock at the end of the tick is it.
                GroundTurret turret = sim.convoy().turret(launcher);
                long target = turret == null ? 0L
                        : turret.burstTargetId != 0L ? turret.burstTargetId : turret.targetId;
                if (target == tank) {
                    roundsAtTank++;
                    if (sight) roundsAtTankSeen++;
                    else roundsAtTankUnseen++;
                } else if (menSet.contains(target)) {
                    roundsAtMen++;
                }
                if (distance > 18f && tankUp) roundsBeyondReach++;
            }

            // A tank that drove off the map edge is gone, not destroyed: its loss stays where it was last read.
            VehicleMission tankMission = sim.convoy().isVehicle(tank) ? sim.convoy().mission(tank) : null;
            boolean wrecked = tankMission != null && tankMission.state == VehicleState.WRECKED;
            if (tankStart < 0f) tankStart = integrity(sim, tank);
            if (tankUp) tankLost = Math.max(tankLost, tankStart - integrity(sim, tank));
            if (wrecked) tankLost = tankStart;
            if (wrecked && tankWreckedTick < 0) tankWreckedTick = tick;
            float launcherNow = launcherUp ? integrity(sim, launcher) : 0f;
            if (launcherStart < 0f) launcherStart = launcherNow;
            launcherLost = Math.max(launcherLost, launcherStart - launcherNow);
            launcherAlive = launcherUp;
            if (launcherUp && sim.convoy().turret(launcher) != null) launcherAmmo = sim.convoy().turret(launcher).ammo;

            float worn = 0f;
            boolean exposed = false;
            for (int i = 0; i < men.length; i++) {
                long id = men[i];
                worn += Math.max(0f, menFull[i] - integrity(sim, id));
                if (!launcherUp || !sim.world().isAlive(id)) continue;
                float mx = sim.world().x(id);
                float my = sim.world().y(id);
                if (Math.hypot(mx - lx, my - ly) <= range && grid.hasLineOfSight((int) Math.floor(lx),
                        (int) Math.floor(ly), (int) Math.floor(mx), (int) Math.floor(my))) {
                    exposed = true;
                }
            }
            menLost = worn;
            if (exposed && launcherAmmo > 0) menExposedTicks++;

            caption = String.format(Locale.ROOT,
                    "%s / tank %.0f cells %s / salvos %d, rounds %d / tank integrity lost %.0f of %.0f / infantry hit %d",
                    sim.convoy().vehicleType(launcher) == VehicleType.LRM_CARRIER ? "LRM carrier" : "SRM technical",
                    distance, sight ? "in sight" : "unseen", salvos, rounds, tankLost, tankStart, roundsAtMen);
        }
    }
}
