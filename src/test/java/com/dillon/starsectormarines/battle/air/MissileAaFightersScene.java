package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.air.engine.HullKinematics;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.setup.MissileAaTestSetup;
import com.dillon.starsectormarines.battle.setup.MissileAaTestSetup.Flight;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledHullEngines;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Same native battle factory, seed and hulls; only evasion or the batteries are disabled in controls. */
public final class MissileAaFightersScene implements BehaviorScene {
    public static final long VISUAL_SEED = 20261016;
    public static final int TICKS = 3300;
    public static final int FPS = 20;
    private static final int CLIP_TICKS = 300;
    private final JSONArray clipEvents = new JSONArray();
    private int clipStart = -1;

    @Override public String id() { return "missile-aa-fighters"; }
    @Override public String label() { return "Native missile AA test battle / ordinary fighters and evasive flight"; }
    @Override public int frameDelayMillis() { return 50; }
    public JSONObject timeline() throws JSONException {
        return new JSONObject().put("fps", FPS).put("durationSeconds", 10)
                .put("simulationStartTick", clipStart).put("simulationStartSeconds", clipStart / 30d)
                .put("events", clipEvents);
    }

    @Override public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install(); InstalledHullEngines.install(); InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        try {
            for (long seed : new long[]{VISUAL_SEED,VISUAL_SEED+1,VISUAL_SEED+2}) {
                for (String mode : List.of("normal","no-evasion","no-aa"))
                    reports.add(playOne(seed,mode,seed==VISUAL_SEED && mode.equals("normal")?frames:FrameSink.NONE));
            }
        } finally { InstalledHullEngines.uninstall(); TurretSlotResolver.useHullSpecs(null); }
        return reports;
    }

    private SceneReport playOne(long seed,String mode,FrameSink frames) throws Exception {
        String loop=seed+"-"+mode;
        var setup=MissileAaTestSetup.createDetailed(seed,List.of());
        try (BattleSimulation sim=setup.sim()) {
            Map<Long,Flight> flights=new HashMap<>();
            Map<Long,ShuttleMission> missions=new HashMap<>();
            Map<Long,AirBody> bodies=new HashMap<>();
            Map<Long,Float> previousHeading=new HashMap<>();
            Map<Long,Integer> previousSteering=new HashMap<>();
            Map<Long,ShuttleState> previousState=new HashMap<>();
            Map<Long,Integer> previousPasses=new HashMap<>();
            Map<Long,Integer> batteryLanes=new HashMap<>();
            for (int lane=0;lane<setup.batteries().size();lane++) {
                long battery=setup.batteries().get(lane); batteryLanes.put(battery,lane);
                if (mode.equals("no-aa")) sim.applyDamage(battery,100000,100);
            }
            for (Flight flight:setup.flights()) {
                flights.put(flight.id(),flight);
                ShuttleMission mission=sim.world().mission(flight.id());
                mission.missileEvasionEnabled=!mode.equals("no-evasion");
                missions.put(flight.id(),mission);
                AirBody body=sim.world().kinematics(flight.id());
                bodies.put(flight.id(),body);previousHeading.put(flight.id(),body.facingDegrees);
                previousSteering.put(flight.id(),0);
                previousState.put(flight.id(),mission.state);previousPasses.put(flight.id(),mission.passesLeft);
            }
            Map<String,Number> metrics=new LinkedHashMap<>();
            for (FighterProfile profile:List.of(FighterProfile.TALON,FighterProfile.WASP,FighterProfile.THUNDER,FighterProfile.BROADSWORD)) {
                AirHandling actual=profile.flight(),expected=measuredHandling(profile);
                if (Math.abs(actual.maxSpeed()-expected.maxSpeed())>.001
                        || Math.abs(actual.maxTurnRateDegPerSec()-expected.maxTurnRateDegPerSec())>.001)
                    throw new IllegalStateException("Native engine handling required for "+profile+": speed="
                            +actual.maxSpeed()+", turn="+actual.maxTurnRateDegPerSec());
                metrics.put("handling."+profile+".maxSpeed",actual.maxSpeed());
                metrics.put("handling."+profile+".turnRate",actual.maxTurnRateDegPerSec());
                metrics.put("handling."+profile+".measured",1);
            }
            int firstLaunch=-1,lastFrame=-1;
            boolean visual=seed==VISUAL_SEED && mode.equals("normal");
            for (int tick=1;tick<=TICKS;tick++) {
                List<StrikeCandidate> arrivals=new ArrayList<>();
                for (PendingDetonation det:sim.snapshotInflightDetonations())
                    if (det.aerialDelivery && det.remainingTime<=BattleSimulation.TICK_DT+.0001f && flights.containsKey(det.shooterId))
                        arrivals.add(new StrikeCandidate(det.shooterId,det.endpointX,det.endpointY,det.aoeRadius));
                sim.advance(BattleSimulation.TICK_DT);
                for (var release:sim.getOrdnanceReleasesThisFrame())
                    if (release.flightTimeSec()<BattleSimulation.TICK_DT && flights.containsKey(release.sourceId()))
                        arrivals.add(new StrikeCandidate(release.sourceId(),release.toX(),release.toY(),release.radiusCells()));
                for (int owner=0;owner<setup.batteries().size();owner++) {
                    long battery=setup.batteries().get(owner);
                    String key=lane(owner)+".batteryDestroyedTick";
                    if (!sim.world().isAlive(battery) && !metrics.containsKey(key)) {
                        metrics.put(key,mode.equals("no-aa")?0:tick);
                        var position=MissileAaTestSetup.LANES.get(owner);
                        int strikes=0,crashes=0,aaBlasts=0;
                        for (StrikeCandidate candidate:arrivals)
                            if (Math.hypot(candidate.x-position.x(),candidate.y-position.y())<=candidate.radius+1.1f) {
                                strikes++;
                                metrics.put(lane(owner)+".deathCandidateStrikeWave",flights.get(candidate.source).wave());
                                metrics.put(lane(owner)+".deathCandidateStrikeSource",candidate.source);
                            }
                        for (Flight flight:setup.flights()) {
                            ShuttleMission mission=missions.get(flight.id());AirBody body=bodies.get(flight.id());
                            if (mission.destroyed && mission.crashSeconds>=AirCrashMotion.FALL_SECONDS) {
                                float radius=Math.max(4,Math.min(8,flight.profile().targetRadiusCells()+3));
                                if (Math.hypot(body.x-position.x(),body.y-position.y())<=radius+1.1f) {
                                    crashes++;metrics.put(lane(owner)+".deathCandidateCrashWave",flight.wave());
                                    metrics.put(lane(owner)+".deathCandidateCrashSource",flight.id());
                                }
                            }
                        }
                        for (ShotEvent shot:sim.getShotsExpiredThisFrame()) {
                            if (!shot.impacts() || shot.turretStructureDef==null) continue;
                            var spec=shot.turretStructureDef.mount.weapon.airMissile;
                            if (spec==null || spec.blastRadius()<=0) continue;
                            double horizontal=Math.hypot(shot.toX-position.x(),shot.toY-position.y());
                            if (Math.hypot(Math.max(0,horizontal-1.1),Math.max(0,shot.toZ-.8))<=spec.blastRadius()) aaBlasts++;
                        }
                        metrics.put(lane(owner)+".deathNearbyStrikeArrivals",strikes);
                        metrics.put(lane(owner)+".deathNearbyGroundedCrashes",crashes);
                        metrics.put(lane(owner)+".deathNearbyAaBlasts",aaBlasts);
                    }
                }
                for (Flight flight:setup.flights()) {
                    AirBody body=bodies.get(flight.id());
                    ShuttleMission mission=missions.get(flight.id());
                    int issued=mission.missileEvasion.steeringTicks;
                    if (issued>previousSteering.get(flight.id())) {
                        float delta=body.facingDegrees-previousHeading.get(flight.id());
                        delta=(delta+540)%360-180;
                        double turn=Math.abs(delta);
                        String prefix=lane(flight.laneIndex());
                        metrics.put(prefix+".evasionTurnDegrees",metrics.getOrDefault(prefix+".evasionTurnDegrees",0).doubleValue()+turn);
                        if (turn>.01) { increment(metrics,"evasiveTurningTicks");increment(metrics,prefix+".evasiveTurningTicks"); }
                    }
                    previousHeading.put(flight.id(),body.facingDegrees);previousSteering.put(flight.id(),issued);
                    String flightPrefix=flightKey(flight);
                    if (mission.destroyed && !metrics.containsKey(flightPrefix+".killedTick")) {
                        metrics.put(flightPrefix+".killedTick",tick);
                        metrics.put(flightPrefix+".killedX",body.x);metrics.put(flightPrefix+".killedY",body.y);
                        metrics.put(flightPrefix+".killedSpeed",body.speed());
                        metrics.put(flightPrefix+".killedPassesLeft",mission.passesLeft);
                        metrics.put(flightPrefix+".killedFromState."+previousState.get(flight.id()),1);
                    }
                    if (mission.state==ShuttleState.ATTACK_RUN && !metrics.containsKey(flightPrefix+".firstAttackRunTick"))
                        metrics.put(flightPrefix+".firstAttackRunTick",tick);
                    if (!mission.destroyed && previousPasses.get(flight.id())>0
                            && mission.passesLeft==previousPasses.get(flight.id())-1) increment(metrics,flightPrefix+".completedPasses");
                    previousState.put(flight.id(),mission.state);previousPasses.put(flight.id(),mission.passesLeft);
                    if (!mission.destroyed && mission.state==ShuttleState.GONE && !metrics.containsKey(flightPrefix+".exitTick"))
                        metrics.put(flightPrefix+".exitTick",tick);
                }
                for (ShotEvent shot:sim.getShotsThisFrame()) {
                    Integer lane=batteryLanes.get(shot.shooterId);
                    if (lane==null) continue;
                    if (firstLaunch<0) firstLaunch=tick;
                    if (visual && clipStart<0) clipStart=tick;
                    increment(metrics,"launches");
                    increment(metrics,lane(lane)+".launches");
                    Flight target=flights.get(shot.flightTargetId);
                    if (target!=null) increment(metrics,lane(lane)+".targetHull."+target.profile().name()+".launches");
                    if (visual) cue("launch",tick,lane,shot);
                }
                for (ShotEvent shot:sim.getShotsExpiredThisFrame()) {
                    Integer lane=batteryLanes.get(shot.shooterId);
                    if (lane==null) continue;
                    String outcome=shot.impacts()?"detonations":"expiries";
                    increment(metrics,outcome); increment(metrics,lane(lane)+"."+outcome);
                    Flight contact=flights.get(shot.flightContactId);
                    if (contact!=null) {
                        increment(metrics,"aircraftContacts");
                        increment(metrics,lane(lane)+".contactHull."+contact.profile().name()+".contacts");
                        increment(metrics,lane(lane)+".contactLane."+MissileAaTestSetup.LANES.get(contact.laneIndex()).id()+".contacts");
                    }
                    if (visual && shot.impacts()) cue("impact",tick,lane,shot);
                }
                if (visual && clipStart>=0 && tick>=clipStart && tick<clipStart+CLIP_TICKS) {
                    frames.advanced(loop,sim,tick);
                    int frame=Math.round((tick-clipStart)*FPS/30f);
                    if (frame>lastFrame && frame<CLIP_TICKS*FPS/30) {
                        frames.frame(loop,sim,tick,String.format(Locale.ROOT,"Missile AA / t %.2fs / ordinary fighter health",tick/30f));
                        lastFrame=frame;
                    }
                }
            }
            int killed=0,exited=0,unresolved=0,breaks=0,steering=0;
            for (Flight flight:setup.flights()) {
                ShuttleMission mission=missions.get(flight.id());
                AirBody body=bodies.get(flight.id());String key=flightKey(flight);
                metrics.put(key+".state."+mission.state.name(),1);
                metrics.put(key+".x",body.x);metrics.put(key+".y",body.y);
                metrics.put(key+".speed",body.speed());metrics.put(key+".heading",body.facingDegrees);
                metrics.put(key+".hp",sim.world().isAlive(flight.id())?sim.world().hp(flight.id()):0);
                metrics.put(key+".dueSeconds",flight.dueSeconds());metrics.put(key+".passesLeft",mission.passesLeft);
                metrics.put(key+".pendingDelay",mission.pendingDelay);
                metrics.put(key+".runFromX",mission.runFromX);metrics.put(key+".runFromY",mission.runFromY);
                metrics.put(key+".runToX",mission.runToX);metrics.put(key+".runToY",mission.runToY);
                String prefix=lane(flight.laneIndex())+".hull."+flight.profile().name();
                String ending=mission.destroyed?"kills":mission.state==ShuttleState.GONE?"exits":"unresolved";
                increment(metrics,ending); increment(metrics,lane(flight.laneIndex())+"."+ending);
                increment(metrics,prefix+"."+ending);
                if (mission.destroyed) killed++; else if (mission.state==ShuttleState.GONE) exited++; else unresolved++;
                breaks+=mission.missileEvasion.breaks; steering+=mission.missileEvasion.steeringTicks;
                add(metrics,lane(flight.laneIndex())+".evasionBreaks",mission.missileEvasion.breaks);
                add(metrics,prefix+".evasionBreaks",mission.missileEvasion.breaks);
                add(metrics,prefix+".steeringTicks",mission.missileEvasion.steeringTicks);
            }
            for (int lane=0;lane<setup.batteries().size();lane++)
                metrics.put(lane(lane)+".batterySurvives",sim.world().isAlive(setup.batteries().get(lane))?1:0);
            for (ShotEvent shot:sim.getActiveShots()) {
                Integer owner=batteryLanes.get(shot.shooterId);
                if (owner!=null) { increment(metrics,"activeRounds");increment(metrics,lane(owner)+".activeRounds"); }
            }
            metrics.put("seed",seed); metrics.put("plannedFighters",setup.flights().size());
            metrics.put("firstLaunchTick",firstLaunch); metrics.put("evasionBreaks",breaks); metrics.put("evasionSteeringTicks",steering);
            metrics.put("kills",killed); metrics.put("exits",exited); metrics.put("unresolved",unresolved);
            if (visual) metrics.put("visualStartTick",clipStart);
            List<Verdict> verdicts=new ArrayList<>();
            verdicts.add(Verdict.of("finite-fighter-accounting",killed+exited+unresolved==setup.flights().size(),
                    "killed="+killed+", exited="+exited+", unresolved="+unresolved));
            verdicts.add(Verdict.of("physical-round-accounting",count(metrics,"launches")
                            ==count(metrics,"detonations")+count(metrics,"expiries")+count(metrics,"activeRounds"),
                    "launched="+count(metrics,"launches")+", detonated="+count(metrics,"detonations")
                            +", expired="+count(metrics,"expiries")+", active="+count(metrics,"activeRounds")));
            verdicts.add(Verdict.of(mode.equals("no-aa")?"no-batteries-no-aa-fire":"batteries-release-physical-rounds",
                    mode.equals("no-aa")?count(metrics,"launches")==0:count(metrics,"launches")>0,
                    "launches="+count(metrics,"launches")));
            verdicts.add(Verdict.of(mode.equals("normal")?"pilots-use-evasive-steering":"diagnostic-control-has-no-missile-breaks",
                    mode.equals("normal")?breaks>0 && steering>0:breaks==0,
                    "breaks="+breaks+", steering ticks="+steering));
            if (mode.equals("no-aa")) verdicts.add(Verdict.of("no-aa-no-fighter-loss",killed==0,"kills="+killed));
            else verdicts.add(Verdict.of("aa-can-kill-fighters",killed>0,"kills="+killed+", exits="+exited));
            return new SceneReport(id(),loop,TICKS,verdicts,metrics);
        }
    }

    private static String lane(int index) { return "lane."+MissileAaTestSetup.LANES.get(index).id(); }
    /** Installed 0.98a source rows, converted by the same runtime calibration; rejects silent fallback. */
    private static AirHandling measuredHandling(FighterProfile profile) {
        return switch (profile) {
            case TALON -> HullKinematics.fromSpec(325,400,300,150);
            case WASP -> HullKinematics.fromSpec(325,500,450,180);
            case THUNDER -> HullKinematics.fromSpec(450,1000,750,225);
            case BROADSWORD -> HullKinematics.fromSpec(200,400,350,90);
            default -> throw new IllegalArgumentException("Unmeasured fixture hull "+profile);
        };
    }
    private static String flightKey(Flight flight) { return lane(flight.laneIndex())+".wave"+flight.wave()+"."+flight.profile().name(); }
    private record StrikeCandidate(long source,float x,float y,float radius) { }
    private static void increment(Map<String,Number> metrics,String key) { add(metrics,key,1); }
    private static void add(Map<String,Number> metrics,String key,int value) { metrics.put(key,count(metrics,key)+value); }
    private static int count(Map<String,Number> metrics,String key) { return metrics.getOrDefault(key,0).intValue(); }
    private void cue(String kind,int tick,int lane,ShotEvent shot) throws JSONException {
        if (clipStart<0 || tick<clipStart || tick>=clipStart+CLIP_TICKS) return;
        clipEvents.put(new JSONObject().put("kind",kind).put("lane",MissileAaTestSetup.LANES.get(lane).id())
                .put("tick",tick).put("seconds",(tick-clipStart)/30d)
                .put("soundId",kind.equals("launch")?shot.turretStructureDef.mount.weapon.fireSoundId
                        :shot.turretStructureDef.mount.weapon.impactSoundId));
    }
}
