package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
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
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleEngagement;
import com.dillon.starsectormarines.battle.vehicle.VehicleEngine;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehiclePlating;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Two machines on open ground: <b>do mechs fight armor from a duel band, or walk together and slug it
 * out?</b> The acceptance for {@link MechDuelRange} ({@code mechs-nouns.md}, Family and doctrine).
 *
 * <p>Three pairings:
 * <ul>
 *   <li><b>LRM Warden against a chaingun Hound.</b> The Warden (Long-Range Support) carries an
 *       attack-move at the Hound's ground; the Hound (Brawler) holds an area defence of its own. The
 *       Warden out-reaches the Hound by ten cells, so the rule holds it outside the chaingun; the
 *       Hound's short band trades better than its long one, so the rule closes it as far as its order
 *       lets it.</li>
 *   <li><b>The same pair the other way round</b>: the Hound attack-moves at a Warden holding its
 *       ground, and should close to its SRM band. Readings, mostly: the attack-move walks it in anyway.</li>
 *   <li><b>Two stock Bulwarks</b> under Brawler doctrine, one attack-moving at the other: the doctrine
 *       that seeks contact. (Frontline Support with nobody to pace holds where it lands and never meets the
 *       other; Balanced already holds at its medium band.) Equal machines trade alike at every distance, but
 *       each carries anti-armor racks fitted for exactly this target, so they should close to the racks' band
 *       and still stop short of contact.</li>
 * </ul>
 *
 * <p>Then vehicles, with no steering script at all. A Hound attack-moves at a deployed breakthrough tank (vehicle
 * engagement off so the pair asks about the mech alone). The player's tank, carrying a gun that out-reaches the
 * Hound's chaingun, is set down forty-four cells from a holding Hound with no order ({@link VehicleEngagement} on, and off as the control): it should drive to a
 * band outside the Hound's reach. And the player's tank is ordered once onto the Hound: an explicit move wins over
 * any band, so it goes there.
 *
 * <p>Verdicts assert distance mechanics, never who wins: wins, damage and deaths are readings. The
 * fight is the ticks from the first round either machine fires to the end of the window or a death.
 */
public final class MechDuelRangeScene implements BehaviorScene {

    static final String WARDEN_HOUND = "warden-hound";

    static final String HOUND_WARDEN = "hound-warden";

    static final String HOUND_TANK = "hound-tank";

    static final String TANK_HOUND = "tank-hound";
    static final String TANK_HOUND_CONTROL = "tank-hound-control";
    static final String TANK_ORDERED = "tank-ordered";
    /**
     * The unordered tank's gun: one that out-reaches the Hound's chaingun, so the stand-off is the question. The
     * Onager's stock HC-20 (26 cells) no longer does; the twin cannon it carried when this pair was written (36) did.
     */
    private static final MechWeaponComponent OUT_REACHING_GUN = MechWeaponComponent.AC_6;
    /**
     * Where an unordered tank is set down: forty-four cells off the Hound, inside its awareness (one and a half times
     * the AC-6's 34-cell reach) and outside the gun's reach.
     */
    private static final int TANK_UNORDERED_X = 56;
    static final String BULWARKS = "bulwarks";

    static final int FRAME_EVERY_TICKS = 15;

    private static final int WIDTH = 120;
    private static final int HEIGHT = 41;
    private static final int MID_Y = 20;
    private static final int ATTACKER_X = 12;
    private static final int DEFENDER_X = 100;
    private static final int WINDOW_TICKS = 2400;
    private static final long SEED = 1L;

    /** A Hound's chaingun reach: the Warden outside it is out of the Hound's fight. */
    static final float HOUND_REACH = 30f;
    /** A Hound's SRM reach: where its close band begins to pay. */
    static final float SRM_REACH = 18f;
    /** Inside this distance the Hound is inside a stand-off band built on its reach; the shooter should open it. */
    static final float GIVE_GROUND_DISTANCE = HOUND_REACH + 1.5f;
    /** Cells of ground, straight away from the Hound, that count as having given it. */
    static final float GIVE_GROUND_CELLS = 3f;
    /**
     * The tank's bar, lower than the walking Warden's: a tank backs up at 0.3 of its top speed (0.93 cells/s), and
     * it stands off only until its fire takes out the Hound's chaingun, when the Hound's reach falls to its SRM's
     * and the stand-off band closes in to 19.5. That window is about two seconds, so it gives under two cells.
     */
    static final float TANK_GIVE_GROUND_CELLS = 1f;
    /** Cells the Hound must close on the Warden once it is being shelled. */
    static final float HOUND_CLOSES_CELLS = 4f;

