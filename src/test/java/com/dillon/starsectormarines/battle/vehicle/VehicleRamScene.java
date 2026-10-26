package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.evacuation.SwarmStrains;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.HullContactSystem;
import com.dillon.starsectormarines.battle.sim.MechRamSystem;
import com.dillon.starsectormarines.battle.sim.VehicleRepulsionSystem;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hulls against bodies and against each other ({@code convoy-nouns.md}, ramming and hull contact): <b>does a hull
 * hurt only the other side, by its weight, and do two hulls meeting in a lane get past each other?</b>
 *
 * <ul>
 *   <li>{@code friendly-file}: a technical driven through a file of our own riflemen standing across the road.</li>
 *   <li>{@code runners-light} / {@code runners-heavy}: a technical and a gun tank driven into a Runner pack.</li>
 *   <li>{@code brutes-light} / {@code brutes-heavy}: the same two hulls driven at two Brutes in the road.</li>
 *   <li>{@code lane-meet}: two APCs ordered past each other down a walled lane five cells wide.</li>
 *   <li>{@code lane-meet-mixed}: a technical and a gun tank, in a lane seven wide: the tank is 3.9 cells across,
 *       so the two together with the route planner's clearance need more than six.</li>
 *   <li>{@code mech-friendly-file}, {@code mech-runners}: a Hound walked through the same file and pack.</li>
 *   <li>{@code mech-into-hull}: a Hound walked down the road through a parked APC, which outweighs it.</li>
 *   <li>{@code mech-meet}: a Hound and a Bulwark walked into each other head-on.</li>
 *   <li>{@code runners-parked}: a Runner pack beside a gun tank parked in the road; the tank is wider than a
 *       Runner's reach, so its centre is out of reach from every side ({@code colony-breach-nouns.md}, A standing
 *       vehicle).</li>
 *   <li>{@code parked-barricade}: the same tank parked in the only gap through a wall, a rifleman behind it and
 *       the pack in front.</li>
 * </ul>
 *
 * <p>Mechs are weighed in the same tonnes as hulls, so these loops ask the same questions of a walking machine:
 * friends shoved and never hurt, hostiles rammed, a machine never inside a hull, the lighter machine giving way.
 *
 * <p>Verdicts are mechanics: friends are shoved and never hurt; hostile bodies on the bow are rammed; against the
 * same Brutes the heavy hull deals more a ram than the light one and the light one takes damage back where the
 * heavy one takes none; hulls never sit inside each other; and both hulls of a lane meet reach their goals. Who
 * dies, how many rams, the ram damage and the meet's time are readings.
 */
public final class VehicleRamScene implements BehaviorScene {

    private static final int WIDTH = 64, HEIGHT = 25, ROAD = 12;
    private static final int FRAME_EVERY = 3;
    private static final int DRIVE_TICKS = 600, LANE_TICKS = 1500;
    /** A Bulwark walks a cell a second: two minutes covers the road and a stall. */
    private static final int MARCH_TICKS = 3600;
    private static final float DEEP = 5000f;
    /** Two bare footprints this far inside each other after a tick are overlapping, not touching. */
    static final float OVERLAP_TOLERANCE = .2f;
    /** A hull within this of its goal has arrived. */
    static final float ARRIVED = 3f;
    /** A technical into an adult Brute hurts itself from about 1.7 cells a second of closing ({@link HullRam}). */
    static final float LIGHT_PAYS_AT = 2f;
    /** How far a mech's collision circle may lean into a hull's footprint, cells. */
    static final float MECH_LEAN = .25f;

    @Override public String id() { return "vehicle-ram"; }

    @Override
    public String label() {
        return "Vehicle ram and hull contact: friends shoved unhurt, the other side rammed by weight, a lane meet";
    }

