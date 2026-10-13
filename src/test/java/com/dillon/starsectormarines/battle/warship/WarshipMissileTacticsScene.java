package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.ShuttleType;
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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The missile behaviours the spec asks for beyond a plain flight: a carrier that may split early, a
 * submunition that flanks, and a missile that stands off and fires a beam.
 *
 * <p>Each answers one question the doc (<code>warship-missiles.md</code>) promises. A Hurricane sent
 * at a district nearer than its split range splits before its dispenser has armed, because its spec
 * says it may. A Hydra's platforms swing out abeam of the ground and come in from the side, to both
 * flanks, where a Hurricane's sheaf comes more nearly straight in. A Dragonfire stops at its spec's
 * standoff, paints for its spec's time, and then fires a beam that point defence, which can stop the
 * missile on its way out, cannot touch once it is loose.
 */
public final class WarshipMissileTacticsScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 90;
    private static final int TICKS = 900;
    /** Dragonfires fly out slowly and paint for three seconds, and a mount fires them one after another. */
    private static final int DRAGON_TICKS = 1500;
    private static final int FRAME_EVERY_TICKS = 6;
    private static final float SHIP_X = 20f;
    private static final float SHIP_Y = 45f;
    private static final float SHIP_HEADING = -90f;
    private static final float TARGET_Y = 45f;
    private static final float AREA_RADIUS = 6f;
    private static final int DISTRICT = 36;
    private static final float DISTRICT_RADIUS = 12f;
    private static final String[] SLOTS = {"WS 021", "WS 022", "WS 023", "WS 024"};
    private static final long SEED = 20261012L;

    private float targetX;
    /** The mean angle off the line of fire the Hurricane control's warheads come in at, for the Hydra loop to be read against. */
    private float controlArrival = Float.NaN;
    /** The mean share of its leg the control's warheads swing out. */
    private float controlSwing = Float.NaN;

    @Override public String id() { return "warship-missile-tactics"; }

    @Override public String label() {
        return "Warship missile tactics: early splits, the Hydra's flanking approach, and the Dragonfire's standoff beam";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        if (wanted("hurricane-close")) reports.add(playSplit("hurricane-close", "hurricane", 62f, frames));
        if (wanted("hurricane-control")) reports.add(playFlank("hurricane-control", "hurricane", FrameSink.NONE));
        if (wanted("hydra-flank")) reports.add(playFlank("hydra-flank", "hydra", frames));
        if (wanted("locust-anti-air")) reports.add(playAntiAir(frames));
        if (wanted("dragonfire")) reports.add(playDragon("dragonfire", false, frames));
        if (wanted("dragonfire-pd")) reports.add(playDragon("dragonfire-pd", true, frames));
        return reports;
    }

    private static boolean wanted(String loop) {
        String only = System.getProperty("battle.tactics.loops");
        return only == null || only.isBlank() || List.of(only.split(",")).contains(loop);
    }

    private static WarshipLoadout loadout(String weaponId, int mounts) {
        return new WarshipLoadout() {
            @Override public String hullId() { return "onslaught"; }

            @Override public String weaponInSlot(String slotId) {
                for (int i = 0; i < mounts; i++) if (SLOTS[i].equals(slotId)) return weaponId;
                return null;
            }
        };
    }

    private List<String> district(SceneBuilder builder) {
        List<String> keys = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (int i = 0; keys.size() < DISTRICT && i < DISTRICT * 4; i++) {
            double r = DISTRICT_RADIUS * Math.sqrt((i + 0.5) / (DISTRICT * 1.6));
            double a = i * 2.399963;
            int x = (int) Math.round(targetX + Math.cos(a) * r);
            int y = (int) Math.round(TARGET_Y + Math.sin(a) * r);
            if (!taken.add(((long) x << 32) | y)) continue;
            String key = "d" + keys.size();
            keys.add(key);
            builder.unit(key, Faction.DEFENDER, UnitType.MARINE, x, y);
        }
        builder.unit("anchor", Faction.MARINE, UnitType.MARINE, 4, 4);
        return keys;
    }

    private Warship shooter(String weaponId, int mounts, MissilePreset preset, SceneWorld world) throws Exception {
        Warship ship = Warship.fit(loadout(weaponId, mounts), Faction.MARINE, SHIP_X, SHIP_Y, SHIP_HEADING);
        ship.setMissilePreset(preset);
        ship.order(new WarshipOrder.FireOnArea(targetX, TARGET_Y, AREA_RADIUS, true));
        world.sim().addWarship(ship);
        return ship;
    }

    // ---------------------------------------------------------------------------------------- early split

    private SceneReport playSplit(String loop, String weaponId, float x, FrameSink frames) throws Exception {
        targetX = x;
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        List<String> keys = district(builder);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(SEED);
        shooter(weaponId, 2, MissilePreset.FLAVOURED, world);
        SplitTrace trace = new SplitTrace(world, keys);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, FRAME_EVERY_TICKS, tick -> loop + " / " + weaponId + " / tick " + tick)
                .run(TICKS);
        trace.observe(sim, TICKS);
        MissileFlightSpec.Mirv mirv = VanillaWeapons.get(weaponId).flight().mirv();
        float range = Math.abs(targetX - SHIP_X);
        float splitRange = WarshipScale.cells(mirv.splitRangePx());
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("target-is-inside-split-range", range < splitRange,
                "the district is " + range + " cells off against a split range of " + splitRange));
        verdicts.add(Verdict.of("carriers-split", trace.splits >= 2, trace.splits + " carriers split"));
        verdicts.add(Verdict.of("splits-before-the-dispenser-arms",
                mirv.canSplitEarly() && trace.splits > 0 && trace.shortestAge < mirv.minTimeToSplit(),
                "the earliest split came " + trace.shortestAge + " s after launch against a spec arming time of "
                        + mirv.minTimeToSplit() + " s (canSplitEarly=" + mirv.canSplitEarly() + ")"));
        verdicts.add(Verdict.of("splits-near-the-launch",
                trace.splits > 0 && trace.longestShare <= 0.45f,
                "carriers split at no more than " + trace.longestShare + " of the way to the ground"));
        verdicts.add(Verdict.of("kills-the-district", trace.deaths.size() >= 6, trace.deaths.size() + " of " + DISTRICT + " dead"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("splits", trace.splits);
        metrics.put("shortestAgeSeconds", trace.shortestAge);
        metrics.put("deaths", trace.deaths.size());
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class SplitTrace implements TickObserver {
        private final SceneWorld world;
        private final List<String> keys;
        final Map<String, Integer> deaths = new LinkedHashMap<>();
        private final Map<WarshipShell, Boolean> carriers = new IdentityHashMap<>();
        private final Set<SplitEvent> seen = new HashSet<>();
        int splits;
        float shortestAge = Float.MAX_VALUE;
        float longestShare;

        private SplitTrace(SceneWorld world, List<String> keys) {
            this.world = world;
            this.keys = keys;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (String key : keys) {
                if (!deaths.containsKey(key) && !sim.getRoster().isAliveById(world.unit(key))) deaths.put(key, tick);
            }
            for (WarshipShell s : sim.warships().shells()) if (s.carrier()) carriers.put(s, Boolean.TRUE);
            for (SplitEvent e : sim.warships().splits()) {
                if (!seen.add(e)) continue;
                splits++;
                for (WarshipShell c : carriers.keySet()) {
                    if (Math.abs(c.path().landX() - e.x) + Math.abs(c.path().landY() - e.y) > 1e-3f) continue;
                    float line = (float) Math.hypot(c.toX - c.fromX, c.toY - c.fromY);
                    float flown = (float) Math.hypot(e.x - c.fromX, e.y - c.fromY);
                    longestShare = Math.max(longestShare, flown / Math.max(1e-3f, line));
                    shortestAge = Math.min(shortestAge, c.flightSeconds);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------- flanking

    private SceneReport playFlank(String loop, String weaponId, FrameSink frames) throws Exception {
        targetX = 92f;
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        List<String> keys = district(builder);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(SEED);
        shooter(weaponId, 2, MissilePreset.FLAVOURED, world);
        FlankTrace trace = new FlankTrace(world, keys);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, FRAME_EVERY_TICKS, tick -> loop + " / " + weaponId + " / tick " + tick)
                .run(TICKS);
        trace.observe(sim, TICKS);
        MissileFlightSpec.Mirv mirv = VanillaWeapons.get(weaponId).flight().mirv();
        List<Verdict> verdicts = new ArrayList<>();
        Map<String, Number> metrics = new LinkedHashMap<>();
        verdicts.add(Verdict.of("sheaves-released", trace.sheaves >= 2 && trace.warheads == trace.sheaves * mirv.count(),
                trace.sheaves + " sheaves, " + trace.warheads + " warheads, the spec is " + mirv.count() + " each"));
        float arrival = trace.meanArrivalOffDegrees();
        metrics.put("meanArrivalOffLineDegrees", arrival);
        metrics.put("meanSwingShareOfLeg", trace.meanSwing());
        if (mirv.flanks()) {
            verdicts.add(Verdict.of("swings-abeam", trace.minSwing() >= 0.18f,
                    "the least any platform swings out is " + trace.minSwing() + " of its leg"));
            verdicts.add(Verdict.of("arrives-from-the-side", arrival >= 30f,
                    "platforms come in " + arrival + " degrees off the line of fire on average"));
            verdicts.add(Verdict.of("both-flanks-used", trace.sheavesWithBothSides() == trace.sheaves,
                    trace.sheavesWithBothSides() + " of " + trace.sheaves + " sheaves go out to both sides"));
            verdicts.add(Verdict.of("flank-exceeds-the-bloom-control",
                    Float.isNaN(controlSwing) || trace.meanSwing() > controlSwing + 0.1f,
                    "Hydra platforms swing out " + trace.meanSwing() + " of their leg on average, a Hurricane sheaf "
                            + controlSwing + " (its fan comes in " + controlArrival + " degrees off the line)"));
            float payload = trace.sheaves * mirv.totalDamage();
            float accounted = trace.landedDamage + trace.inFlightDamage(sim);
            verdicts.add(Verdict.of("damage-budget-conserved",
                    payload > 0f && Math.abs(accounted - payload) <= payload * 0.01f,
                    accounted + " damage landed or in the air against " + payload));
            verdicts.add(Verdict.of("kills-the-district", trace.deaths.size() >= 6, trace.deaths.size() + " of " + DISTRICT + " dead"));
        } else {
            controlArrival = arrival;
            controlSwing = trace.meanSwing();
            verdicts.add(Verdict.of("control-does-not-flank", !mirv.flanks(), weaponId + " carries no angled approach"));
        }
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class FlankTrace implements TickObserver {
        private final SceneWorld world;
        private final List<String> keys;
        final Map<String, Integer> deaths = new LinkedHashMap<>();
        private final Map<WarheadStrike, Boolean> strikes = new IdentityHashMap<>();
        private final Map<String, int[]> sides = new LinkedHashMap<>();
        private final Map<WarheadStrike, Boolean> landed = new IdentityHashMap<>();
        int sheaves;
        int warheads;
        float landedDamage;
        private float swingSum;
        private float swingMin = Float.MAX_VALUE;
        private float offSum;

        private FlankTrace(SceneWorld world, List<String> keys) {
            this.world = world;
            this.keys = keys;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (String key : keys) {
                if (!deaths.containsKey(key) && !sim.getRoster().isAliveById(world.unit(key))) deaths.put(key, tick);
            }
            List<WarheadStrike> live = sim.warships().strikes();
            for (WarheadStrike s : live) {
                if (strikes.put(s, Boolean.TRUE) != null) continue;
                String sheaf = s.releaseX + "," + s.releaseY;
                if (!sides.containsKey(sheaf)) sheaves++;
                int[] n = sides.computeIfAbsent(sheaf, k -> new int[2]);
                warheads++;
                BloomPath p = s.path();
                float chord = (float) Math.hypot(s.aimX - s.releaseX, s.aimY - s.releaseY);
                float lx = -(s.aimY - s.releaseY) / chord;
                float ly = (s.aimX - s.releaseX) / chord;
                float widest = 0f;
                float signed = 0f;
                for (float t = 0f; t <= p.totalSeconds(); t += 0.05f) {
                    float off = (p.x(t) - s.releaseX) * lx + (p.y(t) - s.releaseY) * ly;
                    if (Math.abs(off) > Math.abs(signed)) signed = off;
                    widest = Math.max(widest, Math.abs(off));
                }
                n[signed >= 0f ? 0 : 1]++;
                float swing = widest / chord;
                swingSum += swing;
                swingMin = Math.min(swingMin, swing);
                float t1 = p.totalSeconds();
                float heading = AirBody.facingToward(p.x(t1) - p.x(t1 - 0.05f), p.y(t1) - p.y(t1 - 0.05f));
                float line = AirBody.facingToward(s.aimX - SHIP_X, s.aimY - SHIP_Y);
                float d = Math.abs(heading - line) % 360f;
                offSum += d > 180f ? 360f - d : d;
            }
            for (WarheadStrike s : strikes.keySet()) {
                if (landed.containsKey(s) || containsIdentity(live, s)) continue;
                landed.put(s, Boolean.TRUE);
                if (!s.isIntercepted()) landedDamage += s.detonation().damage;
            }
        }

        int sheavesWithBothSides() {
            int n = 0;
            for (int[] s : sides.values()) if (s[0] > 0 && s[1] > 0) n++;
            return n;
        }

        float minSwing() { return swingMin == Float.MAX_VALUE ? 0f : swingMin; }

        float meanSwing() { return warheads == 0 ? 0f : swingSum / warheads; }

        float meanArrivalOffDegrees() { return warheads == 0 ? 0f : offSum / warheads; }

        float inFlightDamage(BattleSimulation sim) {
            float d = 0f;
            for (WarheadStrike s : sim.warships().strikes()) if (!s.isIntercepted()) d += s.detonation().damage;
            return d;
        }

        private static <T> boolean containsIdentity(List<T> list, T item) {
            for (T t : list) if (t == item) return true;
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------- anti-fighter

    /**
     * A ship with Locusts in every slot, ordered onto a district, while a hostile boat crosses its reach:
     * the missiles built to overwhelm fighters take the aircraft rather than the ground.
     */
    private SceneReport playAntiAir(FrameSink frames) throws Exception {
        targetX = 92f;
        String loop = "locust-anti-air";
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        district(builder);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(SEED);
        Warship ship = shooter("locust", 4, MissilePreset.FLAVOURED, world);
        ship.airFire(AirFireOrder.ALL);
        sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER, 60f, 38f, 150f, 38f, 150f, 38f, 1f, 6);
        SceneRun.of(world)
                .frames(frames, loop, FRAME_EVERY_TICKS, tick -> loop + " / locust / tick " + tick)
                .run(TICKS);
        WarshipAirFire air = sim.warships().airFire();
        int locustAtAir = air.roundsByWeapon().getOrDefault("locust", 0);
        int fired = 0;
        for (Warship.Mount m : ship.mounts()) fired += m.shotsFired;
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("locusts-take-the-aircraft", locustAtAir >= 4,
                locustAtAir + " Locusts were fired at the boat of " + fired + " fired in all"));
        verdicts.add(Verdict.of("the-aircraft-is-the-first-target", air.roundsFired() > 0 && locustAtAir == air.roundsFired(),
                air.roundsFired() + " air rounds, " + locustAtAir + " of them Locusts"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("locustsAtAir", locustAtAir);
        metrics.put("fired", fired);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    // ---------------------------------------------------------------------------------------- Dragonfire

    private SceneReport playDragon(String loop, boolean cover, FrameSink frames) throws Exception {
        targetX = 92f;
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        List<String> keys = district(builder);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(SEED);
        shooter("dragon", 4, MissilePreset.FLAVOURED, world);
        if (cover) {
            sim.addWarship(Warship.fit("onslaught", "onslaught_Standard", Faction.DEFENDER, targetX - 30f, TARGET_Y, 0f));
        }
        DragonTrace trace = new DragonTrace(world, keys);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, FRAME_EVERY_TICKS, tick -> loop + " / dragon / tick " + tick)
                .run(DRAGON_TICKS);
        trace.observe(sim, DRAGON_TICKS);
        MissileFlightSpec.Standoff standoff = VanillaWeapons.get("dragon").flight().standoff();
        float standoffCells = WarshipScale.cells(standoff.standoffPx());
        List<Verdict> verdicts = new ArrayList<>();
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("fired", trace.fired);
        metrics.put("painted", trace.painted);
        metrics.put("beamsLoosed", trace.beams);
        metrics.put("shotDownBeforeTheBeam", trace.shotDown);
        metrics.put("deaths", trace.deaths.size());
        verdicts.add(Verdict.of("dragons-fly", trace.fired >= 2, trace.fired + " Dragonfires fired"));
        if (!cover) {
            verdicts.add(Verdict.of("holds-the-standoff",
                    trace.painted > 0 && Math.abs(trace.meanHover() - standoffCells) <= 1.5f,
                    "they hover " + trace.meanHover() + " cells short of the ground; the spec's standoff is "
                            + standoffCells + " (" + standoff.standoffPx() + " px)"));
            verdicts.add(Verdict.of("paints-before-it-fires",
                    trace.beams > 0 && trace.shortestPaintSeconds >= standoff.paintSeconds() - 0.1f,
                    "the shortest paint was " + trace.shortestPaintSeconds + " s against a spec of " + standoff.paintSeconds()));
            verdicts.add(Verdict.of("beam-lands-its-damage", trace.beams == trace.fired && trace.deaths.size() >= 6,
                    trace.beams + " of " + trace.fired + " beams loosed; " + trace.deaths.size() + " of " + DISTRICT + " dead"));
        } else {
            verdicts.add(Verdict.of("point-defence-can-stop-the-missile-before-the-beam", trace.shotDown >= 1,
                    trace.shotDown + " Dragonfires were shot down while flying out or painting"));
            verdicts.add(Verdict.of("a-loosed-beam-is-never-engaged",
                    trace.beams >= 1 && trace.engagedWhileFiring == 0 && trace.offeredWhileFiring == 0,
                    trace.beams + " beams loosed; point defence aimed at a firing one " + trace.engagedWhileFiring
                            + " times and the ground defences were offered one " + trace.offeredWhileFiring + " times"));
        }
        return new SceneReport(id(), loop, DRAGON_TICKS, verdicts, metrics);
    }

    private static final class DragonTrace implements TickObserver {
        private final SceneWorld world;
        private final List<String> keys;
        final Map<String, Integer> deaths = new LinkedHashMap<>();
        /** Per missile: painted, fired, the tick painting began, and whether it has left the sky. */
        private final Map<WarshipShell, float[]> shells = new IdentityHashMap<>();
        int fired;
        int painted;
        int beams;
        int shotDown;
        int engagedWhileFiring;
        int offeredWhileFiring;
        float shortestPaintSeconds = Float.MAX_VALUE;
        private float hoverSum;

        private DragonTrace(SceneWorld world, List<String> keys) {
            this.world = world;
            this.keys = keys;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (String key : keys) {
                if (!deaths.containsKey(key) && !sim.getRoster().isAliveById(world.unit(key))) deaths.put(key, tick);
            }
            List<WarshipShell> live = sim.warships().shells();
            for (WarshipShell s : live) {
                if (!s.hasBeam()) continue;
                float[] st = shells.get(s);
                if (st == null) {
                    st = new float[]{0f, 0f, -1f, 0f};
                    shells.put(s, st);
                    fired++;
                }
                if (s.phase() == WarshipShell.BeamPhase.PAINT && st[0] == 0f) {
                    st[0] = 1f;
                    st[2] = tick;
                    painted++;
                    hoverSum += (float) Math.hypot(s.x() - s.toX, s.y() - s.toY);
                }
                if (s.phase() == WarshipShell.BeamPhase.FIRE) {
                    if (st[1] == 0f) {
                        st[1] = 1f;
                        beams++;
                        shortestPaintSeconds = Math.min(shortestPaintSeconds, (tick - st[2]) * BattleSimulation.TICK_DT);
                    }
                    if (s.isIntercepted()) engagedWhileFiring++;
                    for (Object body : sim.warships().interceptables()) if (body == s) offeredWhileFiring++;
                    for (WarshipPointDefence.Mark m : sim.warships().pointDefence().marks()) {
                        if (m.freshness() > 0.95f && Math.hypot(m.toX() - s.x(), m.toY() - s.y()) < 0.75f) engagedWhileFiring++;
                    }
                }
            }
            for (Map.Entry<WarshipShell, float[]> e : shells.entrySet()) {
                if (e.getValue()[3] == 1f || contains(live, e.getKey())) continue;
                e.getValue()[3] = 1f;
                if (e.getKey().isIntercepted()) shotDown++;
            }
        }

        float meanHover() { return painted == 0 ? 0f : hoverSum / painted; }

        private static boolean contains(List<WarshipShell> list, WarshipShell s) {
            for (WarshipShell t : list) if (t == s) return true;
            return false;
        }
    }
}