    /** Bulwarks closed to their racks: the fight's median distance at most this (the SRM reach and a cell). */
    static final float RACK_MEDIAN_MAX = SRM_REACH + 1f;
    static final float CONTACT_CELLS = 10f;
    /** A tank ordered onto a Hound's cell has gone there when it comes this close. */
    static final float ORDERED_ARRIVAL_CELLS = 8f;
    static final float CONTACT_SHARE_MAX = 0.1f;

    private static final String ATTACKER = "attacker";
    private static final String DEFENDER = "defender";

    @Override public String id() { return "mech-duel-range"; }

    @Override
    public String label() {
        return "Mech duel range: an LRM Warden stands off a chaingun Hound, the Hound closes,"
                + " equal Bulwarks close to their racks short of contact";
    }

    @Override public int frameDelayMillis() { return 60; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        boolean vehicleBefore = VehicleEngagement.isEnabled();
        try {
            Trace sh = playOnce(frames, WARDEN_HOUND, MechVariant.WARDEN, MechRole.LR_SUPPORT,
                    MechVariant.HOUND, MechRole.ASSAULT);
            Trace hs = playOnce(frames, HOUND_WARDEN, MechVariant.HOUND, MechRole.ASSAULT,
                    MechVariant.WARDEN, MechRole.LR_SUPPORT);
            Trace bb = playOnce(frames, BULWARKS, MechVariant.BULWARK, MechRole.ASSAULT,
                    MechVariant.BULWARK, MechRole.ASSAULT);
            Trace ht = playVehicle(frames, HOUND_TANK, VehicleCase.HOUND_ATTACKS, false);
            Trace th = playVehicle(frames, TANK_HOUND, VehicleCase.TANK_UNORDERED, true);
            Trace thc = playVehicle(frames, TANK_HOUND_CONTROL, VehicleCase.TANK_UNORDERED, false);
            Trace to = playVehicle(frames, TANK_ORDERED, VehicleCase.TANK_ORDERED, true);
            return List.of(reportWardenHound(sh),
                    reportHoundWarden(hs),
                    reportBulwarks(bb),
                    reportHoundTank(ht),
                    reportTankHound(th, TANK_HOUND, true),
                    reportTankHound(thc, TANK_HOUND_CONTROL, false),
                    reportTankOrdered(to));
        } finally {
            VehicleEngagement.setEnabledForEvidence(vehicleBefore);
        }
    }