    @Override public int frameDelayMillis() { return 70; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(drive(frames, "friendly-file", VehicleType.MILITIA_TECHNICAL, Target.FRIENDS).report(null));
        reports.add(drive(frames, "runners-light", VehicleType.MILITIA_TECHNICAL, Target.RUNNERS).report(null));
        reports.add(drive(frames, "runners-heavy", VehicleType.BREAKTHROUGH_TANK, Target.RUNNERS).report(null));
        Drive light = drive(frames, "brutes-light", VehicleType.MILITIA_TECHNICAL, Target.BRUTES);
        Drive heavy = drive(frames, "brutes-heavy", VehicleType.BREAKTHROUGH_TANK, Target.BRUTES);
        reports.add(light.report(null));
        reports.add(heavy.report(light));
        reports.add(meet(frames, "lane-meet", VehicleType.HEAVY_APC, VehicleType.HEAVY_APC, 5));
        reports.add(meet(frames, "lane-meet-mixed", VehicleType.MILITIA_TECHNICAL, VehicleType.BREAKTHROUGH_TANK, 7));
        reports.add(march(frames, "mech-friendly-file", Walk.FRIENDS));
        reports.add(march(frames, "mech-runners", Walk.RUNNERS));
        reports.add(march(frames, "mech-into-hull", Walk.HULL));
        reports.add(march(frames, "mech-meet", Walk.MECH));
        reports.add(parked(frames, "runners-parked", false));
        reports.add(parked(frames, "parked-barricade", true));
        return reports;
    }

    // ---- Runners at a parked hull ----

    private static final int PARKED_TICKS = 900, WALL_X = 32;

