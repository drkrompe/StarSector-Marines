package com.dillon.starsectormarines.battle.warship;

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
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An Onslaught's missile mounts firing on a cluster of enemy marines, recorded four ways: a
 * pod of unguided Annihilator rockets, homing Harpoons, Hurricane multi-warhead
 * missiles, and a pair of Reaper torpedoes.
 *
 * <p>It asks what a missile has to be to be a missile and not a slow bullet: that
 * it takes the time its own kinematics give it, that a rocket and a homing missile
 * fly visibly different paths, that a multi-warhead missile carries the payload its
 * spec says and spreads it over the ground, and that a blast hurts as far as its
 * spec's damage footprint and no further, however large the fireball drawn.
 */
public final class WarshipMissilesScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 80;
    private static final int TICKS = 600;
    /** A multi-warhead missile flies longer and then spends seconds releasing its sheaf; the loop runs until the last of it lands. */
    private static final int MIRV_TICKS = 900;
    private static final float SHIP_X = 22f;
    private static final float SHIP_Y = 40f;
    /** Forward is (-sin h, cos h), so the cluster due east is a bearing of -90. */
    private static final float SHIP_HEADING = -90f;
    private static final String[] SLOTS = {"WS 021", "WS 022", "WS 023", "WS 024"};
    private static final int[][] CLUSTER_OFFSETS = {
            {-7, -5}, {-2, -9}, {4, -6}, {9, -2}, {6, 4}, {1, 8}, {-5, 6}, {-9, 1}, {0, 0}, {-3, -2}, {3, 2}};

    /** What the rocket loop saw, kept for the homing loop to be compared with. */
    private float rocketSignChanges = 0f;
    private float rocketPeakLateral = 0f;

    @Override public String id() { return "warship-missiles"; }

    @Override public String label() {
        return "Warship missiles: kinematic flight, rocket versus homing paths, true multi-warhead payloads, damage radius";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        reports.add(playCluster("rockets", "annihilatorpod", 4, 78f, TICKS, frames));
        reports.add(playCluster("homing", "harpoon", 4, 100f, TICKS, frames));
        reports.add(playCluster("mirv", "hurricane", 2, 104f, MIRV_TICKS, frames));
        reports.add(playTorpedo(frames));
        return reports;
    }

    /** The Onslaught's hull with one weapon in each of its first {@code mounts} missile slots and nothing else. */
    private static WarshipLoadout loadout(String weaponId, int mounts) {
        return new WarshipLoadout() {
            @Override public String hullId() { return "onslaught"; }

            @Override public String weaponInSlot(String slotId) {
                for (int i = 0; i < mounts; i++) if (SLOTS[i].equals(slotId)) return weaponId;
                return null;
            }
        };
    }

    private SceneReport playCluster(String loop, String weaponId, int mounts, float targetX,
                                    int ticks, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < CLUSTER_OFFSETS.length; i++) {
            String key = "e" + i;
            keys.add(key);
            builder.unit(key, Faction.DEFENDER, UnitType.MARINE,
                    Math.round(targetX) + CLUSTER_OFFSETS[i][0], 40 + CLUSTER_OFFSETS[i][1]);
        }
        builder.unit("anchor", Faction.DEFENDER, UnitType.MARINE, 150, 72);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261012L);
        Warship ship = Warship.fit(loadout(weaponId, mounts), Faction.MARINE, SHIP_X, SHIP_Y, SHIP_HEADING);
        ship.order(new WarshipOrder.FireOnArea(targetX, 40f, 14f, true));
        sim.addWarship(ship);
        Trace trace = new Trace(world, keys);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 3, tick -> loop + " / " + weaponId + " / tick " + tick)
                .run(ticks);
        return clusterReport(loop, weaponId, trace, ship, ticks);
    }

    private SceneReport clusterReport(String loop, String weaponId, Trace trace, Warship ship, int ticks) {
        VanillaWeapon weapon = VanillaWeapons.get(weaponId);
        List<Verdict> verdicts = new ArrayList<>();
        int fired = 0;
        for (Warship.Mount m : ship.mounts()) fired += m.shotsFired;
        verdicts.add(Verdict.of("rounds-fly", fired >= 4 && !trace.flights.isEmpty(),
                fired + " rounds fired, " + trace.flights.size() + " seen in flight"));
        verdicts.add(Verdict.of("landing-clock-preserved", trace.clockErrorTicks() <= 3,
                "the worst gap between a round's committed flight time and its observed one: "
                        + trace.clockErrorTicks() + " ticks over " + trace.landed() + " landings"));
        int firstFire = trace.firstFireTick();
        float shortest = trace.shortestFlightSeconds();
        verdicts.add(Verdict.of("shells-take-time",
                trace.firstDeathTick() < 0 || trace.firstDeathTick() >= firstFire + shortest / BattleSimulation.TICK_DT - 3,
                "first death at tick " + trace.firstDeathTick() + "; first round fired at tick " + firstFire
                        + " and the shortest flight takes " + shortest + " s"));
        verdicts.add(Verdict.of("kills-the-cluster", trace.deaths.size() >= 3,
                trace.deaths.size() + " of " + CLUSTER_OFFSETS.length + " marines dead"));

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("fired", fired);
        metrics.put("deaths", trace.deaths.size());
        metrics.put("meanSignChanges", trace.meanSignChanges());
        metrics.put("meanPeakLateral", trace.meanPeakLateral());
        metrics.put("meanFlightSeconds", trace.meanFlightSeconds());

        if (loop.equals("rockets")) {
            rocketSignChanges = trace.meanSignChanges();
            rocketPeakLateral = trace.meanPeakLateral();
            verdicts.add(Verdict.of("rockets-are-unguided", weapon.flight().guidance() == MissileFlightSpec.Guidance.UNGUIDED,
                    "the Annihilator pod's rocket reads as " + weapon.flight().guidance()));
            verdicts.add(Verdict.of("not-a-straight-line", trace.meanPeakLateral() > 0.3f,
                    "mean peak departure from the straight line " + trace.meanPeakLateral() + " cells"));
            verdicts.add(Verdict.of("volley-fans-out", trace.bulgesBothWays(),
                    "rockets bulge to both sides of the line: left " + trace.leftBulges + ", right " + trace.rightBulges));
            verdicts.add(Verdict.of("rockets-bulge-without-weaving", trace.meanSignChanges() < 1f,
                    "a rocket crosses its own line " + trace.meanSignChanges() + " times on average"));
        } else if (loop.equals("homing")) {
            verdicts.add(Verdict.of("harpoon-homes", weapon.flight().guidance() == MissileFlightSpec.Guidance.HOMING,
                    "the Harpoon reads as " + weapon.flight().guidance()));
            verdicts.add(Verdict.of("homing-weaves-across", trace.meanSignChanges() >= rocketSignChanges + 1.5f,
                    "homing crosses its line " + trace.meanSignChanges() + " times on average against a rocket's "
                            + rocketSignChanges));
            verdicts.add(Verdict.of("weave-within-turn-limit",
                    trace.worstLateralAcceleration() <= trace.turnLimit(weapon),
                    "worst lateral acceleration " + trace.worstLateralAcceleration() + " cells/s^2 against the missile's "
                            + trace.turnLimit(weapon)));
            verdicts.add(Verdict.of("paths-differ-from-rockets", trace.meanPeakLateral() != rocketPeakLateral,
                    "mean peak departure " + trace.meanPeakLateral() + " cells against a rocket's " + rocketPeakLateral));
        } else {
            MissileFlightSpec.Mirv mirv = weapon.flight().mirv();
            verdicts.add(Verdict.of("carries-its-true-payload",
                    trace.carriers > 0 && trace.payloadsMatch,
                    trace.carriers + " carriers, each with " + mirv.count() + " warheads of " + mirv.damage()
                            + " (" + mirv.totalDamage() + " per missile, not the table row's " + weapon.damagePerShot() + ")"));
            verdicts.add(Verdict.of("every-warhead-detonates",
                    trace.warheadsSeen == trace.releasedCarriers * mirv.count() && trace.warheadsLanded >= trace.warheadsSeen - 2,
                    trace.releasedCarriers + " carriers released " + trace.warheadsSeen + " warheads, "
                            + trace.warheadsLanded + " detonated"));
            verdicts.add(Verdict.of("delivered-damage-is-the-payload",
                    Math.abs(trace.damageDelivered - trace.warheadsLanded * mirv.damage()) < 0.01f
                            && trace.warheadsLanded > 0,
                    trace.damageDelivered + " damage over " + trace.warheadsLanded + " detonations of " + mirv.damage()));
            verdicts.add(Verdict.of("warheads-cover-the-ground", trace.minSpreadCells >= 2f * trace.blastCells,
                    "widest gap between one missile's warheads " + trace.minSpreadCells + " cells, a blast being "
                            + trace.blastCells + " cells"));
            verdicts.add(Verdict.of("warheads-follow-the-split",
                    trace.splitBeforeLanding && trace.warheadClockErrorTicks <= 3,
                    "warheads left the carrier after it had flown, and each detonated within "
                            + trace.warheadClockErrorTicks + " ticks of its committed delay"));
            metrics.put("warheads", trace.warheadsSeen);
            metrics.put("damageDelivered", trace.damageDelivered);
        }
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    private SceneReport playTorpedo(FrameSink frames) throws Exception {
        float targetX = 70f;
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.unit("target", Faction.DEFENDER, UnitType.MARINE, Math.round(targetX), 40);
        builder.unit("inner", Faction.MARINE, UnitType.MARINE, Math.round(targetX) + 5, 40);
        builder.unit("outer", Faction.MARINE, UnitType.MARINE, Math.round(targetX) + 11, 40);
        builder.unit("anchor", Faction.DEFENDER, UnitType.MARINE, 150, 72);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261012L);
        Warship ship = Warship.fit(loadout("reaper", 2), Faction.MARINE, SHIP_X, SHIP_Y, SHIP_HEADING);
        ship.order(new WarshipOrder.FireOnArea(targetX, 40f, 3f, true));
        sim.addWarship(ship);
        Trace trace = new Trace(world, List.of("target", "inner", "outer"));
        SceneRun.of(world).observe(trace)
                .frames(frames, "torpedo", 3, tick -> "torpedo / reaper / tick " + tick)
                .run(TICKS);
        VanillaWeapon reaper = VanillaWeapons.get("reaper");
        float damageCells = WarshipScale.cells(reaper.damageRadiusPx());
        float visualCells = WarshipScale.cells(reaper.explosionRadiusPx());
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("landing-clock-preserved", trace.clockErrorTicks() <= 3 && trace.landed() >= 1,
                "worst gap " + trace.clockErrorTicks() + " ticks over " + trace.landed() + " landings"));
        verdicts.add(Verdict.of("blast-uses-the-damage-radius",
                trace.landingRadius > 0f && Math.abs(trace.landingRadius - damageCells) < 1e-3f
                        && trace.landingRadius < visualCells,
                "the landing detonation reaches " + trace.landingRadius + " cells; the spec's damage radius is "
                        + damageCells + " and its visual fireball " + visualCells));
        verdicts.add(Verdict.of("fireball-is-drawn-at-the-visual-radius",
                Math.abs(trace.fireballCells - visualCells) < 1e-3f,
                "the heavy impact is sized " + trace.fireballCells + " cells against a visual radius of " + visualCells));
        verdicts.add(Verdict.of("inside-the-radius-dies", trace.deaths.containsKey("target") && trace.deaths.containsKey("inner"),
                "the target and the marine five cells from it: " + trace.deaths));
        verdicts.add(Verdict.of("outside-the-radius-lives", !trace.deaths.containsKey("outer"),
                "the marine eleven cells out, inside the visual fireball but past the damage radius: " + trace.deaths));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("damageRadiusCells", damageCells);
        metrics.put("visualRadiusCells", visualCells);
        metrics.put("deaths", trace.deaths.size());
        return new SceneReport(id(), "torpedo", TICKS, verdicts, metrics);
    }

    /** One round's record: when it appeared, how long its path is committed to take, and its shape. */
    private static final class Flight {
        final int fireTick;
        final float seconds;
        int landedTick = -1;

        Flight(int fireTick, float seconds) {
            this.fireTick = fireTick;
            this.seconds = seconds;
        }
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final List<String> keys;
        final Map<String, Integer> deaths = new LinkedHashMap<>();
        final Map<WarshipShell, Flight> flights = new IdentityHashMap<>();
        private final Map<WarheadStrike, int[]> strikes = new IdentityHashMap<>();
        private final Map<WarheadStrike, Float> strikeDelay = new IdentityHashMap<>();
        private final List<Float> peaks = new ArrayList<>();
        private final List<Integer> crossings = new ArrayList<>();
        private float worstAcceleration;
        int leftBulges;
        int rightBulges;
        int carriers;
        int releasedCarriers;
        boolean payloadsMatch = true;
        int warheadsSeen;
        int warheadsLanded;
        float damageDelivered;
        float minSpreadCells = Float.MAX_VALUE;
        float blastCells;
        boolean splitBeforeLanding = true;
        int warheadClockErrorTicks;
        float landingRadius;
        float fireballCells;

        private Trace(SceneWorld world, List<String> keys) {
            this.world = world;
            this.keys = keys;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (String key : keys) {
                if (!deaths.containsKey(key) && !sim.getRoster().isAliveById(world.unit(key))) {
                    deaths.put(key, tick);
                }
            }
            List<WarshipShell> live = sim.warships().shells();
            for (WarshipShell shell : live) {
                if (!flights.containsKey(shell)) firstSight(shell, tick);
            }
            for (Map.Entry<WarshipShell, Flight> e : flights.entrySet()) {
                if (e.getValue().landedTick < 0 && !containsIdentity(live, e.getKey())) {
                    e.getValue().landedTick = tick;
                    if (!e.getKey().warheads().isEmpty()) releasedCarriers++;
                }
            }
            for (WarheadStrike strike : sim.warships().strikes()) {
                if (!strikes.containsKey(strike)) {
                    strikes.put(strike, new int[]{tick, -1});
                    strikeDelay.put(strike, strike.delaySeconds);
                    warheadsSeen++;
                }
            }
            for (Map.Entry<WarheadStrike, int[]> e : strikes.entrySet()) {
                if (e.getValue()[1] < 0 && !containsIdentity(sim.warships().strikes(), e.getKey())) {
                    e.getValue()[1] = tick;
                    warheadsLanded++;
                    damageDelivered += e.getKey().detonation().damage;
                    float expected = strikeDelay.get(e.getKey()) / BattleSimulation.TICK_DT;
                    int error = Math.round(Math.abs((tick - e.getValue()[0]) - expected));
                    warheadClockErrorTicks = Math.max(warheadClockErrorTicks, error);
                }
            }
        }

        private static <T> boolean containsIdentity(List<T> list, T item) {
            for (T t : list) if (t == item) return true;
            return false;
        }

        private void firstSight(WarshipShell shell, int tick) {
            flights.put(shell, new Flight(tick, shell.flightSeconds));
            if (landingRadius == 0f && shell.landing() != null) {
                landingRadius = shell.landing().aoeRadius;
                fireballCells = shell.blastCells;
            }
            MissilePath path = shell.path();
            // The path is committed and closed-form: its whole shape can be read the moment it is fired.
            float peak = 0f;
            int changes = 0;
            float last = 0f;
            float h = 0.02f;
            for (float t = h; t < path.totalSeconds() - h; t += h) {
                float lateral = path.lateral(t);
                peak = Math.max(peak, Math.abs(lateral));
                if (Math.abs(lateral) > 1e-3f) {
                    if (last != 0f && Math.signum(lateral) != Math.signum(last)) changes++;
                    last = lateral;
                }
                float a = (path.lateral(t + h) - 2f * lateral + path.lateral(t - h)) / (h * h);
                worstAcceleration = Math.max(worstAcceleration, Math.abs(a));
            }
            peaks.add(peak);
            crossings.add(changes);
            if (path.bulge() > 0f) leftBulges++;
            else if (path.bulge() < 0f) rightBulges++;
            if (!shell.warheads().isEmpty()) carrier(shell);
        }

        private void carrier(WarshipShell shell) {
            carriers++;
            VanillaWeapon w = shell.weapon;
            MissileFlightSpec.Mirv mirv = w.flight().mirv();
            float total = 0f;
            float widest = 0f;
            List<WarshipShell.Warhead> heads = shell.warheads();
            for (WarshipShell.Warhead a : heads) {
                total += a.detonation().damage;
                blastCells = a.detonation().aoeRadius;
                if (a.detonation().damage != mirv.damage()) payloadsMatch = false;
                for (WarshipShell.Warhead b : heads) {
                    widest = Math.max(widest, (float) Math.hypot(a.aimX() - b.aimX(), a.aimY() - b.aimY()));
                }
                if (a.delaySeconds() <= 0f) splitBeforeLanding = false;
            }
            if (heads.size() != mirv.count() || Math.abs(total - mirv.totalDamage()) > 0.01f) payloadsMatch = false;
            minSpreadCells = Math.min(minSpreadCells, widest);
        }

        int landed() {
            int n = 0;
            for (Flight f : flights.values()) if (f.landedTick >= 0) n++;
            return n;
        }

        int clockErrorTicks() {
            int worst = 0;
            for (Flight f : flights.values()) {
                if (f.landedTick < 0) continue;
                float expected = f.seconds / BattleSimulation.TICK_DT;
                worst = Math.max(worst, Math.round(Math.abs((f.landedTick - f.fireTick) - expected)));
            }
            return worst;
        }

        int firstFireTick() {
            int first = Integer.MAX_VALUE;
            for (Flight f : flights.values()) first = Math.min(first, f.fireTick);
            return first == Integer.MAX_VALUE ? 0 : first;
        }

        float shortestFlightSeconds() {
            float s = Float.MAX_VALUE;
            for (Flight f : flights.values()) s = Math.min(s, f.seconds);
            return s == Float.MAX_VALUE ? 0f : s;
        }

        float meanFlightSeconds() {
            float sum = 0f;
            for (Flight f : flights.values()) sum += f.seconds;
            return flights.isEmpty() ? 0f : sum / flights.size();
        }

        float meanSignChanges() {
            float sum = 0f;
            for (int c : crossings) sum += c;
            return crossings.isEmpty() ? 0f : sum / crossings.size();
        }

        float meanPeakLateral() {
            float sum = 0f;
            for (float p : peaks) sum += p;
            return peaks.isEmpty() ? 0f : sum / peaks.size();
        }

        boolean bulgesBothWays() {
            return leftBulges >= 2 && rightBulges >= 2;
        }

        float worstLateralAcceleration() {
            return worstAcceleration;
        }

        /** What the missile's own turn rate allows laterally at its cruising speed, in cells per second squared. */
        float turnLimit(VanillaWeapon weapon) {
            return WarshipScale.cells(weapon.flight().maxSpeedPx())
                    * (float) Math.toRadians(weapon.flight().turnRateDegPerSec());
        }

        int firstDeathTick() {
            return deaths.values().stream().mapToInt(Integer::intValue).min().orElse(-1);
        }
    }
}
