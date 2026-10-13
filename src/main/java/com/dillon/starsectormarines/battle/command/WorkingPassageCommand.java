package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.setup.WorkingPassageSequence;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.story.WorkingPassageProgress.Phase;
import com.dillon.starsectormarines.battle.tender.TenderSystem;
import com.dillon.starsectormarines.battle.unit.Faction;
import java.util.ArrayList;
import java.util.List;

/** Public successive holds with stable militia duties and preserved player intervention. */
public final class WorkingPassageCommand implements AutonomousMissionCommand<WorkingPassageCommand.Frame,String> {
    public static final class Frame extends CommandFrame {
        final Phase phase;
        final int x,y;
        final List<Integer> militia;
        final List<WorkingPassageSequence.RoadTask> road;
        final List<CommanderContact> contacts;
        Frame(CommandFrame base,Phase phase,int x,int y,List<Integer> militia,List<WorkingPassageSequence.RoadTask> road){
            this(base,phase,x,y,militia,road,base.influence()==null?List.of():base.influence().contacts());
        }
        Frame(CommandFrame base,Phase phase,int x,int y,List<Integer> militia,List<WorkingPassageSequence.RoadTask> road,List<CommanderContact> contacts){
            super(base.tick(),base.perspective(),base.squads(),base.influence(),base.topology(),base.assignments());
            this.phase=phase;this.x=x;this.y=y;this.militia=militia;this.road=road;
            this.contacts=List.copyOf(contacts);
        }
    }
    public static CommandFrameDisclosure<Frame> disclosure(WorkingPassageSequence sequence){
        return(view,faction,topology,assignments)->new Frame(CommandFrame.freeze(view,faction,topology,assignments),
                sequence.progress().phase(),sequence.targetX(),sequence.targetY(),sequence.militiaSquadIds(),sequence.roadTasks());
    }
    private final Faction faction;
    public WorkingPassageCommand(Faction faction){this.faction=faction;}
    public static String issuer(Faction faction){return "working-passage:"+faction;}
    public Faction faction(){return faction;}
    public String strategyId(){return issuer(faction);}
    public void publish(CommanderSnapshot<String> snapshot){}
    public CommandPlan<String> plan(Frame frame){
        var proposals=new ArrayList<CommandProposal>();int count=0;
        for(var squad:frame.squads()){
            if(squad.aliveMembers()<=0)continue;
            var old=squad.directive();
            boolean owned=old!=null&&(old.authority()==CommandAuthority.MISSION_COMMAND&&strategyId().equals(old.issuer())
                    ||old.authority()==CommandAuthority.PLAYER_INTERVENTION&&old.leaseUntilTick()>=0&&old.leaseUntilTick()<frame.tick());
            if(!owned){proposals.add(CommandProposal.retain(squad.squadId(),CommandAuthority.MISSION_COMMAND,"EXTERNAL_OWNERSHIP_PRESERVED"));continue;}
            int x=frame.x,y=frame.y,radius=5;boolean advance=faction!=Faction.ALLY,screen=false;
            if(faction==Faction.MARINE){
                int flank=squad.squadId()%2==0?1:-1;
                x+=flank*5;y+=flank*5;
                // A work escort occupies a bounded frontage even while traveling to it;
                // attack-move contact prosecution may pursue beyond the protected worksite.
                advance=false;
                if(frame.phase!=Phase.DEPARTURE&&frame.phase!=Phase.COMPLETE&&frame.phase!=Phase.FAILED){
                    var threat=constructionThreat(frame.contacts,frame.x,frame.y);
                    if(threat!=null){
                        int[] point=screenPoint(frame.x,frame.y,threat,flank,old.assignment());
                        x=point[0];y=point[1];radius=4;screen=true;
                    }
                }
            }else if(faction==Faction.ALLY){
                int team=frame.militia.indexOf(squad.squadId());
                boolean bendHeld=frame.phase.ordinal()>=Phase.NARROWS.ordinal(),narrowsHeld=frame.phase.ordinal()>=Phase.CLIMB_OUT.ordinal();
                if(team==0){x=bendHeld?201:82;y=bendHeld?132:124;}
                else{x=narrowsHeld?248:bendHeld?210:158;y=narrowsHeld?121:bendHeld?137:124;}
                radius=4;
            }else {
                for(var task:frame.road)if(task.squadId()==squad.squadId()){x=task.x();y=task.y();break;}
            }
            // The authored purpose survives a blocked exact cell; public topology chooses nearby ground.
            int bestDistance=Integer.MAX_VALUE,bestX=Math.round(squad.centroidX()),bestY=Math.round(squad.centroidY());
            for(int cy=y-7;cy<=y+7;cy++)for(int cx=x-7;cx<=x+7;cx++){
                int d=(cx-x)*(cx-x)+(cy-y)*(cy-y);
                if(d<bestDistance&&(!screen||Math.hypot(cx-frame.x,cy-frame.y)<16)
                        &&frame.topology().isWalkable(cx,cy)&&frame.topology().reachable((int)squad.centroidX(),(int)squad.centroidY(),cx,cy)){
                    bestDistance=d;bestX=cx;bestY=cy;
                }
            }
            x=bestX;y=bestY;
            var next=advance?ObjectiveAssignment.attackMove(squad.squadId(),x,y):ObjectiveAssignment.defendArea(squad.squadId(),x,y,radius);
            if(old.assignment()!=null&&old.assignment().kind()==next.kind()&&old.assignment().targetCellX()==x&&old.assignment().targetCellY()==y
                    &&old.assignment().targetRadiusCells()==next.targetRadiusCells())next=old.assignment();
            proposals.add(CommandProposal.assign(next,CommandAuthority.MISSION_COMMAND,"WORKING_PASSAGE_"+frame.phase,
                    next==old.assignment()?CommandStabilityBreak.NONE:CommandStabilityBreak.CONTEXT_INVALIDATED));count++;
        }
        return new CommandPlan<>(faction,strategyId(),"WORKING_PASSAGE_"+frame.phase,frame.tick(),
                frame.influence()==null?-1:frame.influence().updatedTick(),count,0,List.of(frame.phase.name()),proposals,frame.phase.name());
    }
    /** A worksite screen closes rifle reach while staying with Morrow instead of chasing the enemy. */
    static int[] screenPoint(int x,int y,CommanderContact threat,int flank,ObjectiveAssignment old){
        double dx=threat.cellX()-x,dy=threat.cellY()-y;
        double distance=Math.hypot(dx,dy),scale=Math.max(1,distance);
        double axisX=dx/scale,axisY=dy/scale;
        double forward=Math.min(14,distance*.55);
        if(old!=null&&old.kind()==AssignmentKind.DEFEND_AREA&&old.targetRadiusCells()==4){
            double oldX=old.targetCellX()-x,oldY=old.targetCellY()-y;
            double projection=oldX*axisX+oldY*axisY;
            double lateral=(-oldX*axisY+oldY*axisX)*flank;
            // Preserve useful frontage through small changes to the contact bearing.
            if(Math.hypot(oldX,oldY)<16&&projection>=forward*.8&&projection<distance
                    &&lateral>=1&&lateral<=8)return new int[]{old.targetCellX(),old.targetCellY()};
        }
        return new int[]{(int)Math.round(x+axisX*forward-axisY*flank*4),
                (int)Math.round(y+axisY*forward+axisX*flank*4)};
    }
    /** Clear the actual builder safety footprint from faction reports, then resume the hold. */
    static CommanderContact constructionThreat(List<CommanderContact> contacts,int x,int y){
        CommanderContact nearest=null;double best=TenderSystem.THREAT_CELLS*TenderSystem.THREAT_CELLS;
        for(var contact:contacts){
            double dx=contact.cellX()-x,dy=contact.cellY()-y;
            double distance=dx*dx+dy*dy;
            if(distance<=best&&(nearest==null||distance<best||contact.unitId()<nearest.unitId())){
                nearest=contact;best=distance;
            }
        }
        return nearest;
    }
}
