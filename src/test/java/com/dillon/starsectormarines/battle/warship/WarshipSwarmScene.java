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
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An Onslaught's missile mounts firing on a district of defenders, recorded the ways the swarm
 * is meant to be read: a Hurricane cluster under each preset, a Sabot shotgun, the same Hurricane
 * cluster against a defender ship's point defence, a rocket rack's volley, a Squall barrage rolling
 * out, a Hammer split by the cinematic preset, and a ship firing Locusts from every slot until the
 * caps bite.
 *
 * <p>The owner's reference is a cluster munition that launches and, on reaching an area, splits
 * into submunitions that hit at random for a large burst. The questions are those the doc
 * (<code>warship-missiles.md</code>) promises and none of them is answerable by counting rounds:
 * that the bodies number what the spec says, that they arrive across a drumroll rather than as one
 * thump, that however many they are the weapon's damage and area are conserved, that the footprint
 * is wide, that point defence kills bodies individually and a carrier shot down loses its payload,
 * and that the caps fuse rather than delete.
 */
public final class WarshipSwarmScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 90;
    private static final int TICKS = 900;
    /** A frame every this many ticks: the recording holds every frame of a loop in memory. */
    private static final int FRAME_EVERY_TICKS = 6;
    private static final float SHIP_X = 20f;
    private static final float SHIP_Y = 45f;
    private static final float SHIP_HEADING = -90f;
    private static final float FAR_TARGET_X = 92f;
    private static final float NEAR_TARGET_X = 62f;
    /** Where the district is this loop: the long-range missiles take the far one, the short-range ones the near one. */
    private float targetX = FAR_TARGET_X;
    private static final float TARGET_Y = 45f;
    private static final float AREA_RADIUS = 6f;
    /** A picket flies in the carriers' lane, well short of where they split. */
    private static final float PICKET_X = 38f;
    private static final int DISTRICT = 44;
    private static final float DISTRICT_RADIUS = 15f;
    private static final String[] SLOTS = {"WS 021", "WS 022", "WS 023", "WS 024"};
    private static final long SEED = 20261012L;

    /** What stands in the way of the swarm: nothing, an umbrella of point defence over the district, or a picket in the carriers' lane. */
    private enum Cover { NONE, UMBRELLA, PICKET }

    /** Deaths in the unopposed four-mount Hurricane loop, for the defended loops to be compared with. */
    private int volleyDeaths = -1;
    private float faithfulWindow = Float.NaN;

    @Override public String id() { return "warship-swarm"; }

    @Override public String label() {
        return "Warship swarm burst: carriers that split into scattering, staggered warheads, rack volleys, point defence and the caps";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        targetX = FAR_TARGET_X;
        if (wanted("hurricane-faithful")) reports.add(playCluster("hurricane-faithful", "hurricane", 2, MissilePreset.FAITHFUL, Cover.NONE, frames));
        if (wanted("hurricane")) reports.add(playCluster("hurricane", "hurricane", 2, MissilePreset.FLAVOURED, Cover.NONE, frames));
        if (wanted("hurricane-cinematic")) reports.add(playCluster("hurricane-cinematic", "hurricane", 2, MissilePreset.CINEMATIC, Cover.NONE, frames));
        // The control for the point-defence loops is a number, not a picture.
        if (wanted("hurricane-volley")) reports.add(playCluster("hurricane-volley", "hurricane", 4, MissilePreset.FLAVOURED, Cover.NONE, FrameSink.NONE));
        if (wanted("hurricane-umbrella")) reports.add(playCluster("hurricane-umbrella", "hurricane", 4, MissilePreset.FLAVOURED, Cover.UMBRELLA, frames));
        if (wanted("hurricane-picket")) reports.add(playCluster("hurricane-picket", "hurricane", 4, MissilePreset.FLAVOURED, Cover.PICKET, frames));
        targetX = NEAR_TARGET_X;
        if (wanted("sabot")) reports.add(playCluster("sabot", "sabotpod", 2, MissilePreset.FLAVOURED, Cover.NONE, frames));
        if (wanted("rack-annihilator")) reports.add(playRack("rack-annihilator", "annihilator", 4, 400, frames));
        if (wanted("hammer-cinematic")) reports.add(playHammer(frames));
        targetX = FAR_TARGET_X;
        if (wanted("rack-squall")) reports.add(playRack("rack-squall", "squall", 2, 540, frames));
        if (wanted("rack-cap")) reports.add(playRack("rack-cap", "locust", -1, 600, FrameSink.NONE));
        return reports;
    }

    /** Whether a loop is played: all of them, or those named by the comma-separated {@code battle.swarm.loops}, so one can be recorded alone. */
    private static boolean wanted(String loop) {
        String only = System.getProperty("battle.swarm.loops");
        return only == null || only.isBlank() || List.of(only.split(",")).contains(loop);
    }

    /** The Onslaught hull with {@code weaponId} in its first {@code mounts} missile slots, or every slot when {@code mounts} is negative. */
    private static WarshipLoadout loadout(String weaponId, int mounts) {
        return new WarshipLoadout() {
            @Override public String hullId() { return "onslaught"; }

            @Override public String weaponInSlot(String slotId) {
                if (mounts < 0) return weaponId;
                for (int i = 0; i < mounts; i++) if (SLOTS[i].equals(slotId)) return weaponId;
                return null;
            }
        };
    }

    /** A district of defenders: a golden-angle spiral of distinct cells about the target. */
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
        // Something on the shooter's side's far corner so the battle is not decided before the swarm lands.
        builder.unit("anchor", Faction.MARINE, UnitType.MARINE, 4, 4);
        return keys;
    }

    private SceneReport playCluster(String loop, String weaponId, int mounts, MissilePreset preset,
                                    Cover cover, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        List<String> keys = district(builder);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(SEED);
        Warship ship = Warship.fit(loadout(weaponId, mounts), Faction.MARINE, SHIP_X, SHIP_Y, SHIP_HEADING);
        ship.setMissilePreset(preset);
        ship.order(new WarshipOrder.FireOnArea(targetX, TARGET_Y, AREA_RADIUS, true));
        sim.addWarship(ship);
        if (cover != Cover.NONE) {
            float guardX = cover == Cover.UMBRELLA ? targetX - 6f : PICKET_X;
            sim.addWarship(Warship.fit("onslaught", "onslaught_Standard", Faction.DEFENDER, guardX, TARGET_Y, 0f));
        }
        Trace trace = new Trace(world, keys);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, FRAME_EVERY_TICKS, tick -> loop + " / " + weaponId + " / " + preset + " / tick " + tick)
                .run(TICKS);
        return clusterReport(loop, weaponId, preset, cover, trace, sim, ship);
    }

    private SceneReport clusterReport(String loop, String weaponId, MissilePreset preset, Cover cover,
                                      Trace trace, BattleSimulation sim, Warship ship) {
        // The observers see the world before each tick; this is the look at where the last tick left it.
        trace.observe(sim, TICKS);
        VanillaWeapon weapon = VanillaWeapons.get(weaponId);
        MissileFlightSpec.Mirv mirv = weapon.flight().mirv();
        List<Verdict> verdicts = new ArrayList<>();
        int fired = 0;
        for (Warship.Mount m : ship.mounts()) fired += m.shotsFired;
        verdicts.add(Verdict.of("carriers-fly", fired >= 2 && trace.carriers >= 2,
                fired + " carriers fired, " + trace.carriers + " seen in flight"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("carriers", trace.carriers);
        metrics.put("carriersShotDown", trace.carriersShot);
        metrics.put("sheaves", trace.groups.size());
        metrics.put("warheadsReleased", trace.warheadsReleased);
        metrics.put("warheadsLanded", trace.warheadsLanded);
        metrics.put("warheadsShotDown", trace.warheadsShot);
        metrics.put("deaths", trace.deaths.size());
        metrics.put("damageDelivered", trace.damageDelivered);

        boolean unopposed = cover == Cover.NONE;
        int resolved = trace.carriers - liveCarriers(sim);
        if (unopposed) {
            int expectedBodies = trace.groups.size() * mirv.count();
            verdicts.add(Verdict.of("bodies-as-designed",
                    !trace.groups.isEmpty() && trace.warheadsReleased == expectedBodies && trace.groupsOfSize(mirv.count())
                            && trace.groups.size() == resolved,
                    trace.groups.size() + " sheaves released " + trace.warheadsReleased + " warheads; the spec is "
                            + mirv.count() + " each (" + expectedBodies + ")"));
            // Everything the carriers carried is accounted for: landed, or still in the air when the loop ends.
            float payload = trace.groups.size() * mirv.totalDamage();
            float accounted = trace.damageDelivered + trace.inFlightDamage(sim);
            verdicts.add(Verdict.of("damage-budget-conserved",
                    payload > 0f && Math.abs(accounted - payload) <= payload * 0.01f
                            && trace.landedStrikeClockErrorTicks <= 3,
                    accounted + " landed or in the air against " + payload + " in " + trace.groups.size()
                            + " payloads of " + mirv.totalDamage() + "; worst detonation clock error "
                            + trace.landedStrikeClockErrorTicks + " ticks"));
            float min = preset.arrivalMinSeconds();
            float max = preset.arrivalMaxSeconds();
            float shortestSpread = trace.shortestSpread();
            if (preset == MissilePreset.FAITHFUL) {
                faithfulWindow = trace.longestSpread();
                verdicts.add(Verdict.of("window-is-the-specs-stagger", trace.longestSpread() <= 2.0f,
                        "faithful sheaves land inside " + trace.longestSpread() + " s"));
                verdicts.add(Verdict.of("no-telegraph-when-faithful", trace.footprintsSeen == 0,
                        trace.footprintsSeen + " footprint rings on faithful carriers"));
            } else {
                // The stagger is on top of each warhead's own travel, so a sheaf spreads at least the window's width.
                boolean staggered = shortestSpread >= (weaponId.startsWith("sabot") ? 0.15f : (max - min) * 0.6f);
                verdicts.add(Verdict.of("arrivals-staggered", staggered,
                        "each sheaf's warheads land across at least " + shortestSpread + " s (a stagger of " + min + " to " + max
                                + " s after their own travel; the sheaves run from " + trace.earliestArrival() + " to "
                                + trace.latestArrival() + " s after the split)"));
                if (!Float.isNaN(faithfulWindow) && loop.equals("hurricane")) {
                    verdicts.add(Verdict.of("drumroll-longer-than-faithful", trace.longestSpread() > faithfulWindow + 0.5f,
                            "flavoured spread " + trace.longestSpread() + " s against faithful " + faithfulWindow + " s"));
                }
                verdicts.add(Verdict.of("telegraph-ring", trace.footprintsSeen >= trace.groups.size() && trace.groups.size() > 0,
                        trace.footprintsSeen + " carriers wore a footprint ring for " + trace.groups.size() + " sheaves"));
            }
            float blast = WarshipScale.cells(mirv.damageRadiusPx());
            verdicts.add(Verdict.of("footprint-wide", trace.narrowestSheaf() >= (weaponId.startsWith("sabot") ? 2f : 3.5f) * blast,
                    "the narrowest sheaf spans " + trace.narrowestSheaf() + " cells; one warhead's blast is " + blast));
            verdicts.add(Verdict.of("split-is-heard-and-seen", trace.splitCues >= trace.groups.size() && trace.splitEvents >= trace.groups.size(),
                    trace.splitEvents + " split flashes and " + trace.splitCues + " split cues for " + trace.groups.size() + " sheaves"));
            verdicts.add(Verdict.of("kills-the-district", trace.deaths.size() >= 8,
                    trace.deaths.size() + " of " + DISTRICT + " defenders dead"));
            if (loop.equals("hurricane-volley")) volleyDeaths = trace.deaths.size();
        } else {
            int engagements = sim.warships().pointDefence().engagements();
            int potential = resolved * mirv.count();
            int lost = trace.carriersShot * mirv.count() + trace.warheadsShot;
            if (cover == Cover.UMBRELLA) {
                verdicts.add(Verdict.of("bodies-are-individually-killable", trace.warheadsShot >= 1,
                        trace.warheadsShot + " released warheads were shot down in flight by point defence over the district, "
                                + engagements + " engagements in all"));
            } else {
                verdicts.add(Verdict.of("carrier-kill-loses-payload",
                        trace.carriersShot >= 1 && trace.groups.size() == resolved - trace.carriersShot,
                        trace.carriersShot + " of " + trace.carriers + " carriers were shot down before the split and released nothing ("
                                + trace.groups.size() + " sheaves came from the rest)"));
            }
            verdicts.add(Verdict.of("pd-takes-a-measurable-share", potential > 0 && lost >= potential * 0.10f,
                    lost + " of " + potential + " warheads' worth of payload never landed ("
                            + Math.round(100f * lost / Math.max(1, potential)) + "%)"));
            verdicts.add(Verdict.of("not-a-wall", trace.warheadsLanded >= 1,
                    trace.warheadsLanded + " warheads still got through"));
            verdicts.add(Verdict.of("fewer-deaths-than-unopposed", volleyDeaths < 0 || trace.deaths.size() < volleyDeaths,
                    trace.deaths.size() + " defenders dead against " + volleyDeaths + " with no point defence"));
            metrics.put("pdEngagements", engagements);
            metrics.put("payloadLostShare", Math.round(100f * lost / Math.max(1, potential)));
        }
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static int liveCarriers(BattleSimulation sim) {
        int n = 0;
        for (WarshipShell s : sim.warships().shells()) if (s.carrier()) n++;
        return n;
    }

    private SceneReport playRack(String loop, String weaponId, int mounts, int ticks, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        List<String> keys = district(builder);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(SEED);
        Warship ship = Warship.fit(loadout(weaponId, mounts), Faction.MARINE, SHIP_X, SHIP_Y, SHIP_HEADING);
        ship.order(new WarshipOrder.FireOnArea(targetX, TARGET_Y, AREA_RADIUS, true));
        sim.addWarship(ship);
        RackTrace trace = new RackTrace(world, keys, ship);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, FRAME_EVERY_TICKS, tick -> loop + " / " + weaponId + " / tick " + tick)
                .run(ticks);
        return rackReport(loop, weaponId, ticks, trace, sim, ship);
    }

    private SceneReport rackReport(String loop, String weaponId, int ticks, RackTrace trace, BattleSimulation sim, Warship ship) {
        trace.observe(sim, ticks);
        VanillaWeapon weapon = VanillaWeapons.get(weaponId);
        List<Verdict> verdicts = new ArrayList<>();
        int fired = 0;
        for (Warship.Mount m : ship.mounts()) if (!m.pointDefence()) fired += m.shotsFired;
        float firedDamage = fired * weapon.damagePerShot();
        float accounted = trace.landedDamage + trace.inFlightDamage(sim);
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("fired", fired);
        metrics.put("maxLiveBodies", trace.maxLive);
        metrics.put("fusedRounds", sim.warships().fusedRounds());
        metrics.put("deaths", trace.deaths.size());
        metrics.put("firedDamage", firedDamage);
        metrics.put("accountedDamage", accounted);
        verdicts.add(Verdict.of("rounds-fly", fired >= 10 && trace.shells > 0,
                fired + " rounds fired, " + trace.shells + " bodies seen in flight"));
        verdicts.add(Verdict.of("damage-budget-conserved",
                firedDamage > 0f && Math.abs(accounted - firedDamage) <= firedDamage * 0.002f,
                accounted + " damage landed or in the air against " + firedDamage + " fired (" + fired + " x "
                        + weapon.damagePerShot() + "), with " + sim.warships().fusedRounds() + " rounds fused"));
        verdicts.add(Verdict.of("landing-clock-preserved", trace.clockErrorTicks <= 3 && trace.landed > 0,
                "the worst gap between a body's committed flight time and its observed one: "
                        + trace.clockErrorTicks + " ticks over " + trace.landed + " landings"));
        if (loop.equals("rack-cap")) {
            verdicts.add(Verdict.of("bodies-capped", trace.maxLive <= SwarmBudget.PER_SHIP && trace.maxLive >= SwarmBudget.PER_SHIP - 40,
                    "at most " + trace.maxLive + " bodies in the air at once against a cap of " + SwarmBudget.PER_SHIP
                            + "; " + fired + " rounds fired"));
            verdicts.add(Verdict.of("excess-is-fused-not-deleted", sim.warships().fusedRounds() > 0
                            && Math.abs(accounted - firedDamage) <= firedDamage * 0.002f,
                    sim.warships().fusedRounds() + " rounds were folded into bodies already in the air, and every point of damage was kept"));
        } else {
            verdicts.add(Verdict.of("volley-scattered-over-a-footprint",
                    trace.widestVolley() >= 2.5f * WarshipScale.cells(weapon.damageRadiusPx()),
                    "the widest volley's rounds land across " + trace.widestVolley() + " cells; one blast is "
                            + WarshipScale.cells(weapon.damageRadiusPx())));
            verdicts.add(Verdict.of("telegraph-ring", trace.footprintsSeen > 0,
                    trace.footprintsSeen + " volleys wore a footprint ring"));
            if (loop.equals("rack-squall")) {
                float specSeconds = weapon.burstDelaySeconds() * (weapon.burstSize() - 1);
                verdicts.add(Verdict.of("barrage-rolls-rather-than-trickles",
                        trace.longestVolleySeconds <= 3.5f && trace.bestVolleySize >= weapon.burstSize(),
                        "a " + trace.bestVolleySize + "-round volley leaves the rack in " + trace.longestVolleySeconds
                                + " s; the spec's own cadence would take " + specSeconds + " s"));
            }
            verdicts.add(Verdict.of("kills-the-district", trace.deaths.size() >= 6,
                    trace.deaths.size() + " of " + DISTRICT + " defenders dead"));
        }
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    private SceneReport playHammer(FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        List<String> keys = district(builder);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(SEED);
        Warship ship = Warship.fit(loadout("hammer", 4), Faction.MARINE, SHIP_X, SHIP_Y, SHIP_HEADING);
        ship.setMissilePreset(MissilePreset.CINEMATIC);
        ship.order(new WarshipOrder.FireOnArea(targetX, TARGET_Y, AREA_RADIUS, true));
        sim.addWarship(ship);
        Trace trace = new Trace(world, keys);
        SceneRun.of(world).observe(trace)
                .frames(frames, "hammer-cinematic", FRAME_EVERY_TICKS, tick -> "hammer-cinematic / hammer / CINEMATIC / tick " + tick)
                .run(TICKS);
        trace.observe(sim, TICKS);
        VanillaWeapon hammer = VanillaWeapons.get("hammer");
        List<Verdict> verdicts = new ArrayList<>();
        float blast = WarshipScale.cells(hammer.damageRadiusPx());
        float payload = trace.groups.size() * hammer.damagePerShot();
        float accounted = trace.damageDelivered + trace.inFlightDamage(sim);
        verdicts.add(Verdict.of("hammer-splits-into-a-cluster", !trace.groups.isEmpty() && trace.groupsOfSize(6),
                trace.groups.size() + " Hammers split into " + trace.warheadsReleased + " bodies, six each"));
        verdicts.add(Verdict.of("damage-budget-conserved",
                payload > 0f && Math.abs(accounted - payload) <= payload * 0.01f,
                accounted + " landed or in the air against " + payload + " (" + trace.groups.size() + " x " + hammer.damagePerShot() + ")"));
        verdicts.add(Verdict.of("area-budget-conserved", trace.areaPerSheaf() > 0f
                        && Math.abs(trace.areaPerSheaf() - blast * blast) <= blast * blast * 0.02f,
                "a cluster's blasts cover " + trace.areaPerSheaf() + " cells squared against one Hammer's " + blast * blast));
        verdicts.add(Verdict.of("cluster-lands-as-a-rolling-burst", trace.longestSpread() >= 0.6f,
                "a cluster lands over " + trace.longestSpread() + " s"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("sheaves", trace.groups.size());
        metrics.put("deaths", trace.deaths.size());
        metrics.put("damageDelivered", trace.damageDelivered);
        return new SceneReport(id(), "hammer-cinematic", TICKS, verdicts, metrics);
    }

    // ------------------------------------------------------------------------------------------

    /** One released sheaf: where it split, how many bodies it had, when each lands, and where. */
    private static final class Sheaf {
        final List<WarheadStrike> strikes = new ArrayList<>();
        float widest;
    }

    /** What the cluster loops observe: carriers, sheaves, warheads and their fates, and the dead. */
    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final List<String> keys;
        final Map<String, Integer> deaths = new LinkedHashMap<>();
        private final Map<WarshipShell, Boolean> carrierSeen = new IdentityHashMap<>();
        private final Map<WarheadStrike, int[]> strikeTicks = new IdentityHashMap<>();
        final Map<String, Sheaf> groups = new LinkedHashMap<>();
        int carriers;
        int carriersShot;
        int warheadsReleased;
        int warheadsLanded;
        int warheadsShot;
        float damageDelivered;
        float deliveredFromShotCarriers;
        int landedStrikeClockErrorTicks;
        int footprintsSeen;
        int splitCues;
        int splitEvents;
        private final Set<SplitEvent> splitsSeen = Collections.newSetFromMap(new IdentityHashMap<>());

        private Trace(SceneWorld world, List<String> keys) {
            this.world = world;
            this.keys = keys;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (String key : keys) {
                if (!deaths.containsKey(key) && !sim.getRoster().isAliveById(world.unit(key))) deaths.put(key, tick);
            }
            for (WarshipSoundCue cue : sim.warships().cuesThisFrame()) {
                if (cue.kind() == WarshipSoundCue.Kind.SPLIT) splitCues++;
            }
            for (SplitEvent s : sim.warships().splits()) if (splitsSeen.add(s)) splitEvents++;
            List<WarshipShell> shells = sim.warships().shells();
            for (WarshipShell shell : shells) {
                if (shell.carrier() && !carrierSeen.containsKey(shell)) {
                    carrierSeen.put(shell, Boolean.FALSE);
                    carriers++;
                    if (shell.footprint() != null) footprintsSeen++;
                }
            }
            // A carrier that has left the list without releasing was shot down: its warheads never existed.
            for (Map.Entry<WarshipShell, Boolean> e : carrierSeen.entrySet()) {
                if (e.getValue() || containsIdentity(shells, e.getKey())) continue;
                e.setValue(Boolean.TRUE);
                if (e.getKey().isIntercepted()) carriersShot++;
            }
            for (WarheadStrike strike : sim.warships().strikes()) {
                if (strikeTicks.containsKey(strike)) continue;
                strikeTicks.put(strike, new int[]{tick, -1});
                warheadsReleased++;
                String key = tick + "@" + strike.releaseX + "," + strike.releaseY;
                groups.computeIfAbsent(key, k -> new Sheaf()).strikes.add(strike);
            }
            for (Map.Entry<WarheadStrike, int[]> e : strikeTicks.entrySet()) {
                if (e.getValue()[1] >= 0 || containsIdentity(sim.warships().strikes(), e.getKey())) continue;
                e.getValue()[1] = tick;
                if (e.getKey().isIntercepted()) {
                    warheadsShot++;
                    continue;
                }
                warheadsLanded++;
                damageDelivered += e.getKey().detonation().damage;
                float expected = e.getKey().delaySeconds / BattleSimulation.TICK_DT;
                int error = Math.round(Math.abs((tick - e.getValue()[0]) - expected));
                landedStrikeClockErrorTicks = Math.max(landedStrikeClockErrorTicks, error);
            }
            for (Sheaf g : groups.values()) {
                if (g.widest > 0f) continue;
                for (WarheadStrike a : g.strikes) {
                    for (WarheadStrike b : g.strikes) {
                        g.widest = Math.max(g.widest, (float) Math.hypot(a.aimX - b.aimX, a.aimY - b.aimY));
                    }
                }
            }
        }

        private static <T> boolean containsIdentity(List<T> list, T item) {
            for (T t : list) if (t == item) return true;
            return false;
        }

        float inFlightDamage(BattleSimulation sim) {
            float d = 0f;
            for (WarheadStrike s : sim.warships().strikes()) if (!s.isIntercepted()) d += s.detonation().damage;
            return d;
        }

        boolean groupsOfSize(int n) {
            for (Sheaf g : groups.values()) if (g.strikes.size() != n) return false;
            return true;
        }

        private float spreadOf(Sheaf g) {
            float lo = Float.MAX_VALUE;
            float hi = 0f;
            for (WarheadStrike s : g.strikes) {
                lo = Math.min(lo, s.delaySeconds);
                hi = Math.max(hi, s.delaySeconds);
            }
            return g.strikes.isEmpty() ? 0f : hi - lo;
        }

        float shortestSpread() {
            float s = Float.MAX_VALUE;
            for (Sheaf g : groups.values()) s = Math.min(s, spreadOf(g));
            return s == Float.MAX_VALUE ? 0f : s;
        }

        float longestSpread() {
            float s = 0f;
            for (Sheaf g : groups.values()) s = Math.max(s, spreadOf(g));
            return s;
        }

        float earliestArrival() {
            float s = Float.MAX_VALUE;
            for (Sheaf g : groups.values()) for (WarheadStrike k : g.strikes) s = Math.min(s, k.delaySeconds);
            return s == Float.MAX_VALUE ? 0f : s;
        }

        float latestArrival() {
            float s = 0f;
            for (Sheaf g : groups.values()) for (WarheadStrike k : g.strikes) s = Math.max(s, k.delaySeconds);
            return s;
        }

        float narrowestSheaf() {
            float s = Float.MAX_VALUE;
            for (Sheaf g : groups.values()) s = Math.min(s, g.widest);
            return s == Float.MAX_VALUE ? 0f : s;
        }

        /** The sum of the blast areas of one cluster's bodies, in cells squared. */
        float areaPerSheaf() {
            float best = 0f;
            for (Sheaf g : groups.values()) {
                float area = 0f;
                for (WarheadStrike k : g.strikes) area += k.detonation().aoeRadius * k.detonation().aoeRadius;
                best = Math.max(best, area);
            }
            return best;
        }
    }

    /** What the rack loops observe: every body, its committed clock, its volley, and the dead. */
    private static final class RackTrace implements TickObserver {
        private final SceneWorld world;
        private final List<String> keys;
        private final Warship ship;
        final Map<String, Integer> deaths = new LinkedHashMap<>();
        private final Map<WarshipShell, int[]> seen = new IdentityHashMap<>();
        private final Map<WarshipShell.Footprint, List<WarshipShell>> volleys = new IdentityHashMap<>();
        int shells;
        int landed;
        int clockErrorTicks;
        int maxLive;
        int footprintsSeen;
        float landedDamage;
        float longestVolleySeconds;
        int bestVolleySize;

        private RackTrace(SceneWorld world, List<String> keys, Warship ship) {
            this.world = world;
            this.keys = keys;
            this.ship = ship;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (String key : keys) {
                if (!deaths.containsKey(key) && !sim.getRoster().isAliveById(world.unit(key))) deaths.put(key, tick);
            }
            maxLive = Math.max(maxLive, sim.warships().liveBodies(null));
            List<WarshipShell> live = sim.warships().shells();
            for (WarshipShell s : live) {
                if (seen.containsKey(s)) continue;
                seen.put(s, new int[]{tick, -1, 0});
                shells++;
                WarshipShell.Footprint f = s.footprint();
                if (f != null) {
                    List<WarshipShell> v = volleys.computeIfAbsent(f, k -> new ArrayList<>());
                    if (v.isEmpty()) footprintsSeen++;
                    v.add(s);
                }
            }
            for (Map.Entry<WarshipShell, int[]> e : seen.entrySet()) {
                int[] info = e.getValue();
                if (info[1] >= 0 || containsIdentity(live, e.getKey())) continue;
                info[1] = tick;
                landed++;
                landedDamage += e.getKey().damage();
                // A body that absorbed a fused round flew the same clock; the gap is on its own committed time.
                float expected = e.getKey().flightSeconds / BattleSimulation.TICK_DT;
                clockErrorTicks = Math.max(clockErrorTicks, Math.round(Math.abs((tick - info[0]) - expected)));
            }
            for (List<WarshipShell> v : volleys.values()) {
                int first = Integer.MAX_VALUE;
                int last = 0;
                for (WarshipShell s : v) {
                    first = Math.min(first, seen.get(s)[0]);
                    last = Math.max(last, seen.get(s)[0]);
                }
                if (v.size() >= bestVolleySize) {
                    bestVolleySize = v.size();
                    longestVolleySeconds = Math.max(longestVolleySeconds, (last - first) * BattleSimulation.TICK_DT);
                }
            }
        }

        float inFlightDamage(BattleSimulation sim) {
            float d = 0f;
            for (WarshipShell s : sim.warships().shells()) d += s.damage();
            return d;
        }

        float widestVolley() {
            float widest = 0f;
            for (List<WarshipShell> v : volleys.values()) {
                for (WarshipShell a : v) {
                    for (WarshipShell b : v) widest = Math.max(widest, (float) Math.hypot(a.toX - b.toX, a.toY - b.toY));
                }
            }
            return widest;
        }

        private static <T> boolean containsIdentity(List<T> list, T item) {
            for (T t : list) if (t == item) return true;
            return false;
        }
    }
}
