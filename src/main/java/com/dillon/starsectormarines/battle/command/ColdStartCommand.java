package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.story.ColdStartProgress.Phase;
import com.dillon.starsectormarines.battle.setup.ColdStartSequence;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import java.util.ArrayList;
import java.util.List;

/** Coordinated escort and work-site coverage from public tasks and own-force reports. */
public final class ColdStartCommand implements AutonomousMissionCommand<ColdStartCommand.Frame, String> {
    public static final class Frame extends CommandFrame {
        final Phase phase;
        final int targetX, targetY;
        final boolean companyCover;
        Frame(CommandFrame base, Phase phase, int targetX, int targetY, boolean companyCover) {
            super(base.tick(), base.perspective(), base.squads(), base.influence(), base.topology(), base.assignments());
            this.phase = phase; this.targetX = targetX; this.targetY = targetY; this.companyCover = companyCover;
        }
    }
    public static CommandFrameDisclosure<Frame> disclosure(ColdStartSequence sequence) {
        return (view, faction, topology, assignments) -> {
            var base=CommandFrame.freeze(view, faction, topology, assignments);
            var phase=sequence.progress().phase();
            int crewX=phase==Phase.ESCORT?82:phase==Phase.POWER?171:147;
            int crewY=phase==Phase.ESCORT?119:phase==Phase.POWER?117:121;
            // Cooperative task coordination shares only friendly company presence, never hostile occupancy.
            boolean covered=faction!=Faction.ALLY || CommandFrame.freeze(view,Faction.MARINE,topology,assignments).squads().stream()
                    .anyMatch(squad->squad.aliveMembers()>0 && squad.role()!=UnitRole.TURRET && squad.role()!=UnitRole.STRUCTURE
                            && squad.role()!=UnitRole.DRONE_HUB && squad.role()!=UnitRole.DRONE_PATROL && Math.hypot(squad.centroidX()-crewX,squad.centroidY()-crewY)<=22);
            return new Frame(base,phase,sequence.targetX(),sequence.targetY(),covered);
        };
    }
    private final Faction faction;
    private final int engineers;
    public ColdStartCommand(Faction faction, int engineers) { this.faction = faction; this.engineers = engineers; }
    public static String issuer(Faction faction) { return "cold-start:" + faction; }
    public Faction faction() { return faction; }
    public String strategyId() { return issuer(faction); }
    public void publish(CommanderSnapshot<String> snapshot) { }
    /** Choose an actual connected exterior cell from the public grid; never aim inside Morrow's hull. */
    static int[] exteriorScreen(CommandTopology topology,CommandSquadState squad,int preferredX,int preferredY) {
        int bestX=Math.round(squad.centroidX()),bestY=Math.round(squad.centroidY()),bestDistance=Integer.MAX_VALUE;
        int startX=(int)squad.centroidX(),startY=(int)squad.centroidY();
        for(int y=preferredY-6;y<=preferredY+6;y++)for(int x=Math.max(145,preferredX-6);x<=preferredX+6;x++) {
            int distance=(x-preferredX)*(x-preferredX)+(y-preferredY)*(y-preferredY);
            if(distance<bestDistance && topology.isWalkable(x,y) && topology.reachable(startX,startY,x,y)) {
                bestDistance=distance;bestX=x;bestY=y;
            }
        }
        return new int[]{bestX,bestY};
    }
    public CommandPlan<String> plan(Frame frame) {
        var proposals = new ArrayList<CommandProposal>();
        int count = 0;
        for (var squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            var old = squad.directive();
            boolean owned = old != null && (old.authority() == CommandAuthority.MISSION_COMMAND && strategyId().equals(old.issuer())
                    || old.authority() == CommandAuthority.PLAYER_INTERVENTION && old.leaseUntilTick() >= 0 && old.leaseUntilTick() < frame.tick());
            if (!owned) {
                proposals.add(CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND, "EXTERNAL_OWNERSHIP_PRESERVED"));
                continue;
            }
            int x = frame.targetX, y = frame.targetY, radius = 5;
            boolean advance = faction != Faction.ALLY;
            if (faction == Faction.MARINE) {
                if (frame.phase != Phase.ESCORT) {
                    advance = frame.phase == Phase.POWER || frame.phase == Phase.CONSTRUCTION;
                    // Sections screen separate approaches; the work cell remains free for technicians.
                    if(frame.phase==Phase.CONSTRUCTION || frame.phase==Phase.REPAIR || frame.phase==Phase.COMPLETE) {
                        x=frame.phase==Phase.CONSTRUCTION?(squad.squadId()%2==0?155:165):(squad.squadId()%2==0?151:159);
                        y=frame.phase==Phase.CONSTRUCTION?(squad.squadId()%2==0?100:109):(squad.squadId()%2==0?116:123);
                        int[] screen=exteriorScreen(frame.topology(),squad,x,y);
                        x=screen[0];y=screen[1];
                    } else {
                        x += squad.squadId() % 2 == 0 ? 5 : -5;
                        y += squad.squadId() % 2 == 0 ? 3 : -3;
                    }
                }
            } else if (faction == Faction.ALLY) {
                if (squad.squadId() == engineers) {
                    radius = 2;
                    if (frame.phase == Phase.ESCORT) { x = 82; y = 119; }
                    else if (frame.phase == Phase.POWER) { x = 171; y = 117; }
                    else { x = 147; y = 121; }
                    if(!frame.companyCover) {
                        x=Math.round(squad.centroidX());y=Math.round(squad.centroidY());
                    }
                } else if (squad.squadId() % 2 == 0) {
                    // One militia section retains the workshop through the entire mobilization.
                    x = 78; y = 124; radius = 3;
                } else {
                    // The second holds delivered cargo, then the rear of the powered pump approach.
                    x = frame.phase == Phase.ESCORT ? 74 : 158;
                    y = frame.phase == Phase.ESCORT ? 113 : 123;
                    radius = 3;
                }
            }
            ObjectiveAssignment next = advance ? ObjectiveAssignment.attackMove(squad.squadId(), x, y)
                    : ObjectiveAssignment.defendArea(squad.squadId(), x, y, radius);
            if (old.assignment() != null && old.assignment().kind() == next.kind()
                    && old.assignment().targetCellX() == x && old.assignment().targetCellY() == y
                    && old.assignment().targetRadiusCells() == next.targetRadiusCells()) next = old.assignment();
            proposals.add(CommandProposal.assign(next, CommandAuthority.MISSION_COMMAND, "COLD_START_" + frame.phase,
                    next == old.assignment() ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED));
            count++;
        }
        return new CommandPlan<>(faction, strategyId(), "COLD_START_" + frame.phase, frame.tick(),
                frame.influence() == null ? -1 : frame.influence().updatedTick(), count, 0,
                List.of(frame.phase.name()), proposals, frame.phase.name());
    }
}
