package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.i18n.Strings;
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
import com.dillon.starsectormarines.battle.world.model.CellTopology;
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
    private static final int ROAD_WARNING_DELAY_TICKS=20*30;
    private int releasedAtTick;
    private final SalvageRightsProgress progress=new SalvageRightsProgress();
    private final PointOfInterest receiving,workshop;
    private final int engineers;
    private final List<EntitySpec> reserve;
    private List<RoadTechnical> roadMachines=List.of();
    private final StoryBeatLog log;
    private final List<Integer> roadSquads=new ArrayList<>();
    private final Set<Integer> handedOff=new HashSet<>();
    private boolean released;
    /** {@code reserve} is the south road's infantry, four to a squad in order; each squad takes its first member's type. */
    public SalvageRightsSequence(PointOfInterest receiving,PointOfInterest workshop,int engineers,List<EntitySpec> reserve,StoryBeatLog log) {
        this.receiving=receiving;this.workshop=workshop;this.engineers=engineers;this.reserve=List.copyOf(reserve);this.log=log;
    }
    /** Hulls that drive up the haul road behind the infantry when it enters. Setup only. */
    void sendAlongRoad(List<RoadTechnical> machines){
        if(released)throw new IllegalStateException("The road reserve has already entered");
        roadMachines=List.copyOf(machines);
    }
    public SalvageRightsProgress progress(){return progress;}
    public int pendingMembers(){return released?0:reserve.size()+roadMachines.size();}
    public int targetX(){return progress.phase()==Phase.RECEIVING?receiving.interiorAnchorX:progress.phase()==Phase.WORKSHOP?workshop.interiorAnchorX:127;}
    public int targetY(){return progress.phase()==Phase.RECEIVING?receiving.interiorAnchorY:progress.phase()==Phase.WORKSHOP?workshop.interiorAnchorY:101;}
    public PointOfInterest targetBuilding(){return progress.phase()==Phase.RECEIVING?receiving:progress.phase()==Phase.WORKSHOP?workshop:null;}
    public void advance(BattleSimulation sim) {
        if(sim.isComplete() || progress.terminal())return;
        boolean company=false,enemy=false,crewAtHull=false;
        int reserveAlive=0;
        for(int i=0;i<sim.liveUnitCount();i++){
            long id=sim.liveUnitAt(i);float x=sim.world().x(id),y=sim.world().y(id);
            Faction faction=sim.identity().faction(id);
            if(faction==Faction.ALLY && sim.squad().squadId(id)==engineers && Math.hypot(x-127,y-101)<5)crewAtHull=true;
            if(!sim.identity().type(id).combatant)continue;
            boolean at;
            PointOfInterest building=progress.phase()==Phase.RECEIVING?receiving:progress.phase()==Phase.WORKSHOP?workshop:null;
            if(building!=null) at=insideBuilding(sim.getTopology(),building,x,y);
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
        if(progress.phase()!=before) {
            emit(sim,progress.phase().name().toLowerCase(Locale.ROOT));
            // A newly displayed objective must not inherit presence at the previous building.
            boolean nextCompany=false,nextEnemy=false;
            PointOfInterest nextBuilding=targetBuilding();
            for(int i=0;i<sim.liveUnitCount();i++) {
                long id=sim.liveUnitAt(i);
                if(!sim.identity().type(id).combatant)continue;
                float x=sim.world().x(id),y=sim.world().y(id);
                boolean at=nextBuilding!=null?insideBuilding(sim.getTopology(),nextBuilding,x,y):Math.hypot(x-127,y-101)<13;
                if(at) {
                    Faction faction=sim.identity().faction(id);
                    nextCompany|=faction==Faction.MARINE;
                    nextEnemy|=Faction.MARINE.hostileTo(faction);
                }
            }
            progress.observeTarget(nextCompany,nextEnemy);
        }
        if(progress.phase()==Phase.INSPECTION && enemy)emit(sim,"inspection_contested");
        if(!released && !progress.terminal() && progress.phase().ordinal()>=Phase.CRADLE.ordinal()) {
            released=true;
            for(int start=0;start<reserve.size();start+=4){
                int squad=sim.mintSquad(Faction.DEFENDER,reserve.get(start).type);
                for(int i=start;i<Math.min(start+4,reserve.size());i++)sim.spawn(reserve.get(i).squad(squad));
                sim.getSquad(squad).originalSize=Math.min(4,reserve.size()-start);
                sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad,194,45),CommandAuthority.SCRIPTED,ROAD,"Scrappers enter along the south haul road");
                roadSquads.add(squad);
            }
            for(RoadTechnical machine:roadMachines)machine.enter(sim);
            log.emitOnce(sim.getSimTickIndex(),"shepherd.salvage.reserve_entered",StoryBeatLog.Audience.OBSERVER_ONLY);
            releasedAtTick=sim.getSimTickIndex();
        }
        // Castle's warning follows Vale's cradle call and first sight of Morrow instead of
        // queueing behind them on the same tick; the scrappers are still on the road.
        if(released && !progress.terminal() && sim.getSimTickIndex()-releasedAtTick>=ROAD_WARNING_DELAY_TICKS)emit(sim,"road_warning");
        for(int squadId:roadSquads){
            var squad=sim.getSquad(squadId);
            if(!handedOff.contains(squadId) && sim.squadMemberCount(squadId)>0
                    && (Math.hypot(squad.centroidX-194,squad.centroidY-45)<5 || squad.hasDirectContactThisTick()))
                if(sim.handoffSquadCommand(squadId,ROAD,CommandAuthority.MISSION_COMMAND,SalvageRightsCommand.issuer(Faction.DEFENDER),null,"Scrappers press the cradle approach"))
                    handedOff.add(squadId);
        }
    }
    /** Concave exterior pockets and wall cells are not secured indoor ground. */
    public static boolean insideBuilding(CellTopology topology,PointOfInterest building,float x,float y){
        int cellX=(int)Math.floor(x),cellY=(int)Math.floor(y);
        if(cellX<building.left || cellX>building.right || cellY<building.top || cellY>building.bottom)return false;
        int identity=topology.getBuildingId(building.interiorAnchorX,building.interiorAnchorY);
        return identity>0 && topology.getBuildingId(cellX,cellY)==identity;
    }
    private void emit(BattleSimulation sim,String beat){log.emitOnce(sim.getSimTickIndex(),"shepherd.salvage."+beat,StoryBeatLog.Audience.PLAYER);}
    public Faction owningFaction(){return Faction.MARINE;}
    public void tick(BattleView sim){}
    public boolean isComplete(){return progress.phase()==Phase.SECURED;}
    public boolean isFailed(){return progress.phase()==Phase.FAILED;}
    public String displayName(){return switch(progress.phase()){
        case RECEIVING->Strings.get("salvageRightsReceiving");
        case WORKSHOP->Strings.get("salvageRightsWorkshop");
        case CRADLE->Strings.get("salvageRightsCradle");
        case INSPECTION->Strings.format("salvageRightsInspection",(int)(progress.inspection()/SalvageRightsProgress.INSPECTION_SECONDS*100));
        case SECURED->Strings.get("salvageRightsSecured");
        case FAILED->Strings.get("salvageRightsFailed");
    };}
}
