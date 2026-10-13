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
            for (int tick = 0; tick < 3000; tick++) {
                radio.advance(BattleSimulation.TICK_DT, log.events(), false, false);
                for (var shot : sim.getShotsThisFrame()) if (guns.contains(shot.shooterId)) {
                    launches++;
                    firingBatteries.add(shot.shooterId);
                    if (firstLaunch < 0) firstLaunch = tick;
                }
                for (var shot : sim.getActiveShots()) if (shot.guidedFlight != null && shot.guidedFlight.z > .5f) curvedSamples++;
                if (firstDamage < 0 && (mission.destroyed || sim.world().isAlive(aircraft)
                        && sim.world().hp(aircraft) < initialHp)) firstDamage = tick;
                if (tick % 12 == 0 && tick < 1800) frames.frame(loop, sim, tick,
                        "Thunder Run / " + loop + " / missile launches " + launches + " / " + mission.state);
                frames.advanced(loop, sim, tick);
                sim.advance(BattleSimulation.TICK_DT);
                pass.tick(sim);
            }
            var metrics = new LinkedHashMap<String, Number>(playback);
            metrics.putAll(Map.of("launches", launches, "guidedSamples", curvedSamples,
                    "firstDamageTick", firstDamage, "firingBatteries", firingBatteries.size()));
            return new SceneReport(id(), loop, 3000, List.of(
                    Verdict.of("batteries-on-inaccessible-summits", hillMountsIntact(sim),
                            "Full summit footprints remain natural-solid and impassable, including after battery destruction"),
                    Verdict.of(cleared ? "clearing-preserves-kestrel" : "missiles-can-down-kestrel",
                            cleared ? !mission.destroyed && launches == 0 : mission.destroyed && launches > 0 && curvedSamples > 0,
                            "destroyed=" + mission.destroyed + ", launches=" + launches + ", guided samples=" + curvedSamples),
                    Verdict.of("no-damage-at-launch", cleared || firstDamage > firstLaunch && firstLaunch >= 0,
                            "first launch=" + firstLaunch + ", first damage=" + firstDamage),
                    Verdict.of("both-batteries-cover-the-pass", cleared || eastern || firingBatteries.size() == 2,
                            "Firing batteries=" + firingBatteries.size()),
                    Verdict.of("radio-follows-the-encounter", radioMatches(log, cleared), log.events().toString()),
                    Verdict.of("recorded-radio-plays", cleared
                            ? playback.containsKey("radioStart.wayline_thunder_run_gunship_on_station")
                            : playback.containsKey("radioStart.wayline_thunder_run_gunship_missile_warning")
                                && playback.containsKey("radioStart.wayline_thunder_run_gunship_lost"),
                            "Production narration queue starts: " + playback),
                    Verdict.of("survivor-joins-station", !cleared || mission.gunshipIngress.complete()
                            && mission.state == ShuttleState.ORBIT, "state=" + mission.state)),
                    metrics);
        }
    }

    private static boolean radioMatches(StoryBeatLog log, boolean cleared) {
        var beats = log.events().stream().map(StoryBeatLog.Event::id).toList();
        String prefix = "supply_route.thunder_run.gunship_";
        if (!beats.contains(prefix + "inbound") || !beats.contains(prefix + "pass_committed")) return false;
        if (cleared) return !beats.contains(prefix + "missile_warning") && !beats.contains(prefix + "hit")
                && !beats.contains(prefix + "lost") && beats.contains(prefix + "on_station");
        return beats.indexOf(prefix + "missile_warning") > beats.indexOf(prefix + "pass_committed")
                && beats.indexOf(prefix + "hit") > beats.indexOf(prefix + "missile_warning")
                && beats.indexOf(prefix + "lost") > beats.indexOf(prefix + "hit");
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
