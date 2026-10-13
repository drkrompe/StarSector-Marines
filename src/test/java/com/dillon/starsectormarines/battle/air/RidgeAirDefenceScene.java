package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.ThunderRunAirPass;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.battle.audio.NarrationPlayer;
import com.dillon.starsectormarines.battle.audio.StoryNarrationPlayer;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;

/** Production ridge geometry with a live aircraft: launch/contact and pre-cleared battery controls. */
public final class RidgeAirDefenceScene implements BehaviorScene {
    @Override public String id() { return "ridge-aa"; }
    @Override public String label() { return "Thunder Run / northern missile batteries"; }
    @Override public int frameDelayMillis() { return 400; }

    @Override public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install(); InstalledWeaponSlots.install(); InstalledVanillaData.install();
        try {
            List<SceneReport> reports = new ArrayList<>();
            for (String loop : List.of("live-batteries", "eastern-battery", "cleared-batteries")) reports.add(playOne(frames, loop));
            return reports;
        } finally { TurretSlotResolver.useHullSpecs(null); }
    }

    private SceneReport playOne(FrameSink frames, String loop) throws Exception {
        boolean cleared = loop.equals("cleared-batteries"), eastern = loop.equals("eastern-battery");
        var profile = new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD);
        var layout = ThunderRunCompound.generate(cleared ? 9302 : 9301, profile);
        var map = layout.map();
        try (var sim = BattleSetup.buildMap(map, List.of(), List.of(), List.of(), 9301).sim()) {
            sim.setMissionCompletionEnabled(false);
            sim.spawn(new EntitySpec("observer", Faction.MARINE, UnitType.MARINE, 21, 75));
            sim.spawn(new EntitySpec("distant", Faction.DEFENDER, UnitType.MILITIA, 290, 90));
            List<Long> guns = new ArrayList<>();
            for (var post : ThunderRunCompound.ANTI_AIR_POSTS) {
                long gun = sim.spawn(MapTurret.create("ridge-" + post.x(), Faction.DEFENDER,
                        TurretCatalogRegistry.RIDGE_AA_STRUCTURE_ID, post.x(), post.y()));
                guns.add(gun);
                if (cleared || eastern && post.equals(ThunderRunCompound.ANTI_AIR_POSTS.get(0))) sim.applyDamage(gun, 10000f, 100f);
            }
            var log = new StoryBeatLog();
            var pass = new ThunderRunAirPass(sim, log);
            var playback = new LinkedHashMap<String, Number>();
            var catalog = WaylineNarration.parse(new JSONObject(Files.readString(Path.of("mod", WaylineNarration.PATH))));
            var radio = new StoryNarrationPlayer(catalog, "story_supply_route_thunder_run",
                    new NarrationPlayer(line -> null), line -> playback.put("radioStart." + line.id(), sim.getSimTickIndex()));
            long aircraft = pass.aircraft();
            var mission = sim.world().mission(aircraft);
            // Keep the production armament: this is a flown ambush, not an invulnerable scripted victim.
            int launches = 0, curvedSamples = 0, firstDamage = -1, firstLaunch = -1;
            var firingBatteries = new HashSet<Long>();
            float initialHp = sim.world().hp(aircraft);
            float withdrawalMinAltitude = 1f;
            var encounter = new LinkedHashMap<String, Number>();
            for (int tick = 0; tick < 3000; tick++) {
                radio.advance(BattleSimulation.TICK_DT, log.events(), false, false);
                for (var shot : sim.getShotsThisFrame()) if (guns.contains(shot.shooterId)) {
                    launches++;
                    firingBatteries.add(shot.shooterId);
                    String battery = "battery" + sim.world().cellX(shot.shooterId);
                    encounter.merge(battery + "Missiles", 1, (a, b) -> a.intValue() + b.intValue());
                    encounter.put(battery + "LastLaunchTick", tick);
                    if (!encounter.containsKey(battery + "LaunchTick")) {
                        position(encounter, battery + "Launch", sim, aircraft, tick);
                        encounter.put(battery + "Range", sim.turretState().structure(shot.shooterId).mount.weapon.range);
                        encounter.put(battery + "LaunchDistance", Math.hypot(
                                sim.world().x(aircraft) - sim.world().x(shot.shooterId),
                                sim.world().y(aircraft) - sim.world().y(shot.shooterId)));
                    }
                    if (firstLaunch < 0) {
                        firstLaunch = tick;
                        position(encounter, "firstLaunch", sim, aircraft, tick);
                    }
                }
                for (var shot : sim.getActiveShots()) if (shot.guidedFlight != null && shot.guidedFlight.z > .5f) curvedSamples++;
                if (firstDamage < 0 && (mission.destroyed || sim.world().isAlive(aircraft)
                        && sim.world().hp(aircraft) < initialHp)) {
                    firstDamage = tick;
                    position(encounter, "firstDamage", sim, aircraft, tick);
                }
                if (mission.destroyed && !encounter.containsKey("lossTick"))
                    position(encounter, "loss", sim, aircraft, tick);
                if (pass.withdrawing() && !encounter.containsKey("evadeTick"))
                    position(encounter, "evade", sim, aircraft, tick);
                if (pass.withdrawing() && !mission.destroyed && mission.state != ShuttleState.GONE)
                    withdrawalMinAltitude = Math.min(withdrawalMinAltitude, sim.world().altitudeT(aircraft));
                if (pass.withdrawing() && !mission.destroyed
                        && tick == encounter.get("evadeTick").intValue() + 30)
                    position(encounter, "evadeAfterOneSecond", sim, aircraft, tick);
                if (tick % 12 == 0 && tick < 1800) frames.frame(loop, sim, tick,
                        "Thunder Run / " + loop + " / missile launches " + launches + " / "
                                + (pass.withdrawing() && mission.state == ShuttleState.INCOMING ? "EVASIVE BREAK" : mission.state));
                frames.advanced(loop, sim, tick);
                sim.advance(BattleSimulation.TICK_DT);
                pass.tick(sim);
            }
            var metrics = new LinkedHashMap<String, Number>(playback);
            metrics.putAll(encounter);
            metrics.put("withdrawalMinAltitude", withdrawalMinAltitude);
            metrics.putAll(Map.of("launches", launches, "guidedSamples", curvedSamples,
                    "firstDamageTick", firstDamage, "firingBatteries", firingBatteries.size()));
            return new SceneReport(id(), loop, 3000, List.of(
                    Verdict.of("batteries-on-inaccessible-summits", hillMountsIntact(sim),
                            "Full summit footprints remain natural-solid and impassable, including after battery destruction"),
                    Verdict.of(cleared ? "clearing-preserves-kestrel" : eastern ? "damaged-kestrel-can-escape" : "missiles-can-down-kestrel",
                            cleared ? !mission.destroyed && launches == 0 : eastern
                                    ? !mission.destroyed && firstDamage >= 0 && mission.state == ShuttleState.GONE
                                    : mission.destroyed && launches > 0 && curvedSamples > 0,
                            "destroyed=" + mission.destroyed + ", launches=" + launches + ", guided samples=" + curvedSamples),
                    Verdict.of("no-damage-at-launch", cleared || firstDamage > firstLaunch && firstLaunch >= 0,
                            "first launch=" + firstLaunch + ", first damage=" + firstDamage),
                    Verdict.of("batteries-reengage-after-the-break", cleared || encounter.entrySet().stream()
                                    .anyMatch(e -> e.getKey().endsWith("Missiles") && e.getValue().intValue() > 2),
                            "Missiles=" + launches + ", firing batteries=" + firingBatteries.size()),
                    Verdict.of("missile-warning-causes-a-flown-break", cleared ? !pass.withdrawing() : flewBreak(encounter),
                            "One second into break: heading=" + encounter.get("evadeAfterOneSecondHeading")
                                    + ", speed=" + encounter.get("evadeAfterOneSecondSpeed")),
                    Verdict.of("withdrawal-does-not-return-to-station", cleared || log.events().stream()
                                    .noneMatch(e -> e.id().endsWith("gunship_on_station")),
                            "state=" + mission.state),
                    Verdict.of("withdrawal-keeps-cruise-altitude", withdrawalMinAltitude >= .99f,
                            "Minimum non-crashing withdrawal altitude=" + withdrawalMinAltitude),
                    Verdict.of("radio-follows-the-encounter", radioMatches(log, cleared, mission.destroyed), log.events().toString()),
                    Verdict.of("recorded-radio-plays", cleared
                            ? playback.containsKey("radioStart.wayline_thunder_run_gunship_on_station")
                            : playback.containsKey("radioStart.wayline_thunder_run_gunship_missile_warning")
                                && playback.containsKey("radioStart.wayline_thunder_run_gunship_" + (mission.destroyed ? "lost" : "returned")),
                            "Production narration queue starts: " + playback),
                    Verdict.of("survivor-joins-station", !cleared || mission.gunshipIngress.complete()
                            && mission.state == ShuttleState.ORBIT, "state=" + mission.state)),
                    metrics);
        }
    }

    private static void position(Map<String, Number> metrics, String event, BattleSimulation sim,
                                 long aircraft, int tick) {
        var body = sim.world().kinematics(aircraft);
        metrics.put(event + "Tick", tick);
        metrics.put(event + "X", body.x);
        metrics.put(event + "Y", body.y);
        metrics.put(event + "Heading", body.facingDegrees);
        metrics.put(event + "Speed", body.speed());
    }

    private static boolean flewBreak(Map<String, Number> metrics) {
        if (!metrics.containsKey("evadeAfterOneSecondHeading")) return false;
        float turn = Math.abs(metrics.get("evadeAfterOneSecondHeading").floatValue()
                - metrics.get("evadeHeading").floatValue());
        return turn >= 20f && turn <= 33f
                && metrics.get("evadeAfterOneSecondSpeed").floatValue() > 4f
                && metrics.get("evadeAfterOneSecondX").floatValue() > metrics.get("evadeX").floatValue()
                && metrics.get("evadeAfterOneSecondY").floatValue() > metrics.get("evadeY").floatValue();
    }

    private static boolean radioMatches(StoryBeatLog log, boolean cleared, boolean lost) {
        var beats = log.events().stream().map(StoryBeatLog.Event::id).toList();
        String prefix = "supply_route.thunder_run.gunship_";
        if (!beats.contains(prefix + "inbound") || !beats.contains(prefix + "pass_committed")) return false;
        if (cleared) return !beats.contains(prefix + "missile_warning") && !beats.contains(prefix + "hit")
                && !beats.contains(prefix + "lost") && beats.contains(prefix + "on_station");
        return beats.indexOf(prefix + "missile_warning") > beats.indexOf(prefix + "pass_committed")
                && beats.indexOf(prefix + "evading") > beats.indexOf(prefix + "missile_warning")
                && beats.indexOf(prefix + "hit") > beats.indexOf(prefix + "missile_warning")
                && beats.indexOf(prefix + (lost ? "lost" : "returned")) > beats.indexOf(prefix + "hit")
                && !beats.contains(prefix + (lost ? "returned" : "lost"));
    }

    private static boolean hillMountsIntact(BattleSimulation sim) {
        for (var post : ThunderRunCompound.ANTI_AIR_POSTS)
            for (int y = post.y() - 1; y <= post.y() + 1; y++) for (int x = post.x() - 1; x <= post.x() + 1; x++) {
                if (sim.getGrid().isWalkable(x, y) || !sim.getGrid().hasTag(x, y, CellTag.NATURAL_SOLID)
                        || !sim.getTopology().terrainSurface().isFullSummit(x, y)) return false;
            }
        return true;
    }
}
