package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.SalvageRightsCommand;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.SalvageRightsProgress;
import com.dillon.starsectormarines.battle.story.SalvageRightsProgress.Phase;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

/** Finite recovery operation; engineers physically cross to the hull before inspection can advance. */
public final class SalvageRightsSequence implements Objective {
    public record Report(boolean inspected,int crewLost){}
    public Report report(BattleSimulation sim){return new Report(isComplete(),2-sim.squadMemberCount(engineers));}
    private static final String ROAD="salvage-south-road";
    private final SalvageRightsProgress progress=new SalvageRightsProgress();
    private final PointOfInterest receiving,workshop;
    private final int engineers;
    private final List<EntitySpec> reserve;
    private final StoryBeatLog log;
    private final List<Integer> roadSquads=new ArrayList<>();
    private final Set<Integer> handedOff=new HashSet<>();
    private boolean released;
    public SalvageRightsSequence(PointOfInterest receiving,PointOfInterest workshop,int engineers,List<EntitySpec> reserve,StoryBeatLog log) {
        this.receiving=receiving;this.workshop=workshop;this.engineers=engineers;this.reserve=List.copyOf(reserve);this.log=log;
    }
    public SalvageRightsProgress progress(){return progress;}
    public int pendingMembers(){return released?0:reserve.size();}
    public int targetX(){return progress.phase()==Phase.RECEIVING?receiving.interiorAnchorX:progress.phase()==Phase.WORKSHOP?workshop.interiorAnchorX:127;}
    public int targetY(){return progress.phase()==Phase.RECEIVING?receiving.interiorAnchorY:progress.phase()==Phase.WORKSHOP?workshop.interiorAnchorY:101;}
    public void advance(BattleSimulation sim) {
        if(sim.isComplete() || progress.terminal())return;
        boolean company=false,enemy=false,crewAtHull=false;
        int reserveAlive=0;
        for(int i=0;i<sim.liveUnitCount();i++){
            long id=sim.liveUnitAt(i);float x=sim.world().x(id),y=sim.world().y(id);
            Faction faction=sim.identity().faction(id);
            if(sim.squad().squadId(id)==engineers && faction==Faction.ALLY && Math.hypot(x-127,y-101)<5)crewAtHull=true;
            if(!sim.identity().type(id).combatant)continue;
            boolean at;
            PointOfInterest building=progress.phase()==Phase.RECEIVING?receiving:progress.phase()==Phase.WORKSHOP?workshop:null;
            if(building!=null) at=x>building.left && x<building.right && y>building.top && y<building.bottom;
            else at=Math.hypot(x-127,y-101)<13;
            if(at){company|=faction==Faction.MARINE;enemy|=Faction.MARINE.hostileTo(faction);}
            if(faction==Faction.MARINE)emit(sim,"arrival");
            if(faction==Faction.MARINE && Math.hypot(x-127,y-101)<35)emit(sim,"morrow_sighted");
        }
        for(int squad:roadSquads)reserveAlive+=sim.squadMemberCount(squad);
        Phase before=progress.phase();
        // Securing the inspection is the contract. Surviving attackers outside the work site
        // must not turn a completed recovery into a map-wide extermination objective.
        boolean responseCommitted=released && (reserveAlive==0 || handedOff.size()==roadSquads.size());
        progress.advance(BattleSimulation.TICK_DT,company,enemy,crewAtHull,sim.squadMemberCount(engineers)>0,responseCommitted);
        if(progress.phase()!=before)emit(sim,progress.phase().name().toLowerCase(Locale.ROOT));
        if(progress.phase()==Phase.INSPECTION && enemy)emit(sim,"inspection_contested");
        if(!released && !progress.terminal() && progress.phase().ordinal()>=Phase.CRADLE.ordinal()) {
            released=true;
            for(int start=0;start<reserve.size();start+=4){
                int squad=sim.mintSquad(Faction.DEFENDER,UnitType.MILITIA);
                for(int i=start;i<Math.min(start+4,reserve.size());i++)sim.spawn(reserve.get(i).squad(squad));
                sim.getSquad(squad).originalSize=Math.min(4,reserve.size()-start);
                sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad,194,45),CommandAuthority.SCRIPTED,ROAD,"Scrappers enter along the south haul road");
                roadSquads.add(squad);
            }
            log.emitOnce(sim.getSimTickIndex(),"shepherd.salvage.reserve_entered",StoryBeatLog.Audience.OBSERVER_ONLY);
            emit(sim,"road_warning");
        }
        for(int squadId:roadSquads){
            var squad=sim.getSquad(squadId);
            if(!handedOff.contains(squadId) && sim.squadMemberCount(squadId)>0
                    && (Math.hypot(squad.centroidX-194,squad.centroidY-45)<5 || squad.hasDirectContactThisTick()))
                if(sim.handoffSquadCommand(squadId,ROAD,CommandAuthority.MISSION_COMMAND,SalvageRightsCommand.issuer(Faction.DEFENDER),null,"Scrappers press the cradle approach"))
                    handedOff.add(squadId);
        }
    }
    private void emit(BattleSimulation sim,String beat){log.emitOnce(sim.getSimTickIndex(),"shepherd.salvage."+beat,StoryBeatLog.Audience.PLAYER);}
    public Faction owningFaction(){return Faction.MARINE;}
    public void tick(BattleView sim){}
    public boolean isComplete(){return progress.phase()==Phase.SECURED;}
    public boolean isFailed(){return progress.phase()==Phase.FAILED;}
    public String displayName(){return switch(progress.phase()){
        case RECEIVING->"Clear the receiving store";
        case WORKSHOP->"Secure the repair workshop for Vale's militia";
        case CRADLE->"Secure Morrow's southern service access";
        case INSPECTION->"Secure the work site / inspection "+(int)(progress.inspection()/SalvageRightsProgress.INSPECTION_SECONDS*100)+"%";
        case SECURED->"Morrow's recovery claim secured";
        case FAILED->"Inspection team lost";
    };}
}
