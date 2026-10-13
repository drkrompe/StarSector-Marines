package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.setup.WorkingPassageSequence.RoadTask;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.BeliefSource;
import com.dillon.starsectormarines.battle.story.WorkingPassageProgress.Phase;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkingPassageCommandTest {
    private static CommandTopology topology(){
        var grid=new NavigationGrid(304,176);
        for(int y=0;y<176;y++)for(int x=0;x<304;x++)grid.setWalkableFloor(x,y);
        var graph=new ZoneGraph(grid);
        var view=(BattleView)Proxy.newProxyInstance(BattleView.class.getClassLoader(),new Class<?>[]{BattleView.class},
                (proxy,method,args)->switch(method.getName()){case "getGrid"->grid;case "getZoneGraph"->graph;default->throw new AssertionError(method.getName());});
        return CommandTopology.freeze(view);
    }
    private static CommandSquadState squad(int id,Faction faction,CommandAuthority authority,int lease){
        return squad(id,faction,authority,lease,null);
    }
    private static CommandSquadState squad(int id,Faction faction,CommandAuthority authority,int lease,ObjectiveAssignment assignment){
        var directive=new CommandDirective(id,faction,authority==CommandAuthority.MISSION_COMMAND?WorkingPassageCommand.issuer(faction):"player",
                authority,"fixture",assignment,0,lease,CommandDirective.Status.ACTIVE,"");
        return new CommandSquadState(id,faction,4,28,78,28,78,0,UnitRole.PATROL,false,null,assignment,directive,0,new int[0]);
    }
    private static WorkingPassageCommand.Frame frame(Faction faction,Phase phase,CommandSquadState... squads){
        var base=new CommandFrame(75,faction,List.of(squads),null,topology(),new CommandAssignmentSnapshot(Map.of()));
        return new WorkingPassageCommand.Frame(base,phase,252,106,List.of(2,3),List.of(new RoadTask(7,209,123),new RoadTask(8,280,77)));
    }
    @Test void playerPersistentAndActiveLeasesSurviveEveryMissionPhase(){
        var command=new WorkingPassageCommand(Faction.MARINE);
        for(var phase:Phase.values())for(int lease:new int[]{-1,100}){
            var result=command.plan(frame(Faction.MARINE,phase,squad(1,Faction.MARINE,CommandAuthority.PLAYER_INTERVENTION,lease)));
            assertEquals(CommandProposal.Action.RETAIN,result.proposals().get(0).action());
        }
        assertEquals(CommandProposal.Action.ASSIGN,command.plan(frame(Faction.MARINE,Phase.NARROWS,
                squad(1,Faction.MARINE,CommandAuthority.PLAYER_INTERVENTION,74))).proposals().get(0).action());
    }
    @Test void militiaTakeSeparateEarnedHoldsAndDoNotRaceToUnsecuredNarrows(){
        var command=new WorkingPassageCommand(Faction.ALLY);
        var one=squad(2,Faction.ALLY,CommandAuthority.MISSION_COMMAND,-1);var two=squad(3,Faction.ALLY,CommandAuthority.MISSION_COMMAND,-1);
        var waiting=command.plan(frame(Faction.ALLY,Phase.BEND_BUILD,one,two)).proposals();
        assertEquals(82,waiting.get(0).assignment().targetCellX());assertEquals(158,waiting.get(1).assignment().targetCellX());
        var pump=command.plan(frame(Faction.ALLY,Phase.NARROWS,one,two)).proposals();
        assertEquals(201,pump.get(0).assignment().targetCellX());assertEquals(210,pump.get(1).assignment().targetCellX());
        var finalHold=command.plan(frame(Faction.ALLY,Phase.CLIMB_OUT,one,two)).proposals();
        assertEquals(201,finalHold.get(0).assignment().targetCellX());assertEquals(248,finalHold.get(1).assignment().targetCellX());
        assertEquals(AssignmentKind.DEFEND_AREA,finalHold.get(1).assignment().kind());
    }
    @Test void roadTeamsRetainTheirNamedApproachTaskInsteadOfFollowingCompanyEverywhere(){
        var command=new WorkingPassageCommand(Faction.DEFENDER);
        var result=command.plan(frame(Faction.DEFENDER,Phase.SPOOL,squad(7,Faction.DEFENDER,CommandAuthority.MISSION_COMMAND,-1),
                squad(8,Faction.DEFENDER,CommandAuthority.MISSION_COMMAND,-1))).proposals();
        assertEquals(209,result.get(0).assignment().targetCellX());assertEquals(123,result.get(0).assignment().targetCellY());
        assertEquals(280,result.get(1).assignment().targetCellX());assertEquals(77,result.get(1).assignment().targetCellY());
    }
    @Test void constructionCoverClearsReportedThreatOutsideTheOldTwentyFourCellHold(){
        var retreatingGuard=new CommanderContact(7,265,128,75,1,1,BeliefSource.DIRECT,1);
        var beyondWorksite=new CommanderContact(8,283,80,75,1,1,BeliefSource.DIRECT,1);
        assertSame(retreatingGuard,WorkingPassageCommand.constructionThreat(List.of(beyondWorksite,retreatingGuard),252,106));
        assertNull(WorkingPassageCommand.constructionThreat(List.of(beyondWorksite),252,106));
        assertNull(WorkingPassageCommand.constructionThreat(List.of(),252,106));
    }
    @Test void escortScreensTheWholeRequiredClearanceBeforeAndDuringConstruction(){
        var command=new WorkingPassageCommand(Faction.MARINE);
        var marine=squad(1,Faction.MARINE,CommandAuthority.MISSION_COMMAND,-1);
        var guard=new CommanderContact(7,265,128,75,1,1,BeliefSource.DIRECT,1);
        for(var phase:List.of(Phase.BEND,Phase.BEND_BUILD,Phase.NARROWS,Phase.NARROWS_BUILD,Phase.CLIMB_OUT,Phase.SPOOL)){
            var ordinary=frame(Faction.MARINE,phase,marine);
            var reported=new WorkingPassageCommand.Frame(ordinary,phase,252,106,ordinary.militia,ordinary.road,List.of(guard));
            var assignment=command.plan(reported).proposals().get(0).assignment();
            assertEquals(AssignmentKind.DEFEND_AREA,assignment.kind(),phase.name());
            assertEquals(4,assignment.targetRadiusCells());
            assertTrue(Math.hypot(assignment.targetCellX()-252,assignment.targetCellY()-106)+assignment.targetRadiusCells()<20);
            assertTrue(Math.hypot(assignment.targetCellX()-265,assignment.targetCellY()-128)<18);
            assertNotEquals(guard.cellY(),assignment.targetCellY());
        }
        var cleared=command.plan(frame(Faction.MARINE,Phase.NARROWS_BUILD,marine)).proposals().get(0).assignment();
        assertEquals(AssignmentKind.DEFEND_AREA,cleared.kind());
        assertEquals(247,cleared.targetCellX());assertEquals(101,cleared.targetCellY());
        var intervened=frame(Faction.MARINE,Phase.NARROWS_BUILD,
                squad(2,Faction.MARINE,CommandAuthority.PLAYER_INTERVENTION,-1),marine);
        var reported=new WorkingPassageCommand.Frame(intervened,Phase.NARROWS_BUILD,252,106,
                intervened.militia,intervened.road,List.of(guard));
        var proposals=command.plan(reported).proposals();
        assertEquals(CommandProposal.Action.RETAIN,proposals.get(0).action());
        assertEquals(AssignmentKind.DEFEND_AREA,proposals.get(1).assignment().kind());
        assertEquals(4,proposals.get(1).assignment().targetRadiusCells());
    }
    @Test void screensKeepSeparateStableFrontageInsteadOfFollowingSmallEnemyMoves(){
        var command=new WorkingPassageCommand(Faction.MARINE);
        var ordinary=frame(Faction.MARINE,Phase.NARROWS_BUILD,
                squad(1,Faction.MARINE,CommandAuthority.MISSION_COMMAND,-1),
                squad(2,Faction.MARINE,CommandAuthority.MISSION_COMMAND,-1));
        var guard=new CommanderContact(7,265,128,75,1,1,BeliefSource.DIRECT,1);
        var reported=new WorkingPassageCommand.Frame(ordinary,Phase.NARROWS_BUILD,252,106,ordinary.militia,ordinary.road,List.of(guard));
        var proposals=command.plan(reported).proposals();
        var left=proposals.get(0).assignment();var right=proposals.get(1).assignment();
        assertTrue(Math.hypot(left.targetCellX()-right.targetCellX(),left.targetCellY()-right.targetCellY())>=6);
        var moved=new CommanderContact(7,266,128,150,1,1,BeliefSource.DIRECT,1);
        var issued=frame(Faction.MARINE,Phase.NARROWS_BUILD,
                squad(1,Faction.MARINE,CommandAuthority.MISSION_COMMAND,-1,left),
                squad(2,Faction.MARINE,CommandAuthority.MISSION_COMMAND,-1,right));
        var next=new WorkingPassageCommand.Frame(issued,Phase.NARROWS_BUILD,252,106,issued.militia,issued.road,List.of(moved));
        var repeated=command.plan(next).proposals();
        assertSame(left,repeated.get(0).assignment());assertSame(right,repeated.get(1).assignment());
        assertEquals(CommandStabilityBreak.NONE,repeated.get(0).stabilityBreak());
    }
}
