package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.IdentityHashMap;

/** Catalog weapons, physical flight and actual effects against a production orbiting aircraft. */
public final class MissileAaEffectsScene implements BehaviorScene {
    public static final int TICKS = 210;
    public static final int FPS = 20;
    public static final List<String> WEAPONS = List.of("locust", "swarmer", "proximity");
    private final JSONArray timeline = new JSONArray();

    @Override public String id() { return "missile-aa-effects"; }
    @Override public String label() { return "Missile AA: actual catalog launch, flight and detonation"; }
    @Override public int frameDelayMillis() { return 50; }
    public JSONObject timeline() throws JSONException {
        return new JSONObject().put("fps", FPS).put("durationSeconds", TICKS * BattleSimulation.TICK_DT)
                .put("timeOrigin", "Each independent weapon loop starts at simulation tick zero")
                .put("events", timeline);
    }

    @Override public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        try {
            for (String weapon : WEAPONS) reports.add(playOne(weapon, false, frames));
            for (String weapon : WEAPONS) reports.add(playOne(weapon, true, FrameSink.NONE));
            for (String weapon : List.of("locust", "swarmer")) reports.add(retarget(weapon));
            reports.add(proximityBlast(true));
            reports.add(proximityBlast(false));
        } finally { TurretSlotResolver.useHullSpecs(null); }
        System.out.println("MISSILE_AA_TIMELINE_JSON=" + timeline());
        return reports;
    }

    private SceneReport playOne(String weapon, boolean friendly, FrameSink frames) throws Exception {
        String loop = weapon + (friendly ? "-friendly-control" : "");
        var world = SceneBuilder.openGround(80, 80).seed(20261015).missionCompletion(false).build();
        try (BattleSimulation sim = world.sim()) {
            long gun = sim.spawn(MapTurret.create("aa-" + weapon, Faction.MARINE,
                    "structure.aa-" + weapon, 40, 25));
            long craft = sim.spawnSortie(ShuttleType.AEROSHUTTLE,
                    friendly ? Faction.MARINE : Faction.DEFENDER,
                    40, 25, 40, 41, 78, 41, 0);
            var body = sim.world().kinematics(craft);
            body.facingDegrees = -90;
            body.vx = GunshipOrbit.ORBIT_SPEED;
            var mission = sim.world().mission(craft);
            GunshipOrbit.begin(mission, body.x, body.y, body.facingDegrees);
            mission.state = ShuttleState.ORBIT;
            // Named fixture durability lets complete salvos remain visible; weapon damage is untouched.
            sim.world().setMaxHp(craft, 20000);
            sim.world().setHp(craft, 20000);
            sim.world().setAltitudeT(craft, 1);
            float initialHp = sim.world().hp(craft);
            float initialX = body.x, initialY = body.y;
            Map<String, Number> metrics = new LinkedHashMap<>();
            int launches = 0, arrivals = 0, flameouts = 0, movingSamples = 0, poweredSamples = 0;
            int lastFrame = -1, firstLaunch = -1, firstDamage = -1;
            frames.frame(loop, sim, 0, weapon + " / t 0.00s / ready");
            lastFrame = 0;
            for (int tick = 0; tick < TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                int elapsedTicks = tick + 1;
                for (ShotEvent shot : sim.getShotsThisFrame()) if (shot.shooterId == gun) {
                    event(loop, "launch", elapsedTicks, shot);
                    metrics.put("launch" + launches + "Tick", elapsedTicks);
                    if (firstLaunch < 0) firstLaunch = elapsedTicks;
                    launches++;
                }
                for (ShotEvent shot : sim.getShotsExpiredThisFrame()) if (shot.shooterId == gun) {
                    if (shot.impacts()) { arrivals++; event(loop, "impact", elapsedTicks, shot); }
                    else { flameouts++; event(loop, "flameout", elapsedTicks, shot); }
                }
                for (ShotEvent shot : sim.getActiveShots()) if (shot.shooterId == gun && shot.guidedFlight != null) {
                    if (shot.guidedFlight.powered()) poweredSamples++;
                }
                if (body.speed() > .1f) movingSamples++;
                if (firstDamage < 0 && sim.world().hp(craft) < initialHp) firstDamage = elapsedTicks;
                frames.advanced(loop, sim, elapsedTicks);
                int frame = Math.round(elapsedTicks * FPS / 30f);
                if (frame > lastFrame && frame < TICKS * FPS / 30) {
                    frames.frame(loop, sim, elapsedTicks, String.format(Locale.ROOT,
                            "%s / t %.2fs / %d launched / %d detonations", weapon,
                            elapsedTicks * BattleSimulation.TICK_DT, launches, arrivals));
                    lastFrame = frame;
                }
            }
            float damage = initialHp - sim.world().hp(craft);
            metrics.put("launches", launches); metrics.put("detonations", arrivals);
            metrics.put("flameouts", flameouts); metrics.put("poweredFlightSamples", poweredSamples);
            metrics.put("aircraftDamage", damage); metrics.put("movingSamples", movingSamples);
            metrics.put("aircraftDisplacement", Math.hypot(body.x - initialX, body.y - initialY));
            metrics.put("fixtureAircraftHp", initialHp);
            metrics.put("launcherSurvives", sim.world().isAlive(gun) ? 1 : 0);
            metrics.put("firstLaunchTick", firstLaunch); metrics.put("firstDamageTick", firstDamage);
            var definition = TurretCatalogRegistry.requireStructure("structure.aa-" + weapon).mount.weapon;
            boolean salvo = true;
            if (!friendly && !weapon.equals("proximity")) {
                int expected = weapon.equals("locust") ? 30 : 4;
                int spacing = weapon.equals("locust") ? 3 : 6;
                salvo = launches >= expected;
                for (int release = 1; release < Math.min(launches, expected); release++) {
                    salvo &= metrics.get("launch" + release + "Tick").intValue()
                            - metrics.get("launch" + (release-1) + "Tick").intValue() == spacing;
                }
                if (launches > expected) salvo &= metrics.get("launch" + expected + "Tick").intValue()
                        - metrics.get("launch" + (expected-1) + "Tick").intValue() == 150;
            }
            return new SceneReport(id(), loop, TICKS, List.of(
                    Verdict.of("aircraft-flies", movingSamples > TICKS / 2, "moving ticks=" + movingSamples),
                    Verdict.of(friendly ? "friendly-aircraft-not-targeted" : "catalog-launches-at-hostile-aircraft",
                            friendly ? launches == 0 : launches > 0, "launches=" + launches),
                    Verdict.of(friendly ? "friendly-control-undamaged" : "physical-arrival-before-damage",
                            friendly ? damage == 0 : firstDamage < 0 || firstDamage > firstLaunch,
                            "first launch=" + firstLaunch + ", first damage=" + firstDamage + ", damage=" + damage),
                    Verdict.of("proximity-has-no-powered-flight", !weapon.equals("proximity") || poweredSamples == 0,
                            "powered samples=" + poweredSamples),
                    Verdict.of("native-family-salvo-and-reload", salvo,
                            "launch tick sequence=" + metrics.entrySet().stream().filter(e -> e.getKey().startsWith("launch")).toList()),
                    Verdict.of("native-guided-family-identity", weapon.equals("proximity")
                                    || definition.damage == (weapon.equals("locust") ? 200 : 100)
                                    && definition.range == (weapon.equals("locust") ? 70 : 50)
                                    && definition.burstCount == (weapon.equals("locust") ? 30 : 4),
                            "damage=" + definition.damage + ", range=" + definition.range + ", salvo=" + definition.burstCount),
                    Verdict.of("launcher-survives-standoff",sim.world().isAlive(gun),"launcher remains alive at 16-cell orbit"),
                    Verdict.of("hostile-aircraft-takes-flight-damage",friendly || damage>0,"aircraft damage="+damage)), metrics);
        }
    }

    private SceneReport retarget(String weapon) throws Exception {
        var world = SceneBuilder.openGround(80,80).seed(20261015).missionCompletion(false).build();
        try (BattleSimulation sim = world.sim()) {
            long gun = sim.spawn(MapTurret.create("retarget-"+weapon,Faction.MARINE,"structure.aa-"+weapon,40,25));
            long original = orbit(sim,Faction.DEFENDER,40,45);
            Map<ShotEvent,Long> previous = new IdentityHashMap<>();
            int switches=0;
            boolean newcomer=false;
            for (int tick=0;tick<90;tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                for (ShotEvent shot : sim.getActiveShots()) if (shot.shooterId == gun && shot.guidedFlight != null) {
                    Long old=previous.put(shot,shot.flightTargetId);
                    if (old != null && old != 0 && shot.flightTargetId != 0 && old != shot.flightTargetId
                            && sim.world().isAlive(old) && !sim.world().mission(old).destroyed) switches++;
                    if (!newcomer && tick>=3) { orbit(sim,Faction.DEFENDER,40,30); newcomer=true; }
                }
            }
            return new SceneReport(id(),weapon+"-retarget",90,List.of(
                    Verdict.of("retargets-while-prior-aircraft-lives",switches>0,"live target changes="+switches)),
                    Map.of("livingTargetSwitches",switches,"originalSurvives",sim.world().isAlive(original)?1:0));
        }
    }

    private static long orbit(BattleSimulation sim,Faction faction,float x,float y) {
        long craft=sim.spawnSortie(ShuttleType.AEROSHUTTLE,faction,x,y-16,x,y,78,y,0);
        var body=sim.world().kinematics(craft);
        body.facingDegrees=-90; body.vx=4;
        var mission=sim.world().mission(craft);
        GunshipOrbit.begin(mission,x,y,-90); mission.state=ShuttleState.ORBIT;
        sim.world().setMaxHp(craft,20000); sim.world().setHp(craft,20000); sim.world().setAltitudeT(craft,1);
        return craft;
    }

    private SceneReport proximityBlast(boolean armed) throws Exception {
        var world=SceneBuilder.openGround(80,80).seed(20261015).missionCompletion(false).build();
        try (BattleSimulation sim=world.sim()) {
            if (armed) sim.spawn(MapTurret.create("blast-proof",Faction.MARINE,"structure.aa-proximity",40,25));
            long hostile=orbit(sim,Faction.DEFENDER,40,41);
            long friendly=orbit(sim,Faction.MARINE,40.5f,41);
            for (int tick=0;tick<TICKS;tick++) sim.advance(BattleSimulation.TICK_DT);
            float hostileDamage=20000-sim.world().hp(hostile);
            float friendlyDamage=20000-sim.world().hp(friendly);
            return new SceneReport(id(),armed?"proximity-friendly-blast":"proximity-no-launcher-control",TICKS,
                    List.of(Verdict.of(armed?"nearby-friendly-aircraft-takes-blast":"unarmed-pair-undamaged",
                            armed?hostileDamage>0 && friendlyDamage>0:hostileDamage==0 && friendlyDamage==0,
                            "hostile damage="+hostileDamage+", adjacent friendly damage="+friendlyDamage)),
                    Map.of("hostileDamage",hostileDamage,"friendlyDamage",friendlyDamage));
        }
    }

    private void event(String weapon, String kind, int tick, ShotEvent shot) throws JSONException {
        timeline.put(new JSONObject().put("weapon", weapon).put("kind", kind)
                .put("tick", tick).put("seconds", tick * BattleSimulation.TICK_DT)
                .put("soundId", kind.equals("launch") ? shot.turretStructureDef.mount.weapon.fireSoundId
                        : kind.equals("impact") ? shot.turretStructureDef.mount.weapon.impactSoundId : JSONObject.NULL)
                .put("x", kind.equals("launch") ? shot.fromX : shot.toX)
                .put("y", kind.equals("launch") ? shot.fromY : shot.toY));
    }
}
