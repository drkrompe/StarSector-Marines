package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.story.SalvageRightsProgress;
import com.dillon.starsectormarines.battle.story.SalvageRightsProgress.Phase;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

/** Public mission phase and own-force reports are the entire command input. */
public final class SalvageRightsCommand implements AutonomousMissionCommand<SalvageRightsCommand.Frame, String> {
    public static final class Frame extends CommandFrame {
        final Phase phase;
        Frame(CommandFrame base, Phase phase) {
            super(base.tick(),base.perspective(),base.squads(),base.influence(),base.topology(),base.assignments());
            this.phase=phase;
        }
    }
    public static CommandFrameDisclosure<Frame> disclosure(SalvageRightsProgress progress) {
        return (view,faction,topology,assignments) -> new Frame(
                CommandFrame.freeze(view,faction,topology,assignments),progress.phase());
    }
    private final Faction faction;
    private final int receivingX,receivingY,workshopX,workshopY,engineers;
    private PointOfInterest receiving,workshop;
    public SalvageRightsCommand(Faction faction,PointOfInterest receiving,PointOfInterest workshop,int engineers) {
        this(faction,receiving.interiorAnchorX,receiving.interiorAnchorY,workshop.interiorAnchorX,workshop.interiorAnchorY,engineers);
        this.receiving=receiving;this.workshop=workshop;
    }
    public SalvageRightsCommand(Faction faction,int receivingX,int receivingY,int workshopX,int workshopY,int engineers) {
        this.faction=faction; this.receivingX=receivingX; this.receivingY=receivingY;
        this.workshopX=workshopX; this.workshopY=workshopY; this.engineers=engineers;
    }
    public static String issuer(Faction faction) { return "salvage-rights:"+faction; }
    public Faction faction() { return faction; }
    public String strategyId() { return issuer(faction); }
    public void publish(CommanderSnapshot<String> snapshot) { }
    public CommandPlan<String> plan(Frame frame) {
        var proposals=new ArrayList<CommandProposal>();
        int count=0;
        for (var squad:frame.squads()) {
            if (squad.aliveMembers()<=0) continue;
            var old=squad.directive();
            boolean owned=old!=null && (old.authority()==CommandAuthority.MISSION_COMMAND && strategyId().equals(old.issuer())
                    || old.authority()==CommandAuthority.PLAYER_INTERVENTION && old.leaseUntilTick()>=0 && old.leaseUntilTick()<frame.tick());
            if (!owned) {
                proposals.add(CommandProposal.retain(squad.squadId(),CommandAuthority.MISSION_COMMAND,"EXTERNAL_OWNERSHIP_PRESERVED"));
                continue;
            }
            int x=127,y=101,radius=5;
            boolean advance=faction!=Faction.ALLY;
            if (faction==Faction.MARINE) {
                if(frame.phase==Phase.RECEIVING){x=receivingX;y=receivingY;}
                if(frame.phase==Phase.WORKSHOP){x=workshopX;y=workshopY;}
                advance=frame.phase.ordinal()<Phase.INSPECTION.ordinal();
                if(!advance){x=squad.squadId()%2==0?137:121;y=98;radius=5;}
            } else if(faction==Faction.ALLY) {
                boolean isCrew=squad.squadId()==engineers;
                boolean workshopSecured=frame.phase.ordinal()>=Phase.CRADLE.ordinal();
                if(isCrew && frame.phase.ordinal()>=Phase.INSPECTION.ordinal()){x=127;y=101;radius=2;}
                else if(!isCrew && squad.squadId()%2==0 && frame.phase.ordinal()>=Phase.INSPECTION.ordinal()){x=123;y=95;radius=3;}
                else if(workshopSecured){x=isCrew?82:78;y=isCrew?117:123+(squad.squadId()%2)*7;radius=2;}
                else {x=isCrew?28:24;y=isCrew?78:84+(squad.squadId()%2)*9;radius=2;}
            }
            ObjectiveAssignment next=advance ? ObjectiveAssignment.attackMove(squad.squadId(),x,y)
                    : ObjectiveAssignment.defendArea(squad.squadId(),x,y,radius);
            PointOfInterest building=frame.phase==Phase.RECEIVING?receiving:frame.phase==Phase.WORKSHOP?workshop:null;
            if(faction==Faction.MARINE && building!=null) {
                var rooms=buildingRooms(frame.topology(),building);
                if(!rooms.isEmpty()) {
                    var room=rooms.get(count%rooms.size());
                    int[] center=roomCenter(frame.topology(),room);
                    boolean entered=squad.currentZoneId()==room.id();
                    for(int zone:squad.memberZoneIds())entered|=zone==room.id();
                    // First enter even an empty room. Once inside, clear the entire
                    // authored room rather than completing an attack move at its corner.
                    // Only own presence and public geometry choose this task.
                    next=entered ? ObjectiveAssignment.clearZone(squad.squadId(),room.id())
                            : ObjectiveAssignment.attackMove(squad.squadId(),center[0],center[1]);
                }
            }
            if(old.assignment()!=null && old.assignment().kind()==next.kind()
                    && old.assignment().targetZoneId()==next.targetZoneId()
                    && old.assignment().targetCellX()==next.targetCellX() && old.assignment().targetCellY()==next.targetCellY()
                    && old.assignment().targetRadiusCells()==next.targetRadiusCells()) next=old.assignment();
            proposals.add(CommandProposal.assign(next,CommandAuthority.MISSION_COMMAND,"SALVAGE_"+frame.phase,
                    next==old.assignment()?CommandStabilityBreak.NONE:CommandStabilityBreak.CONTEXT_INVALIDATED));
            count++;
        }
        return new CommandPlan<>(faction,strategyId(),"SALVAGE_"+frame.phase,frame.tick(),
                frame.influence()==null?-1:frame.influence().updatedTick(),count,0,List.of(frame.phase.name()),proposals,frame.phase.name());
    }
    static List<CommandTopology.Zone> buildingRooms(CommandTopology topology,PointOfInterest building) {
        var rooms=new ArrayList<CommandTopology.Zone>();
        for(var zone:topology.zones()) {
            boolean bounded=zone.cellCount()>1;
            boolean floor=false;
            for(int cell:zone.cells()) {
                floor|=!topology.isDoorwayCell(cell);
                int x=cell%topology.width(),y=cell/topology.width();
                if(x<building.left || x>building.right || y<building.top || y>building.bottom) {bounded=false;break;}
            }
            if(bounded && floor)rooms.add(zone);
        }
        rooms.sort(Comparator.comparingInt(CommandTopology.Zone::cellCount).reversed().thenComparingInt(CommandTopology.Zone::id));
        return rooms;
    }
    private static int[] roomCenter(CommandTopology topology,CommandTopology.Zone room) {
        int[] cells=room.cells();double x=0,y=0;
        for(int cell:cells){x+=cell%topology.width();y+=cell/topology.width();}
        x/=cells.length;y/=cells.length;
        int best=cells[0];double distance=Double.MAX_VALUE;
        for(int cell:cells){double d=Math.hypot(cell%topology.width()-x,cell/topology.width()-y);if(d<distance){distance=d;best=cell;}}
        return new int[]{best%topology.width(),best/topology.width()};
    }
}
