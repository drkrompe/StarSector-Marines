package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.RescueEscortCommand;
import com.dillon.starsectormarines.battle.command.RescueCommandSnapshot;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.LastLiftProgress;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Whole mission/retry evidence, deliberately opt-in. Failure injections are labelled controls. */
@Tag("last-lift-command-evidence")
class LastLiftCommandEvidenceTest {
    @Test void recordsEscortBoardingAndPersistentRetries() throws Exception {
        assertEquals(Integer.MAX_VALUE,UnitUpdateSystem.configuredMinimumParallelUnits());
        Path output = Path.of(System.getProperty("lastlift.command.evidence.outputDir","build/reports/commander/last-lift"));
        Files.createDirectories(output);
        String custom = System.getProperty("lastlift.command.evidence.fixture.path","");
        JSONArray fixtures = custom.isBlank() ? new JSONArray() : new JSONArray(Files.readString(Path.of(custom)));
        if (custom.isBlank()) for (int seed : new int[]{9201,9202,9203})
            fixtures.put(new JSONObject().put("id",seed+"-normal").put("seed",seed));
        if (custom.isBlank()) {
            fixtures.put(new JSONObject().put("id","four-squads").put("boats",4));
            fixtures.put(new JSONObject().put("id","single-ferry").put("boats",1).put("type","AEROSHUTTLE").put("cycles",4));
            fixtures.put(new JSONObject().put("id","pickup-delay").put("pickupDelay",240));
            fixtures.put(new JSONObject().put("id","partial-retreat-retry").put("retreatAtBoarded",3).put("killCivilians",2).put("retry",true));
            fixtures.put(new JSONObject().put("id","escort-loss").put("killEscort",true));
            fixtures.put(new JSONObject().put("id","pickup-loss-retry").put("losePickup",true).put("retry",true));
            fixtures.put(new JSONObject().put("id","no-player").put("playerDelay",700));
            fixtures.put(new JSONObject().put("id","no-survivors").put("killCivilians",12));
        }
        JSONArray all = new JSONArray();
        for (int f=0;f<fixtures.length();f++) {
            JSONObject fixture=fixtures.getJSONObject(f);
            Path dir=output.resolve(fixture.getString("id")); Files.createDirectories(dir);
            String first=null;
            for (int copy=0;copy<EvidenceFanOut.repeat();copy++) {
                var progress=new LastLiftProgress("jangala:rill");
                JSONArray attempts=new JSONArray();
                attempts.put(run(fixture,progress,dir.resolve("first"),copy==0));
                if (fixture.optBoolean("retry",false)) {
                    assertFalse(progress.isClosed());
                    JSONObject retry=new JSONObject().put("id",fixture.getString("id")+"-retry").put("seed",fixture.optLong("seed",9201));
                    attempts.put(run(retry,progress,dir.resolve("retry"),copy==0));
                }
                String result=new JSONObject().put("attempts",attempts).put("ledger",progress.summary())
                        .put("payment",progress.payment()).put("closed",progress.isClosed()).toString(2);
                if(first==null)first=result; else assertEquals(first,result,"Last Lift must replay identically");
            }
            Files.writeString(dir.resolve("summary.json"),first);
            all.put(new JSONObject(first).put("fixture",fixture.getString("id")));
        }
        Files.writeString(output.resolve("summary.json"),all.toString(2));
    }
    private JSONObject run(JSONObject fixture,LastLiftProgress progress,Path dir,boolean capture) throws Exception {
        Files.createDirectories(dir);
        var attempt=progress.beginAttempt();
        List<ShuttleAssignment> manifest=new ArrayList<>();
        ShuttleType type=ShuttleType.valueOf(fixture.optString("type","VALKYRIE"));
        for(int i=0;i<fixture.optInt("boats",2);i++) manifest.add(new ShuttleAssignment(
                new FittedBoat(type,BoatFitting.REINFORCED_PLATING,BoatFitting.STANDARD_DRIVE,"rill-player-"+i),
                fixture.optInt("cycles",1),type.capacity));
        int maxTicks=Integer.getInteger("lastlift.command.evidence.maxTicks",15000);
        try(BattleSimulation sim=BattleSetup.createLastLift(fixture.optLong("seed",9201),manifest,TargetProfile.NEUTRAL,attempt)) {
            long pickup=0;
            int boat=0;
            for(long air:sim.getAirEntityIds()) {
                if(sim.world().airFaction(air)==Faction.MARINE) {
                    assertSame(manifest.get(boat++).airframe,sim.world().airframe(air));
                    sim.world().mission(air).pendingDelay+=(float)fixture.optDouble("playerDelay",0);
                } else if(sim.world().airFaction(air)==Faction.CIVILIAN) {
                    pickup=air;
                    sim.world().mission(air).pendingDelay+=(float)fixture.optDouble("pickupDelay",0);
                    assertEquals(12,sim.world().mission(air).evacueeCapacity);
                }
            }
            assertNotEquals(0,pickup); assertEquals(manifest.size(),boat);
            assertEquals(attempt.people().size(),sim.getCivilianEvacuationTracker().registeredCount());
            assertEquals(12,count(sim,Faction.DEFENDER));
            boolean killed=false,escortKilled=false,pickupLost=false;
            JSONArray samples=new JSONArray();
            var renderer=capture?new BattleReviewFrameRenderer(Path.of("mod"),1920,1080):null;
            sim.setCommandTraceEnabled(true,"LAST_LIFT");
            try(var visuals=CommanderEvidenceCapture.open(capture?dir:null,"last-lift",sim,(battle,caption,marks)->{
                var labels=ReviewAnnotations.builder();
                labels.label(96,111,"RILL MOTOR POOL",ReviewStyle.OBJECTIVE);
                labels.label(96,64,"LOADING STREET",ReviewStyle.NOTE);
                labels.label(68,80,"MAINTENANCE LANE",ReviewStyle.NOTE);
                labels.label(96,20,"EMPLOYER PICKUP",ReviewStyle.LANDING);
                labels.label(2,64,"WEST ROAD",ReviewStyle.NOTE);
                labels.label(172,64,"EAST ROAD",ReviewStyle.NOTE);
                var t=battle.getCivilianEvacuationTracker();
                return renderer.render(battle,caption+" / "+battle.getLastLiftSequence().status()
                        +" / ABOARD "+t.evacuatedCount()+" / ACTIVE "+t.activeCount(),labels.build());
            })) {
                while(!sim.isComplete()&&sim.getSimTickIndex()<maxTicks) {
                    sim.advance(BattleSimulation.TICK_DT);
                    int tick=sim.getSimTickIndex();
                    var tracker=sim.getCivilianEvacuationTracker();
                    if(!killed&&tick>=60&&fixture.optInt("killCivilians",0)>0) {
                        for(int i=0;i<fixture.getInt("killCivilians");i++) sim.applyDamage(sim.getLastLiftAttempt().entityId(i),10000f,1f);
                        killed=true;
                    }
                    if(!escortKilled&&fixture.optBoolean("killEscort",false)&&sim.isCivilianEvacuationTriggered()) {
                        var snapshot=((RescueEscortCommand)sim.getCommander(Faction.MARINE)).rescueSnapshot();
                        for(var intent:snapshot.squadIntents()) if(intent.role()==RescueCommandSnapshot.Role.COHORT_ESCORT) {
                            List<Long> members=new ArrayList<>();
                            for(int j=0;j<sim.squadMemberCount(intent.squadId());j++) members.add(sim.squadMemberAt(intent.squadId(),j));
                            for(long id:members)sim.applyDamage(id,10000f,1f);
                            escortKilled=true;
                        }
                    }
                    if(!pickupLost&&fixture.optBoolean("losePickup",false)&&tick>=1800) {
                        sim.world().mission(pickup).state=ShuttleState.GONE; pickupLost=true;
                    }
                    if(fixture.has("retreatAtBoarded")&&tracker.evacuatedCount()>=fixture.getInt("retreatAtBoarded")&&!sim.isComplete())
                        assertTrue(sim.retreat());
                    if(tick==1||tick%300==0||sim.isComplete()) samples.put(sample(sim,pickup));
                    visuals.afterAdvance();
                }
            }
            if(!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
            var outcome=sim.getLastLiftAttempt().outcome();
            int payment=outcome==null?0:progress.settle(outcome);
            if(outcome!=null)assertEquals(0,progress.settle(outcome));
            if(fixture.optBoolean("retry",false)&&fixture.has("retreatAtBoarded")) {
                assertNotNull(outcome);assertEquals(3,outcome.rescued().size());assertEquals(2,outcome.dead().size());
                assertEquals(7,progress.stranded().size());assertEquals(0,payment);
            }
            String trace=sim.getCommandTraceJsonLines();
            if (capture) Files.writeString(dir.resolve("trace.jsonl"),trace);
            else assertEquals(Files.readString(dir.resolve("trace.jsonl")),trace,"Byte-identical command replay");
            if (fixture.optDouble("playerDelay",0) > maxTicks / 30.0) {
                assertFalse(sim.isCivilianEvacuationTriggered()); assertNull(outcome);
                assertEquals(0,sim.getLastLiftSequence().pendingMembers());
            }
            if (fixture.optBoolean("losePickup",false)) { assertNotNull(outcome); assertFalse(outcome.victory()); }
            if (!fixture.has("playerDelay") && !fixture.has("retreatAtBoarded")
                    && !fixture.optBoolean("losePickup",false) && fixture.optInt("killCivilians",0) < 12)
                assertEquals(Faction.MARINE,sim.getWinner(),"Recoverable authored cohort should complete");
            return new JSONObject().put("outcome",sim.isComplete()?sim.getWinner().name():"TIMEOUT")
                    .put("tick",sim.getSimTickIndex()).put("roster",attempt.people())
                    .put("rescued",outcome==null?List.of():outcome.rescued()).put("dead",outcome==null?List.of():outcome.dead())
                    .put("payment",payment).put("samples",samples).put("storyBeats",StoryBeatEvidence.snapshot(sim))
                    .put("traceHash",trace.hashCode());
        }
    }
    private JSONObject sample(BattleSimulation sim,long pickup)throws Exception {
        var t=sim.getCivilianEvacuationTracker();JSONArray people=new JSONArray();
        for(int i=0;i<t.registeredCount();i++) {long id=t.entityIdAt(i); if(sim.resolveUnit(id)!=0L)
            people.put(new JSONObject().put("id",i).put("x",sim.world().x(id)).put("y",sim.world().y(id)));}
        return new JSONObject().put("tick",sim.getSimTickIndex()).put("active",t.activeCount()).put("aboard",t.evacuatedCount())
                .put("lost",t.lostCount()).put("player",count(sim,Faction.MARINE)).put("militia",count(sim,Faction.ALLY))
                .put("raiders",count(sim,Faction.DEFENDER)).put("pending",sim.getLastLiftSequence().pendingMembers())
                .put("pickup",(sim.world().mission(pickup)==null?"DESTROYED":sim.world().mission(pickup).state.name())).put("people",people);
    }
    private static int count(BattleSimulation sim,Faction faction) {
        int n=0;for(int i=0;i<sim.liveUnitCount();i++)if(sim.identity().faction(sim.liveUnitAt(i))==faction)n++;return n;
    }
}
