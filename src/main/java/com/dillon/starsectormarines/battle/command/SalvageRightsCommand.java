package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.story.SalvageRightsProgress;
import com.dillon.starsectormarines.battle.story.SalvageRightsProgress.Phase;
import com.dillon.starsectormarines.battle.unit.Faction;
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
            if(old.assignment()!=null && old.assignment().kind()==next.kind()
                    && old.assignment().targetCellX()==x && old.assignment().targetCellY()==y
                    && old.assignment().targetRadiusCells()==next.targetRadiusCells()) next=old.assignment();
            proposals.add(CommandProposal.assign(next,CommandAuthority.MISSION_COMMAND,"SALVAGE_"+frame.phase,
                    next==old.assignment()?CommandStabilityBreak.NONE:CommandStabilityBreak.CONTEXT_INVALIDATED));
            count++;
        }
        return new CommandPlan<>(faction,strategyId(),"SALVAGE_"+frame.phase,frame.tick(),
                frame.influence()==null?-1:frame.influence().updatedTick(),count,0,List.of(frame.phase.name()),proposals,frame.phase.name());
    }
}
