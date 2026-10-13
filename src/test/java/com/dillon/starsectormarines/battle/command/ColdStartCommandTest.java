package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.ColdStartProgress.Phase;
import com.dillon.starsectormarines.battle.story.ColdStartPicket;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColdStartCommandTest {
    private static CommandTopology topology() {
        var grid=new NavigationGrid(2,2);
        return topology(grid);
    }
    private static CommandTopology topology(NavigationGrid grid) {
        var graph=new ZoneGraph(grid);
        var view=(BattleView)Proxy.newProxyInstance(BattleView.class.getClassLoader(),new Class<?>[]{BattleView.class},
                (proxy,method,args)->switch(method.getName()) {
                    case "getGrid"->grid;
                    case "getZoneGraph"->graph;
                    default->throw new AssertionError("Unexpected topology read: "+method.getName());
                });
        return CommandTopology.freeze(view);
    }
    private static CommandSquadState squad(int id,Faction faction,CommandAuthority authority,String issuer,int lease,ObjectiveAssignment old) {
        var directive=new CommandDirective(id,faction,issuer,authority,"fixture",old,0,lease,CommandDirective.Status.ACTIVE,"");
        return new CommandSquadState(id,faction,2,28.4f,78.4f,28,78,0,UnitRole.PATROL,false,null,old,directive,0,new int[0]);
    }
    private static ColdStartCommand.Frame frame(Faction faction,Phase phase,boolean cover,CommandSquadState... squads) {
        var base=new CommandFrame(75,faction,List.of(squads),null,topology(),new CommandAssignmentSnapshot(Map.of()));
        return new ColdStartCommand.Frame(base,phase,171,117,cover);
    }
    @Test void techniciansHoldTheirActualPositionWithoutCompanyAndAdvanceAfterCoverageArrives() {
        var command=new ColdStartCommand(Faction.ALLY,3);
        var crew=squad(3,Faction.ALLY,CommandAuthority.MISSION_COMMAND,command.strategyId(),-1,null);
        for(var phase:List.of(Phase.ESCORT,Phase.POWER,Phase.CONSTRUCTION,Phase.REPAIR)) {
            var held=command.plan(frame(Faction.ALLY,phase,false,crew)).proposals().get(0).assignment();
            assertEquals(28,held.targetCellX());assertEquals(78,held.targetCellY());
            assertEquals(AssignmentKind.DEFEND_AREA,held.kind());
        }
        var power=command.plan(frame(Faction.ALLY,Phase.POWER,true,crew)).proposals().get(0).assignment();
        assertEquals(171,power.targetCellX());assertEquals(117,power.targetCellY());
        var bay=command.plan(frame(Faction.ALLY,Phase.CONSTRUCTION,true,crew)).proposals().get(0).assignment();
        assertEquals(147,bay.targetCellX());assertEquals(121,bay.targetCellY());
    }
    @Test void repairScreensUseConnectedExteriorCellsWhenSuggestedCellIsBlocked() {
        var grid=new NavigationGrid(180,140);
        for(int y=0;y<140;y++)for(int x=0;x<180;x++)grid.setWalkableFloor(x,y);
        for(int y=105;y<=124;y++)for(int x=119;x<=142;x++)grid.setWalkable(x,y,false);
        grid.setWalkable(151,116,false);
        var topology=topology(grid);
        var command=new ColdStartCommand(Faction.MARINE,3);
        var marine=squad(2,Faction.MARINE,CommandAuthority.MISSION_COMMAND,command.strategyId(),-1,null);
        var screen=ColdStartCommand.exteriorScreen(topology,marine,151,116);
        assertTrue(screen[0]>=145);assertTrue(grid.isWalkable(screen[0],screen[1]));
        assertTrue(topology.reachable(28,78,screen[0],screen[1]));
        assertFalse(screen[0]==151 && screen[1]==116);
        var base=new CommandFrame(75,Faction.MARINE,List.of(marine),null,topology,new CommandAssignmentSnapshot(Map.of()));
        var repair=command.plan(new ColdStartCommand.Frame(base,Phase.REPAIR,147,115,true)).proposals().get(0).assignment();
        assertArrayEquals(screen,new int[]{repair.targetCellX(),repair.targetCellY()});
        assertEquals(AssignmentKind.DEFEND_AREA,repair.kind());
        var construction=command.plan(new ColdStartCommand.Frame(base,Phase.CONSTRUCTION,154,106,true)).proposals().get(0).assignment();
        assertEquals(AssignmentKind.ATTACK_MOVE,construction.kind());
        assertEquals(155,construction.targetCellX());assertEquals(100,construction.targetCellY());
        assertTrue(grid.isWalkable(construction.targetCellX(),construction.targetCellY()));
        assertTrue(topology.reachable(28,78,construction.targetCellX(),construction.targetCellY()));
    }
    @Test void playerControlIsRetainedUntilLeaseExpiresAndUnchangedMissionOrdersKeepIdentity() {
        var command=new ColdStartCommand(Faction.MARINE,3);
        var player=squad(1,Faction.MARINE,CommandAuthority.PLAYER_INTERVENTION,"player",100,null);
        assertEquals(CommandProposal.Action.RETAIN,command.plan(frame(Faction.MARINE,Phase.ESCORT,true,player)).proposals().get(0).action());
        var expired=squad(1,Faction.MARINE,CommandAuthority.PLAYER_INTERVENTION,"player",74,null);
        assertEquals(CommandProposal.Action.ASSIGN,command.plan(frame(Faction.MARINE,Phase.ESCORT,true,expired)).proposals().get(0).action());
        var old=ObjectiveAssignment.attackMove(1,171,117);
        var incumbent=squad(1,Faction.MARINE,CommandAuthority.MISSION_COMMAND,command.strategyId(),-1,old);
        var proposal=command.plan(frame(Faction.MARINE,Phase.ESCORT,true,incumbent)).proposals().get(0);
        assertSame(old,proposal.assignment());assertEquals(CommandStabilityBreak.NONE,proposal.stabilityBreak());
    }
    @Test void registeredPicketRetainsItsOuterPostAcrossPhasesAndWithdrawsToItsOwnInnerRally() {
        var command = new ColdStartCommand(Faction.ALLY, 3);
        var post = new ColdStartPicket("south_picket", 7, 176, 38, 151, 96, 3);
        var old = ObjectiveAssignment.defendArea(7, 176, 38, 3);
        var militia = squad(7, Faction.ALLY, CommandAuthority.MISSION_COMMAND, command.strategyId(), -1, old);
        var base = new CommandFrame(75, Faction.ALLY, List.of(militia), null, topology(), new CommandAssignmentSnapshot(Map.of()));
        for (var phase : List.of(Phase.ESCORT, Phase.POWER, Phase.CONSTRUCTION, Phase.REPAIR)) {
            var proposal = command.plan(new ColdStartCommand.Frame(base, phase, 171, 117, true, List.of(post.report()))).proposals().get(0);
            assertSame(old, proposal.assignment());
            assertEquals(CommandStabilityBreak.NONE, proposal.stabilityBreak());
        }
        post.observe(60, 2, 0, true, false);
        var withdrawing = command.plan(new ColdStartCommand.Frame(base, Phase.REPAIR, 147, 115, true, List.of(post.report()))).proposals().get(0).assignment();
        assertEquals(151, withdrawing.targetCellX()); assertEquals(96, withdrawing.targetCellY());
        assertEquals(AssignmentKind.WITHDRAW, withdrawing.kind());
        post.observe(70, 2, 2, false, false);
        var arrived = command.plan(new ColdStartCommand.Frame(base, Phase.REPAIR, 147, 115, true, List.of(post.report()))).proposals().get(0).assignment();
        assertEquals(AssignmentKind.DEFEND_AREA, arrived.kind());
        assertEquals(151, arrived.targetCellX()); assertEquals(96, arrived.targetCellY());
        post.observe(71, 2, 0, true, false);
        var player = squad(7, Faction.ALLY, CommandAuthority.PLAYER_INTERVENTION, "player", 100, old);
        var leased = new CommandFrame(75, Faction.ALLY, List.of(player), null, topology(), new CommandAssignmentSnapshot(Map.of()));
        assertEquals(CommandProposal.Action.RETAIN, command.plan(new ColdStartCommand.Frame(leased, Phase.REPAIR, 147, 115, true, List.of(post.report()))).proposals().get(0).action());
    }
}