    private Trace playOnce(FrameSink frames, String loopId, MechVariant attacker, MechRole attackerRole,
                           MechVariant defender, MechRole defenderRole) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
        }
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) grid.recomputeCoverAt(x, y);

        SceneBuilder builder = SceneBuilder.over(grid, topology).missionCompletion(false).seed(SEED);
        builder.squad(ATTACKER).faction(Faction.MARINE).size(1).at(ATTACKER_X, MID_Y)
                .mech(attacker, attackerRole)
                .assigned(id -> ObjectiveAssignment.attackMove(id, DEFENDER_X, MID_Y))
                .done();
        builder.squad(DEFENDER).faction(Faction.DEFENDER).size(1).at(DEFENDER_X, MID_Y)
                .mech(defender, defenderRole)
                .assigned(id -> ObjectiveAssignment.defendArea(id, DEFENDER_X, MID_Y))
                .done();
        SceneWorld world = builder.build();
        Trace trace = new Trace(loopId, world.members(ATTACKER)[0], world.members(DEFENDER)[0]);
        SceneRun run = SceneRun.of(world).observe(trace).frames(frames, loopId, FRAME_EVERY_TICKS, trace::caption);
        try {
            while (run.tick() < WINDOW_TICKS && trace.deathTick < 0) run.step();
        } finally {
            world.sim().close();
        }
        trace.ticks = run.tick();
        return trace;
    }

    /** Who moves in a tank-and-Hound loop. */
    private enum VehicleCase {
        /** The Hound (Brawler) attack-moves at a defending tank that is simply deployed. */
        HOUND_ATTACKS,
        /** The player's tank is set down from a Hound holding its ground ({@link #TANK_UNORDERED_X}), and given no order. */
        TANK_UNORDERED,
        /** The player's tank is given one move order at tick zero onto the holding Hound's cell. */
        TANK_ORDERED
    }

    /** A breakthrough tank and a Hound with no steering script. */
    private Trace playVehicle(FrameSink frames, String loopId, VehicleCase kind, boolean vehicleRule) {
        VehicleEngagement.setEnabledForEvidence(vehicleRule);
        boolean tankOrdered = kind != VehicleCase.HOUND_ATTACKS;
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
        }
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) grid.recomputeCoverAt(x, y);

        SceneBuilder builder = SceneBuilder.over(grid, topology).missionCompletion(false).seed(SEED);
        Faction houndSide = tankOrdered ? Faction.DEFENDER : Faction.MARINE;
        int houndX = tankOrdered ? DEFENDER_X : ATTACKER_X;
        builder.squad(ATTACKER).faction(houndSide).size(1).at(houndX, MID_Y)
                .mech(MechVariant.HOUND, MechRole.ASSAULT)
                .assigned(id -> tankOrdered
                        ? ObjectiveAssignment.defendArea(id, DEFENDER_X, MID_Y)
                        : ObjectiveAssignment.attackMove(id, DEFENDER_X, MID_Y))
                .done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        int tankX = kind == VehicleCase.HOUND_ATTACKS ? DEFENDER_X
                : kind == VehicleCase.TANK_UNORDERED ? TANK_UNORDERED_X : ATTACKER_X;
        Faction tankSide = tankOrdered ? Faction.MARINE : Faction.DEFENDER;
        VehicleMission deployed = VehicleMission.deployed(tankX + 0.5f, MID_Y + 0.5f);
        long tank = kind == VehicleCase.TANK_UNORDERED
                ? sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, List.of(), VehiclePlating.FACTORY,
                        VehicleEngine.STOCK, OUT_REACHING_GUN, tankSide, deployed)
                : sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, tankSide, deployed);
        sim.convoy().body(tank).teleport(tankX + 0.5f, MID_Y + 0.5f, tankOrdered ? 0f : 180f);
        long hound = world.members(ATTACKER)[0];
        // The trace's attacker is whoever moves in: the ordered tank, or the attack-moving Hound.
        Trace trace = tankOrdered
                ? new Trace(loopId, tank, hound)
                : new Trace(loopId, hound, tank);
        if (kind == VehicleCase.TANK_ORDERED) sim.getVehicleMoveOrderService().requestMove(tank, DEFENDER_X, MID_Y);
        SceneRun run = SceneRun.of(world).observe(trace).frames(frames, loopId, FRAME_EVERY_TICKS, trace::caption);
        try {
            while (run.tick() < WINDOW_TICKS && trace.deathTick < 0) run.step();
        } finally {
            sim.close();
        }
        trace.ticks = run.tick();
        return trace;
    }

    // ---- instrument ----

    private static final class Trace implements TickObserver {
        final String loopId;
        final long attacker;
        final long defender;
        int ticks;
        int deathTick = -1;
        String dead = "";
        int firstShotTick = -1;
        int attackerShots;
        int defenderShots;
        float attackerHpStart = Float.NaN;
        float defenderHpStart = Float.NaN;
        float attackerHp;
        float defenderHp;
        float distance;
        /** Defender's x when the first round of the fight is fired, and the least x it reached after. */
        float defenderXAtContact = Float.NaN;
        float defenderMinX = Float.MAX_VALUE;
        final List<Float> fightDistances = new ArrayList<>();
        /** How far the defender ever stood from where it started, and the closest the two ever came. */
        float defenderStartX = Float.NaN;
        float defenderStartY = Float.NaN;
        float defenderMaxDrift;
        float attackerStartX = Float.NaN;
        float attackerStartY = Float.NaN;
        float attackerMaxDrift;
        float closest = Float.MAX_VALUE;
        float prevAx = Float.NaN;
        float prevAy = Float.NaN;
        /** Fight ticks the Hound spent inside {@link #GIVE_GROUND_DISTANCE} of the shooter. */
        int insideStandoffTicks;
        /** Cells the shooter moved straight away from the Hound while it was inside that distance. */
        float attackerGaveGround;

        Trace(String loopId, long attacker, long defender) {
            this.loopId = loopId;
            this.attacker = attacker;
            this.defender = defender;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            boolean aAlive = alive(sim, attacker);
            boolean dAlive = alive(sim, defender);
            if (deathTick < 0 && (!aAlive || !dAlive)) {
                deathTick = tick;
                dead = !aAlive && !dAlive ? "both" : !aAlive ? "attacker" : "defender";
                return;
            }
            if (deathTick >= 0) return;
            if (Float.isNaN(attackerHpStart)) {
                attackerHpStart = integrity(sim, attacker);
                defenderHpStart = integrity(sim, defender);
            }
            attackerHp = integrity(sim, attacker);
            defenderHp = integrity(sim, defender);
            float[] a = position(sim, attacker);
            float[] d = position(sim, defender);
            float ax = a[0];
            float ay = a[1];
            float dx = d[0];
            float dy = d[1];
            if (Float.isNaN(defenderStartX)) {
                defenderStartX = dx;
                defenderStartY = dy;
            }
            defenderMaxDrift = Math.max(defenderMaxDrift, (float) Math.hypot(dx - defenderStartX, dy - defenderStartY));
            if (Float.isNaN(attackerStartX)) {
                attackerStartX = ax;
                attackerStartY = ay;
            }
            attackerMaxDrift = Math.max(attackerMaxDrift, (float) Math.hypot(ax - attackerStartX, ay - attackerStartY));
            closest = Math.min(closest, (float) Math.hypot(ax - dx, ay - dy));
            distance = (float) Math.hypot(ax - dx, ay - dy);
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId == attacker) attackerShots++;
                else if (shot.shooterId == defender) defenderShots++;
                else continue;
                if (firstShotTick < 0) {
                    firstShotTick = tick;
                    defenderXAtContact = dx;
                }
            }
            if (firstShotTick >= 0 && distance < GIVE_GROUND_DISTANCE && distance > 0f && !Float.isNaN(prevAx)) {
                insideStandoffTicks++;
                float away = ((ax - prevAx) * (ax - dx) + (ay - prevAy) * (ay - dy)) / distance;
                if (away > 0f) attackerGaveGround += away;
            }
            prevAx = ax;
            prevAy = ay;
            if (firstShotTick >= 0) {
                fightDistances.add(distance);
                defenderMinX = Math.min(defenderMinX, dx);
            }
        }

        private static boolean alive(BattleSimulation sim, long id) {
            if (sim.convoy().isVehicle(id)) return sim.convoy().isPresent(id);
            return sim.getRoster().isAliveById(id);
        }

        private static float[] position(BattleSimulation sim, long id) {
            if (sim.convoy().isVehicle(id)) {
                GroundBody body = sim.convoy().body(id);
                return new float[] {body.x, body.y};
            }
            return new float[] {sim.world().x(id), sim.world().y(id)};
        }

        private static float integrity(BattleSimulation sim, long id) {
            return sim.world().hp(id) + (sim.world().hasArmor(id) ? sim.world().armor(id) : 0f);
        }

        float shareAbove(float cells) {
            if (fightDistances.isEmpty()) return 0f;
            int n = 0;
            for (float d : fightDistances) if (d > cells) n++;
            return (float) n / fightDistances.size();
        }

        float shareBelow(float cells) {
            if (fightDistances.isEmpty()) return 0f;
            int n = 0;
            for (float d : fightDistances) if (d < cells) n++;
            return (float) n / fightDistances.size();
        }

        float median() {
            if (fightDistances.isEmpty()) return -1f;
            float[] sorted = new float[fightDistances.size()];
            for (int i = 0; i < sorted.length; i++) sorted[i] = fightDistances.get(i);
            Arrays.sort(sorted);
            return sorted[sorted.length / 2];
        }

        float min() {
            float m = Float.MAX_VALUE;
            for (float d : fightDistances) m = Math.min(m, d);
            return fightDistances.isEmpty() ? -1f : m;
        }

        float defenderClosed() {
            return Float.isNaN(defenderXAtContact) ? 0f : defenderXAtContact - defenderMinX;
        }

        String caption(int tick) {
            return String.format(Locale.ROOT, "%s  t=%.1fs  range %.1f  shots %d/%d  integrity %.0f/%.0f",
                    loopId, tick / 30f, distance, attackerShots, defenderShots,
                    attackerHp, defenderHp);
        }

        /**
         * The shooter opened distance while the Hound was inside its stand-off band. Conditional: a Hound that
         * never came that close asked nothing of it, and the detail says so.
         */
        Verdict givesGround(String name, String shooter) {
            return givesGround(name, shooter, GIVE_GROUND_CELLS);
        }

        Verdict givesGround(String name, String shooter, float wanted) {
            if (insideStandoffTicks == 0) {
                return Verdict.of(name, true, String.format(Locale.ROOT, "the Hound never came inside %.1f cells,"
                        + " so the %s was never asked to give ground", GIVE_GROUND_DISTANCE, shooter));
            }
            return Verdict.of(name, attackerGaveGround >= wanted, String.format(Locale.ROOT,
                    "with the Hound inside %.1f cells for %d ticks the %s gave %.1f cells of ground (wanted %.0f);"
                            + " %.0f%% of the fight beyond the Hound's reach, median %.1f", GIVE_GROUND_DISTANCE,
                    insideStandoffTicks, shooter, attackerGaveGround, wanted,
                    shareAbove(HOUND_REACH) * 100f, median()));
        }

        Map<String, Number> metrics() {
            Map<String, Number> m = new LinkedHashMap<>();

            m.put("ticks", ticks);
            m.put("firstShotTick", firstShotTick);
            m.put("deathTick", deathTick);
            m.put("fightTicks", fightDistances.size());
            m.put("medianFightDistance", median());
            m.put("insideStandoffTicks", insideStandoffTicks);
            m.put("attackerGaveGroundCells", attackerGaveGround);
            m.put("minFightDistance", min());
            m.put("shareAboveHoundReach", shareAbove(HOUND_REACH));
            m.put("shareInContact", shareBelow(CONTACT_CELLS));
            m.put("defenderClosedCells", defenderClosed());
            m.put("defenderMaxDriftCells", defenderMaxDrift);
            m.put("attackerMaxDriftCells", attackerMaxDrift);
            m.put("finalDistance", distance);
            m.put("closestEver", closest == Float.MAX_VALUE ? -1f : closest);
            m.put("attackerShots", attackerShots);
            m.put("defenderShots", defenderShots);
            m.put("attackerIntegrityLeft", attackerHpStart > 0f ? attackerHp / attackerHpStart : -1f);
            m.put("defenderIntegrityLeft", defenderHpStart > 0f ? defenderHp / defenderHpStart : -1f);
            m.put("attackerDied", dead.equals("attacker") || dead.equals("both") ? 1 : 0);
            m.put("defenderDied", dead.equals("defender") || dead.equals("both") ? 1 : 0);
            return m;
        }
    }

    // ---- verdicts ----

    private SceneReport reportWardenHound(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("fight-happened", t.firstShotTick >= 0,
                "first round at tick " + t.firstShotTick));
        // The Hound sorties off its post at an out-reaching shooter, so how much of the fight stays beyond
        // its reach is a reading of the chase; that the Warden opens distance when it comes on is the mechanic.
        verdicts.add(t.givesGround("warden-gives-ground", "Warden"));
        verdicts.add(Verdict.of("warden-fires", t.attackerShots > 0, t.attackerShots + " rounds"));
        verdicts.add(Verdict.of("hound-closes", t.defenderClosed() >= HOUND_CLOSES_CELLS,
                String.format(Locale.ROOT, "the Hound came %.1f cells toward the Warden after the first"
                        + " round (wanted %.0f)", t.defenderClosed(), HOUND_CLOSES_CELLS)));
        return new SceneReport(id(), WARDEN_HOUND, t.ticks, verdicts, t.metrics());
    }

    private SceneReport reportHoundWarden(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("fight-happened", t.firstShotTick >= 0,
                "first round at tick " + t.firstShotTick));
        // The Warden gives ground inside its order's leash, so how deep the Hound gets is a reading;
        // that it comes inside its own reach and fights is the mechanic.
        verdicts.add(Verdict.of("hound-closes-and-fires",
                t.min() >= 0f && t.min() < HOUND_REACH && t.attackerShots > 0,
                String.format(Locale.ROOT, "closest %.1f cells (wanted inside its %.0f-cell reach; its SRM"
                        + " band is %.0f), %d rounds", t.min(), HOUND_REACH, SRM_REACH, t.attackerShots)));
        return new SceneReport(id(), HOUND_WARDEN, t.ticks, verdicts, t.metrics());
    }

    private SceneReport reportHoundTank(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("fight-happened", t.firstShotTick >= 0,
                "first round at tick " + t.firstShotTick));
        // Vehicle engagement is off for this pair, so it asks about the mech rule alone: the tank holds.
        verdicts.add(Verdict.of("tank-held-for-isolation", t.defenderMaxDrift < 1f,
                String.format(Locale.ROOT, "the tank moved %.1f cells (wanted under 1)", t.defenderMaxDrift)));
        verdicts.add(Verdict.of("hound-uses-a-band", t.shareBelow(CONTACT_CELLS) <= CONTACT_SHARE_MAX,
                String.format(Locale.ROOT, "%.0f%% of the fight inside %.0f cells (wanted at most %.0f%%);"
                                + " median %.1f, closest %.1f", t.shareBelow(CONTACT_CELLS) * 100f, CONTACT_CELLS,
                        CONTACT_SHARE_MAX * 100f, t.median(), t.min())));
        return new SceneReport(id(), HOUND_TANK, t.ticks, verdicts, t.metrics());
    }

    private SceneReport reportTankHound(Trace t, String loopId, boolean subject) {
        List<Verdict> verdicts = new ArrayList<>();
        // The trace's attacker is the tank here; the Hound holds a post, and sorties off it.
        float moved = t.attackerMaxDrift;
        if (subject) {
            verdicts.add(Verdict.of("unordered-tank-engages", moved >= 5f && t.attackerShots > 0,
                    String.format(Locale.ROOT, "the tank drove %.1f cells and fired %d rounds", moved,
                            t.attackerShots)));
            float share = t.shareAbove(HOUND_REACH);
            // The Hound sorties off its post at a tank that out-reaches it: the share of the fight beyond its reach
            // is a reading of that chase, and the tank opening distance as it comes on is the mechanic.
            verdicts.add(t.givesGround("tank-gives-ground", "tank", TANK_GIVE_GROUND_CELLS));
        } else {
            verdicts.add(Verdict.of("control-tank-holds", moved < 1f,
                    String.format(Locale.ROOT, "the tank moved %.1f cells (wanted under 1)", moved)));
        }
        return new SceneReport(id(), loopId, t.ticks, verdicts, t.metrics());
    }

    private SceneReport reportTankOrdered(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        // An explicit player move wins over any band: the tank goes where it was sent.
        verdicts.add(Verdict.of("player-order-obeyed", t.closest <= ORDERED_ARRIVAL_CELLS,
                String.format(Locale.ROOT, "closest approach %.1f cells to the Hound it was sent onto (wanted"
                        + " within %.0f)", t.closest, ORDERED_ARRIVAL_CELLS)));
        return new SceneReport(id(), TANK_ORDERED, t.ticks, verdicts, t.metrics());
    }

    private SceneReport reportBulwarks(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("fight-happened", t.firstShotTick >= 0,
                "first round at tick " + t.firstShotTick));
        verdicts.add(Verdict.of("rack-band-not-contact",
                t.median() <= RACK_MEDIAN_MAX && t.shareBelow(CONTACT_CELLS) <= CONTACT_SHARE_MAX,
                String.format(Locale.ROOT, "median fight distance %.1f (wanted %.0f or less), %.0f%% of the"
                                + " fight inside %.0f cells (wanted at most %.0f%%), closest %.1f",
                        t.median(), RACK_MEDIAN_MAX, t.shareBelow(CONTACT_CELLS) * 100f, CONTACT_CELLS,
                        CONTACT_SHARE_MAX * 100f, t.min())));
        verdicts.add(Verdict.of("both-fire", t.attackerShots > 0 && t.defenderShots > 0,
                t.attackerShots + " / " + t.defenderShots + " rounds"));
        return new SceneReport(id(), BULWARKS, t.ticks, verdicts, t.metrics());
    }
}