    /**
     * Verdicts: the Runners strike the parked hull (from its side: they cannot reach its centre), and none of them
     * walks into it over and over (no Runner route stall); behind a hull barring the only gap, none gets past while it
     * stands. Hull damage, stalls and blows are readings.
     */
    private SceneReport parked(FrameSink frames, String loop, boolean barricade) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(7L);
        builder.wall(WIDTH - 6, 0, WIDTH - 6, 5).wall(WIDTH - 6, 5, WIDTH - 1, 5);
        builder.unit("far", Faction.DEFENDER, UnitType.MARINE_RED, WIDTH - 3, 2, spec -> spec.health(DEEP));
        if (barricade) {
            builder.wall(WALL_X, 0, WALL_X, ROAD - 2).wall(WALL_X, ROAD + 2, WALL_X, HEIGHT - 1);
            builder.squad("held").faction(Faction.MARINE).type(UnitType.MARINE).size(1)
                    .inFile(16, ROAD, 0, 1).stationary().done();
        }
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            long hull = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.MARINE,
                    VehicleMission.deployed(WALL_X + .5f, ROAD + .5f));
            sim.convoy().body(hull).teleport(WALL_X + .5f, ROAD + .5f, barricade ? 0f : -90f);
            sim.world().setMaxHp(hull, DEEP);
            sim.world().setHp(hull, DEEP);
            long[] runners = new long[6];
            for (int i = 0; i < runners.length; i++) {
                runners[i] = sim.spawn(new EntitySpec("Runner " + i, Faction.DEFENDER, UnitType.SWARM_RUNNER,
                        WALL_X + 8 + (i % 3) * 2, ROAD - 2 + (i / 3) * 4).role(UnitRole.SWARM_PRESSURE).health(DEEP));
            }
            float plateBefore = sim.world().hasArmor(hull) ? sim.world().armor(hull) : 0f;
            int stallsBefore = sim.movement().routeStalls();
            int past = 0;
            SceneRun run = SceneRun.of(world).frames(frames, loop, FRAME_EVERY, tick -> String.format(Locale.ROOT,
                    "%s  t=%.1fs  tank %.0f  stalls %d", loop, tick / 30f, sim.world().hp(hull),
                    sim.movement().routeStalls() - stallsBefore));
            int seen = stallsBefore, stalls = 0;
            StringBuilder stalled = new StringBuilder();
            while (run.tick() < PARKED_TICKS) {
                run.step();
                if (sim.movement().routeStalls() != seen) {
                    seen = sim.movement().routeStalls();
                    long who = sim.movement().lastStalled();
                    // Only the Runners' count: the rifleman holding his post behind the wall is not the question.
                    if (who != 0L && sim.identity().type(who) == UnitType.SWARM_RUNNER) stalls++;
                    if (who != 0L && sim.identity().type(who) == UnitType.SWARM_RUNNER) stalled.append(stalled.length() == 0 ? "" : ", ").append(sim.identity().type(who))
                            .append(String.format(Locale.ROOT, " %.1f,%.1f t%d", sim.world().x(who), sim.world().y(who), run.tick()));
                }
                for (long r : runners) if (sim.getRoster().isAliveById(r) && sim.world().x(r) < WALL_X - 3) past++;
            }
            // Plate first: a Runner's blow is mostly turned by a tank's armour, and what it takes off is struck all the same.
            float struck = DEEP - sim.world().hp(hull)
                    + plateBefore - (sim.world().hasArmor(hull) ? sim.world().armor(hull) : 0f);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("parked-hull-struck", struck > 0f,
                    String.format(Locale.ROOT, "%.1f health and plate off the tank", struck)));
            verdicts.add(Verdict.of("no-runner-route-stalls", stalls == 0, stalls + " Runner route stalls" + (stalled.length() == 0 ? "" : ": " + stalled)));
            if (barricade) {
                verdicts.add(Verdict.of("held-while-it-stands", past == 0 || sim.world().hp(hull) <= 0f,
                        past + " Runner-ticks past the wall with the tank standing"));
            }
            Map<String, Number> m = new LinkedHashMap<>();
            m.put("hullDamage", struck);
            m.put("routeStalls", stalls);
            m.put("runnerTicksPast", past);
            return new SceneReport(id(), loop, PARKED_TICKS, verdicts, m);
        }
    }

    private enum Target { FRIENDS, RUNNERS, BRUTES }

    // ---- a hull driven down the road ----

    private Drive drive(FrameSink frames, String loop, VehicleType type, Target target) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(7L);
        if (target == Target.FRIENDS) {
            builder.squad("file").faction(Faction.MARINE).type(UnitType.MARINE).size(5)
                    .inFile(26, ROAD - 2, 0, 1).stationary().done();
        }
        // The other side, boxed in out of sight, so the battle has two sides and nobody else fires at the file.
        builder.wall(WIDTH - 6, 0, WIDTH - 6, 5).wall(WIDTH - 6, 5, WIDTH - 1, 5);
        builder.unit("far", Faction.DEFENDER, UnitType.MARINE_RED, WIDTH - 3, 2, spec -> spec.health(DEEP));
        SceneWorld world = builder.build();
        Drive drive = new Drive(loop, type, target);
        try (BattleSimulation sim = world.sim()) {
            long hull = sim.convoy().spawn(type, Faction.MARINE, VehicleMission.deployed(6.5f, ROAD + .5f));
            sim.convoy().body(hull).teleport(6.5f, ROAD + .5f, -90f);
            sim.world().setMaxHp(hull, DEEP);
            sim.world().setHp(hull, DEEP);
            List<Long> bodies = new ArrayList<>();
            if (target == Target.FRIENDS) for (long m : world.members("file")) bodies.add(m);
            if (target == Target.RUNNERS) {
                for (int i = 0; i < 8; i++) {
                    bodies.add(sim.spawn(new EntitySpec("Runner " + i, Faction.DEFENDER, UnitType.SWARM_RUNNER,
                            28 + (i % 4) * 2, ROAD - 2 + (i / 4) * 3).role(UnitRole.SWARM_PRESSURE)));
                }
            }
            if (target == Target.BRUTES) {
                bodies.add(sim.spawn(SwarmStrains.spec("Brute A", UnitType.SWARM_BRUTE, 30, ROAD).health(DEEP)));
                bodies.add(sim.spawn(SwarmStrains.spec("Brute B", UnitType.SWARM_BRUTE, 34, ROAD + 1).health(DEEP)));
            }
            drive.bodies = bodies.stream().mapToLong(Long::longValue).toArray();
            float hpBefore = hp(sim, drive.bodies);
            VehicleRepulsionSystem repulsion = sim.vehicleRepulsion();
            SceneRun run = SceneRun.of(world).frames(frames, loop, FRAME_EVERY, tick -> String.format(Locale.ROOT,
                    "%s  %s  t=%.1fs  rams %d  dealt %.0f  taken %.0f", loop, type, tick / 30f,
                    repulsion.rams(), repulsion.ramDamageDealt(), repulsion.ramDamageTaken()));
            sim.getVehicleMoveOrderService().requestMove(hull, WIDTH - 10, ROAD);
            float topSpeed = 0f;
            while (run.tick() < DRIVE_TICKS) {
                run.step();
                if (sim.convoy().body(hull) != null) topSpeed = Math.max(topSpeed, Math.abs(sim.convoy().body(hull).speed));
            }
            drive.pushes = repulsion.pushes();
            drive.rams = repulsion.rams();
            drive.dealt = repulsion.ramDamageDealt();
            drive.taken = repulsion.ramDamageTaken();
            drive.fastest = repulsion.fastestRam();
            drive.hpLost = hpBefore - hp(sim, drive.bodies);
            drive.alive = 0;
            for (long b : drive.bodies) if (sim.getRoster().isAliveById(b)) drive.alive++;
            drive.topSpeed = topSpeed;
            drive.hullX = sim.convoy().body(hull).x;
        }
        return drive;
    }

    private static float hp(BattleSimulation sim, long[] bodies) {
        float sum = 0f;
        for (long b : bodies) if (sim.getRoster().isAliveById(b)) sum += sim.world().hp(b);
        return sum;
    }

    private final class Drive {
        final String loop;
        final VehicleType type;
        final Target target;
        long[] bodies;
        long pushes, rams;
        float dealt, taken, fastest, hpLost, topSpeed, hullX;
        int alive;

        Drive(String loop, VehicleType type, Target target) {
            this.loop = loop;
            this.type = type;
            this.target = target;
        }

        float perRam() { return rams > 0 ? dealt / rams : 0f; }

        SceneReport report(Drive lighter) {
            List<Verdict> verdicts = new ArrayList<>();
            if (target == Target.FRIENDS) {
                verdicts.add(Verdict.of("friends-in-the-road", pushes > 0, pushes + " shoves of the file"));
                verdicts.add(Verdict.of("friends-never-hurt", hpLost == 0f && rams == 0,
                        String.format(Locale.ROOT, "%.1f health lost, %d rams", hpLost, rams)));
            } else {
                verdicts.add(Verdict.of("hostiles-rammed", rams > 0,
                        String.format(Locale.ROOT, "%d rams dealing %.0f, fastest at %.1f cells/s", rams, dealt,
                                fastest)));
            }
            if (lighter != null) {
                boolean both = rams > 0 && lighter.rams > 0;
                verdicts.add(Verdict.of("heavier-hits-harder", !both || perRam() > lighter.perRam(),
                        both ? String.format(Locale.ROOT, "%s %.1f a ram against %s %.1f", type, perRam(),
                                lighter.type, lighter.perRam())
                                : "not compared: a hull landed no ram"));
                verdicts.add(Verdict.of("heavy-hull-unhurt", taken == 0f,
                        String.format(Locale.ROOT, "%s took %.1f back from the Brutes", type, taken)));
                // The light hull pays only for a ram struck at speed; a Brute that met it at a crawl costs nothing.
                boolean fast = lighter.fastest >= LIGHT_PAYS_AT;
                verdicts.add(Verdict.of("light-hull-pays", !fast || lighter.taken > 0f,
                        String.format(Locale.ROOT, "%s took %.1f back, fastest ram %.1f cells/s%s", lighter.type,
                                lighter.taken, lighter.fastest, fast ? "" : " (too slow to ask)")));
            }
            Map<String, Number> m = new LinkedHashMap<>();
            m.put("hullTonnes", type.massTonnes());
            m.put("topSpeed", topSpeed);
            m.put("hullEndX", hullX);
            m.put("pushes", pushes);
            m.put("rams", rams);
            m.put("ramDamageDealt", dealt);
            m.put("ramDamagePerRam", perRam());
            m.put("ramDamageTaken", taken);
            m.put("fastestRam", fastest);
            m.put("bodies", bodies.length);
            m.put("bodiesAlive", alive);
            m.put("bodyHealthLost", hpLost);
            return new SceneReport(id(), loop, DRIVE_TICKS, verdicts, m);
        }
    }

    // ---- two hulls meeting in a lane ----

    private SceneReport meet(FrameSink frames, String loop, VehicleType west, VehicleType east, int lane) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(7L);
        builder.wall(0, ROAD - 3, WIDTH - 1, ROAD - 3).wall(0, ROAD - 2 + lane, WIDTH - 1, ROAD - 2 + lane);
        builder.unit("far", Faction.DEFENDER, UnitType.MARINE_RED, WIDTH - 3, 2, spec -> spec.health(DEEP));
        SceneWorld world = builder.build();
        float westGoal = WIDTH - 7, eastGoal = 6;
        try (BattleSimulation sim = world.sim()) {
            long a = sim.convoy().spawn(west, Faction.MARINE, VehicleMission.deployed(eastGoal + .5f, ROAD + .5f));
            sim.convoy().body(a).teleport(eastGoal + .5f, ROAD + .5f, -90f);
            long b = sim.convoy().spawn(east, Faction.MARINE, VehicleMission.deployed(westGoal + .5f, ROAD + .5f));
            sim.convoy().body(b).teleport(westGoal + .5f, ROAD + .5f, 90f);
            HullContactSystem contact = sim.hullContact();
            float[] scratch = new float[3];
            float[] worst = {0f};
            int[] aArrived = {-1}, bArrived = {-1};
            SceneRun run = SceneRun.of(world).frames(frames, loop, FRAME_EVERY, tick -> String.format(Locale.ROOT,
                    "%s  %s vs %s  t=%.1fs  contacts %d  worst overlap %.2f", loop, west, east, tick / 30f,
                    contact.contacts(), worst[0]));
            sim.getVehicleMoveOrderService().requestMove(a, (int) westGoal, ROAD);
            sim.getVehicleMoveOrderService().requestMove(b, (int) eastGoal, ROAD);
            float aMinSpeed = Float.MAX_VALUE, bMinSpeed = Float.MAX_VALUE;
            while (run.tick() < LANE_TICKS && (aArrived[0] < 0 || bArrived[0] < 0)) {
                run.step();
                GroundBody ba = sim.convoy().body(a), bb = sim.convoy().body(b);
                if (HullContact.overlap(ba.x, ba.y, ba.facingDegrees, west.visualLengthCells / 2f,
                        west.visualWidthCells / 2f, bb.x, bb.y, bb.facingDegrees, east.visualLengthCells / 2f,
                        east.visualWidthCells / 2f, 0f, scratch)) {
                    worst[0] = Math.max(worst[0], scratch[2]);
                }
                if (aArrived[0] < 0 && Math.hypot(ba.x - westGoal, ba.y - ROAD) < ARRIVED) aArrived[0] = run.tick();
                if (bArrived[0] < 0 && Math.hypot(bb.x - eastGoal, bb.y - ROAD) < ARRIVED) bArrived[0] = run.tick();
                if (run.tick() > 60) {
                    aMinSpeed = Math.min(aMinSpeed, Math.abs(ba.speed));
                    bMinSpeed = Math.min(bMinSpeed, Math.abs(bb.speed));
                }
            }
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("hulls-met", contact.contacts() > 0, contact.contacts() + " contact ticks"));
            verdicts.add(Verdict.of("no-overlap", worst[0] < OVERLAP_TOLERANCE,
                    String.format(Locale.ROOT, "deepest overlap after a tick %.3f cells (tolerance %.2f)",
                            worst[0], OVERLAP_TOLERANCE)));
            verdicts.add(Verdict.of("no-deadlock", aArrived[0] >= 0 && bArrived[0] >= 0,
                    String.format(Locale.ROOT, "%s arrived at tick %d, %s at tick %d (window %d)", west, aArrived[0],
                            east, bArrived[0], LANE_TICKS)));
            Map<String, Number> m = new LinkedHashMap<>();
            m.put("laneCells", lane);
            m.put("westTonnes", west.massTonnes());
            m.put("eastTonnes", east.massTonnes());
            m.put("contactTicks", contact.contacts());
            m.put("worstOverlap", worst[0]);
            m.put("westArrivedTick", aArrived[0]);
            m.put("eastArrivedTick", bArrived[0]);
            m.put("westSlowest", aMinSpeed);
            m.put("eastSlowest", bMinSpeed);
            return new SceneReport(id(), loop, run.tick(), verdicts, m);
        }
    }

    // ---- a mech walked down the road ----

    private enum Walk { FRIENDS, RUNNERS, HULL, MECH }

    private static final float GOAL_EAST = WIDTH - 10, GOAL_WEST = 6;

    private SceneReport march(FrameSink frames, String loop, Walk walk) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(7L);
        builder.squad("hound").faction(Faction.MARINE).size(1).at(6, ROAD)
                .mech(MechVariant.HOUND, MechRole.ASSAULT).health(DEEP).done();
        if (walk == Walk.FRIENDS) {
            builder.squad("file").faction(Faction.MARINE).type(UnitType.MARINE).size(5)
                    .inFile(26, ROAD - 2, 0, 1).stationary().done();
        }
        if (walk == Walk.MECH) {
            builder.squad("bulwark").faction(Faction.MARINE).size(1).at((int) GOAL_EAST, ROAD)
                    .mech(MechVariant.BULWARK, MechRole.ASSAULT).health(DEEP).done();
        }
        builder.wall(WIDTH - 6, 0, WIDTH - 6, 5).wall(WIDTH - 6, 5, WIDTH - 1, 5);
        builder.unit("far", Faction.DEFENDER, UnitType.MARINE_RED, WIDTH - 3, 2, spec -> spec.health(DEEP));
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            long hound = world.members("hound")[0];
            long other = walk == Walk.MECH ? world.members("bulwark")[0] : 0L;
            long hull = 0L;
            if (walk == Walk.HULL) {
                hull = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.MARINE,
                        VehicleMission.deployed(30.5f, ROAD + .5f));
                sim.convoy().body(hull).teleport(30.5f, ROAD + .5f, -90f);
            }
            long[] bodies = walk == Walk.FRIENDS ? world.members("file") : new long[0];
            if (walk == Walk.RUNNERS) {
                bodies = new long[8];
                for (int i = 0; i < 8; i++) {
                    bodies[i] = sim.spawn(new EntitySpec("Runner " + i, Faction.DEFENDER, UnitType.SWARM_RUNNER,
                            28 + (i % 4) * 2, ROAD - 2 + (i / 4) * 3).role(UnitRole.SWARM_PRESSURE));
                }
            }
            float hpBefore = hp(sim, bodies);
            float[] start = new float[bodies.length * 2];
            for (int i = 0; i < bodies.length; i++) {
                start[2 * i] = sim.world().x(bodies[i]);
                start[2 * i + 1] = sim.world().y(bodies[i]);
            }
            MechRamSystem ram = sim.mechRam();
            float hullX0 = hull != 0L ? sim.convoy().body(hull).x : 0f;
            float hullY0 = hull != 0L ? sim.convoy().body(hull).y : 0f;
            float deepestInHull = 0f, houndSide = 0f, otherSide = 0f;
            int houndArrived = -1, otherArrived = -1;
            SceneRun run = SceneRun.of(world).frames(frames, loop, FRAME_EVERY * 2, tick -> String.format(Locale.ROOT,
                    "%s  HOUND  t=%.1fs  rams %d  dealt %.0f  taken %.0f", loop, tick / 30f, ram.rams(),
                    ram.damageDealt(), ram.damageTaken()));
            sim.getMechMoveOrderService().requestMove(hound, (int) GOAL_EAST, ROAD);
            if (other != 0L) sim.getMechMoveOrderService().requestMove(other, (int) GOAL_WEST, ROAD);
            float lane = ROAD + .5f;
            while (run.tick() < MARCH_TICKS && (houndArrived < 0 || (other != 0L && otherArrived < 0))) {
                run.step();
                float hx = sim.world().x(hound), hy = sim.world().y(hound);
                houndSide = Math.max(houndSide, Math.abs(hy - lane));
                if (houndArrived < 0 && Math.hypot(hx - GOAL_EAST - .5f, hy - lane) < ARRIVED) {
                    houndArrived = run.tick();
                }
                if (other != 0L) {
                    float ox = sim.world().x(other), oy = sim.world().y(other);
                    otherSide = Math.max(otherSide, Math.abs(oy - lane));
                    if (otherArrived < 0 && Math.hypot(ox - GOAL_WEST - .5f, oy - lane) < ARRIVED) {
                        otherArrived = run.tick();
                    }
                }
                if (hull != 0L) {
                    GroundBody hb = sim.convoy().body(hull);
                    float gap = HullImpact.surfaceDistance(hb.x, hb.y, hb.facingDegrees,
                            VehicleType.HEAVY_APC.visualLengthCells / 2f, VehicleType.HEAVY_APC.visualWidthCells / 2f,
                            hx, hy);
                    deepestInHull = Math.max(deepestInHull, sim.getRoster().radius(hound) - gap);
                }
            }
            float moved = 0f;
            for (int i = 0; i < bodies.length; i++) {
                if (!sim.getRoster().isAliveById(bodies[i])) continue;
                moved = Math.max(moved, (float) Math.hypot(sim.world().x(bodies[i]) - start[2 * i],
                        sim.world().y(bodies[i]) - start[2 * i + 1]));
            }
            float hpLost = hpBefore - hp(sim, bodies);
            List<Verdict> verdicts = new ArrayList<>();
            Map<String, Number> m = new LinkedHashMap<>();
            switch (walk) {
                case FRIENDS -> {
                    verdicts.add(Verdict.of("friends-in-the-road", moved > .3f,
                            String.format(Locale.ROOT, "the file moved up to %.2f cells", moved)));
                    verdicts.add(Verdict.of("friends-never-hurt", hpLost == 0f && ram.rams() == 0,
                            String.format(Locale.ROOT, "%.1f health lost, %d rams", hpLost, ram.rams())));
                }
                case RUNNERS -> verdicts.add(Verdict.of("hostiles-rammed", ram.rams() > 0,
                        String.format(Locale.ROOT, "%d rams dealing %.0f", ram.rams(), ram.damageDealt())));
                case HULL -> {
                    GroundBody hb = sim.convoy().body(hull);
                    m.put("hullMoved", (float) Math.hypot(hb.x - hullX0, hb.y - hullY0));
                    // A mech's collision circle may lean on a hull a little; it may not stand inside it.
                    verdicts.add(Verdict.of("mech-not-inside-hull", deepestInHull < MECH_LEAN,
                            String.format(Locale.ROOT, "deepest %.2f cells inside the APC's footprint (tolerance %.2f)",
                                    deepestInHull, MECH_LEAN)));
                    verdicts.add(Verdict.of("mech-gets-past", houndArrived >= 0, "arrived at tick " + houndArrived));
                }
                case MECH -> {
                    verdicts.add(Verdict.of("no-deadlock", houndArrived >= 0 && otherArrived >= 0,
                            String.format(Locale.ROOT, "Hound arrived at tick %d, Bulwark at tick %d", houndArrived,
                                    otherArrived)));
                    verdicts.add(Verdict.of("lighter-gives-way", houndSide >= otherSide,
                            String.format(Locale.ROOT, "Hound off the road %.2f cells, Bulwark %.2f", houndSide,
                                    otherSide)));
                }
            }
            m.put("houndTonnes", HullRam.bodyTonnes(UnitType.HEAVY_MECH, null, MechVariant.HOUND.relativeMass));
            m.put("rams", ram.rams());
            m.put("ramDamageDealt", ram.damageDealt());
            m.put("ramDamageTaken", ram.damageTaken());
            m.put("bodyHealthLost", hpLost);
            m.put("bodiesMovedUpTo", moved);
            m.put("houndArrivedTick", houndArrived);
            m.put("houndOffRoad", houndSide);
            m.put("otherArrivedTick", otherArrived);
            m.put("otherOffRoad", otherSide);
            m.put("deepestInHull", deepestInHull);
            return new SceneReport(id(), loop, run.tick(), verdicts, m);
        }
    }
}
