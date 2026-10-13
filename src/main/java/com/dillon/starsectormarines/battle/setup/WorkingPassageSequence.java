package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.WorkingPassageCommand;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.ObjectiveFactionPresence;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.story.WorkingPassageProgress;
import com.dillon.starsectormarines.battle.story.WorkingPassageProgress.Phase;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.world.gen.ShepherdBasin;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Finite road commitments, actual built holds and one physical Morrow departure. */
public final class WorkingPassageSequence implements Objective {
    public static final int BEND_X=209, BEND_Y=123, NARROWS_X=252, NARROWS_Y=106, LIFT_X=280, LIFT_Y=77;
    private static final String ROAD="working-passage-road";
    private final WorkingPassageProgress progress=new WorkingPassageProgress();
    private final Tender tender;
    private final List<EntitySpec> reserve;
    private final List<Integer> militia;
    private final StoryBeatLog log;
    private final List<Integer> roadSquads=new ArrayList<>();
    private final Set<Integer> handedOff=new HashSet<>();
    private final Set<Long> turretIds=new HashSet<>();
    private final boolean[] responseReached=new boolean[4];
    private int released;
    private float phaseSeconds;
    private boolean departureRequested;
    private boolean movedFromCradle;
    private final Set<Integer> holdsObserved=new HashSet<>();
    private int approachWaypoint=1;
    public record RoadTask(int squadId,int x,int y) {}
    public List<RoadTask> roadTasks(){
        List<RoadTask> tasks=new ArrayList<>();
        for(int i=0;i<roadSquads.size();i++)tasks.add(new RoadTask(roadSquads.get(i),i==0||i==2?BEND_X:i==1?NARROWS_X:LIFT_X,i==0||i==2?BEND_Y:i==1?NARROWS_Y:LIFT_Y));
        return List.copyOf(tasks);
    }
    public WorkingPassageSequence(Tender tender,List<Integer> militia,List<EntitySpec> reserve,StoryBeatLog log) {
        this.tender=tender;this.militia=List.copyOf(militia);this.reserve=List.copyOf(reserve);this.log=log;
        if(reserve.size()!=20 || militia.size()!=2) throw new IllegalArgumentException("Passage requires four finite teams and two militia holds");
    }
    public WorkingPassageProgress progress(){return progress;}
    public Tender tender(){return tender;}
    public List<Integer> militiaSquadIds(){return militia;}
    public int pendingMembers(){return progress.terminal()?0:reserve.size()-releasedMembers();}
    private int releasedMembers(){return switch(released){case 0->0;case 1->4;case 2->10;case 3->14;default->20;};}
    public Set<Long> turretEntityIds(){return Set.copyOf(turretIds);}
    public int releasedTeams(){return released;}
    public boolean responseReached(int team){return responseReached[team];}
    public int targetX(){return switch(progress.phase()){case BEND,BEND_BUILD->BEND_X;case NARROWS,NARROWS_BUILD->NARROWS_X;default->LIFT_X;};}
    public int targetY(){return switch(progress.phase()){case BEND,BEND_BUILD->BEND_Y;case NARROWS,NARROWS_BUILD->NARROWS_Y;default->LIFT_Y;};}
    public int buildX(){return progress.phase()==Phase.BEND || progress.phase()==Phase.BEND_BUILD?BEND_X:NARROWS_X;}
    public int buildY(){return progress.phase()==Phase.BEND || progress.phase()==Phase.BEND_BUILD?BEND_Y:NARROWS_Y;}
    public WorkingPassageProgress.Report report(BattleSimulation sim){return progress.report(8-militia.stream().mapToInt(sim::squadMemberCount).sum());}
    public void advance(BattleSimulation sim){
        if(sim.isComplete() || progress.terminal())return;
        if(!ObjectiveFactionPresence.anyInPlay(sim,Faction.MARINE,id->isCompany(sim,id))){progress.fail(false);emit(sim,"company_lost");return;}
        Phase before=progress.phase();phaseSeconds+=BattleSimulation.TICK_DT;
        if(!movedFromCradle&&Math.hypot(tender.anchorX()-131,tender.anchorY()-115)>3){movedFromCradle=true;emit(sim,"underway");}
        if(before.ordinal()>=Phase.NARROWS.ordinal())observeHold(sim,0,201,132);
        if(before.ordinal()>=Phase.CLIMB_OUT.ordinal())observeHold(sim,1,248,121);
        boolean company=false,hostile=false;
        for(int i=0;i<sim.liveUnitCount();i++){
            long id=sim.liveUnitAt(i);
            String name=sim.identity().name(id);
            if(sim.identity().faction(id)==Faction.MARINE && name!=null && name.startsWith("tender-turret-"))turretIds.add(id);
            if(!sim.identity().type(id).combatant)continue;
            double d=Math.hypot(sim.world().x(id)-(targetX()+.5f),sim.world().y(id)-(targetY()+.5f));
            if(isCompany(sim,id)&&d<20)company=true;
            // The territorial hold is distinct from the builder's wider known-threat
            // safety footprint. Hidden distant guards cannot freeze the ship's approach.
            if(Faction.MARINE.hostileTo(sim.identity().faction(id))&&d<24)hostile=true;
        }
        if(before==Phase.BEND_BUILD && released==0)release(sim);
        if(before==Phase.NARROWS && released==1 && company)release(sim);
        if(before==Phase.NARROWS_BUILD && released==2 && phaseSeconds>=12)release(sim);
        if(before==Phase.SPOOL && released==3)release(sim);
        handoffRoadTeams(sim);
        int stop=before==Phase.BEND||before==Phase.BEND_BUILD?1:before==Phase.NARROWS||before==Phase.NARROWS_BUILD?2:3;
        float stationX=ShepherdBasin.HOVER_ROUTE[stop][0],stationY=ShepherdBasin.HOVER_ROUTE[stop][1];
        // Construction and deliberate remote/pilot orders own the helm until released.
        if(!tender.piloted() && tender.ship().remoteOrder()==Warship.RemoteOrder.NONE && tender.current()==null && before!=Phase.DEPARTURE){
            if(company&&!hostile){
                if(stop==1){
                    float[][] route=ShepherdBasin.HOVER_APPROACH;
                    if(approachWaypoint<route.length-1&&Math.hypot(tender.anchorX()-route[approachWaypoint][0],tender.anchorY()-route[approachWaypoint][1])<2)approachWaypoint++;
                    tender.ship().stationTo(route[approachWaypoint][0],route[approachWaypoint][1]);
                }else tender.ship().stationTo(stationX,stationY);
            }
            else tender.ship().stationTo(tender.anchorX(),tender.anchorY());
        }
        // A construction handover means the bay is actually within builder reach,
        // not merely that the large hull has entered the assembly area.
        boolean arrived=Math.hypot(tender.anchorX()-stationX,tender.anchorY()-stationY)<1
                && (stop==3 || tender.groundSiteReachable(buildX()+.5f,buildY()+.5f,sim.getGrid()));
        boolean built=false;
        for(long id:turretIds)if(sim.world().isAlive(id)&&Math.hypot(sim.world().x(id)-buildX(),sim.world().y(id)-buildY())<22)built=true;
        boolean response=switch(before){case BEND_BUILD->responseReached[0];case NARROWS_BUILD->responseReached[1]&&responseReached[2];case SPOOL->responseReached[3];default->true;};
        if((before==Phase.BEND_BUILD||before==Phase.NARROWS_BUILD)&&!built&&tender.buildersLost()>=tender.builderComplement()){
            progress.fail(false);emit(sim,"builders_lost");return;
        }
        progress.advance(BattleSimulation.TICK_DT,tender.anchorAlive(),tender.disabled() || tender.ship().hullFraction()<=.2f,
                company,hostile,arrived,built,response,tender.departed());
        if(progress.phase()==Phase.DEPARTURE&&!departureRequested){
            departureRequested=true;tender.cancelAll();tender.defenceOut(false);tender.requestDeparture(sim.getGrid().getWidth(),sim.getGrid().getHeight());
        }
        if(progress.phase()!=before){phaseSeconds=0;emit(sim,progress.phase().name().toLowerCase(Locale.ROOT));}
    }
    private void observeHold(BattleSimulation sim,int team,int x,int y){
        int squadId=militia.get(team);
        if(holdsObserved.contains(squadId)||sim.squadMemberCount(squadId)==0)return;
        var squad=sim.getSquad(squadId);
        if(Math.hypot(squad.centroidX-x,squad.centroidY-y)<8){
            holdsObserved.add(squadId);
            log.emitOnce(sim.getSimTickIndex(),"working_passage.militia_hold_"+team,StoryBeatLog.Audience.PLAYER);
        }
    }
    private void release(BattleSimulation sim){
        int batch=released++,from=switch(batch){case 0->0;case 1->4;case 2->10;default->14;},size=batch==0||batch==2?4:6;
        int squad=sim.mintSquad(Faction.DEFENDER,UnitType.MILITIA);
        for(int i=from;i<from+size;i++)sim.spawn(reserve.get(i).squad(squad));
        sim.getSquad(squad).originalSize=size;
        int x=batch==1?279:194,y=batch==1?133:45;
        sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad,x,y),CommandAuthority.SCRIPTED,ROAD,"Scrappers commit from the named haul approach");
        roadSquads.add(squad);emit(sim,"road_team_"+batch);
    }
    private void handoffRoadTeams(BattleSimulation sim){
        for(int i=0;i<roadSquads.size();i++){
            int id=roadSquads.get(i);var squad=sim.getSquad(id);int x=i==1?279:194,y=i==1?133:45;
            if(!handedOff.contains(id)&&sim.squadMemberCount(id)>0&&(Math.hypot(squad.centroidX-x,squad.centroidY-y)<7||squad.hasDirectContactThisTick())){
                if(sim.handoffSquadCommand(id,ROAD,CommandAuthority.MISSION_COMMAND,WorkingPassageCommand.issuer(Faction.DEFENDER),null,"Pressure the company route"))handedOff.add(id);
            }
            int tx=i==0||i==2?BEND_X:i==1?NARROWS_X:LIFT_X,ty=i==0||i==2?BEND_Y:i==1?NARROWS_Y:LIFT_Y;
            if(sim.squadMemberCount(id)==0||Math.hypot(squad.centroidX-tx,squad.centroidY-ty)<34)responseReached[i]=true;
        }
    }
    private void emit(BattleSimulation sim,String beat){log.emitOnce(sim.getSimTickIndex(),"working_passage."+beat,StoryBeatLog.Audience.PLAYER);}
    private static boolean isCompany(BattleSimulation sim,long id){
        if(sim.identity().faction(id)!=Faction.MARINE)return false;
        return switch(sim.identity().type(id)){case MARINE,MARINE_BLUE,MARINE_RED,HEAVY_MECH->true;default->false;};
    }
    public Faction owningFaction(){return Faction.MARINE;}
    public void tick(BattleView ignored){}
    public boolean isComplete(){return progress.phase()==Phase.COMPLETE;}
    public boolean isFailed(){return progress.phase()==Phase.FAILED;}
    public String displayName(){return switch(progress.phase()){
        case BEND->"Clear the pump bend and escort Morrow forward";
        case BEND_BUILD->"Build a Vulcan turret at the pump bend; hold the haul-road response";
        case NARROWS->"Clear the narrows while militia hold the pump bend";
        case NARROWS_BUILD->"Build a Vulcan turret at the narrows; contain both road approaches";
        case CLIMB_OUT->"Secure the climb-out yard and escort Morrow into it";
        case SPOOL->"Hold the climb-out approach / drives "+(int)(100*progress.work()/WorkingPassageProgress.SPOOL_SECONDS)+"%";
        case DEPARTURE->"Defend Morrow's physical climb-out";
        case COMPLETE->"Morrow has departed; registry transfer authorized";
        case FAILED->"Departure aborted; recovery claim and repairs retained";
    };}
}
