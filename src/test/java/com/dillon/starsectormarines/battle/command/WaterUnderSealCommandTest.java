package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.squad.BeliefSource;
import com.dillon.starsectormarines.battle.setup.WaterUnderSealRoadExit;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.WaterUnderSealProgress.Phase;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaterUnderSealCommandTest {
    private static WaterUnderSealCommand.Frame frame(Faction faction, boolean cover, CommandAuthority authority) {
        return frame(faction,cover,authority,false);
    }
    private static WaterUnderSealCommand.Frame frame(Faction faction, boolean cover, CommandAuthority authority, boolean centroidInWall) {
        return frame(faction,cover,authority,centroidInWall,faction == Faction.DEFENDER ? 6 : 2);
    }
    private static WaterUnderSealCommand.Frame frame(Faction faction, boolean cover, CommandAuthority authority, boolean centroidInWall, int living) {
        var grid = new NavigationGrid(176,112);
        for (int y=0;y<112;y++) for (int x=0;x<176;x++) grid.setWalkableFloor(x,y);
        if (centroidInWall) grid.setWalkable(89,73,false);
        var graph = new ZoneGraph(grid);
        var view = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),new Class<?>[]{BattleView.class},
                (proxy,method,args) -> switch (method.getName()) { case "getGrid" -> grid; case "getZoneGraph" -> graph; default -> throw new AssertionError(method.getName()); });
        var directive = new CommandDirective(3,faction,authority == CommandAuthority.MISSION_COMMAND ? WaterUnderSealCommand.issuer(faction) : "player",
                authority,"fixture",null,0,-1,CommandDirective.Status.ACTIVE,"");
        var squad = new CommandSquadState(3,faction,living,89,73,centroidInWall ? 90 : 89,73,0,UnitRole.PATROL,false,null,null,directive,0,new int[0]);
        var base = new CommandFrame(75,faction,List.of(squad),null,CommandTopology.freeze(view),new CommandAssignmentSnapshot(Map.of()));
        return new WaterUnderSealCommand.Frame(base,Phase.NORTH_ROOM,81,75,3,List.of(1,2),cover);
    }
    @Test void techniciansTravelIntoTheWorkRoomOnlyAfterCompanyCoverArrives() {
        var command = new WaterUnderSealCommand(Faction.ALLY);
        var waiting = command.plan(frame(Faction.ALLY,false,CommandAuthority.MISSION_COMMAND)).proposals().get(0).assignment();
        assertEquals(AssignmentKind.DEFEND_AREA,waiting.kind());
        assertEquals(89,waiting.targetCellX());
        var working = command.plan(frame(Faction.ALLY,true,CommandAuthority.MISSION_COMMAND)).proposals().get(0).assignment();
        assertEquals(AssignmentKind.ATTACK_MOVE,working.kind());
        assertEquals(81,working.targetCellX()); assertEquals(75,working.targetCellY());
    }
    @Test void technicianTravelUsesAnActualMemberAnchorWhenItsCentroidFallsInsideAWall() {
        var assignment = new WaterUnderSealCommand(Faction.ALLY).plan(frame(Faction.ALLY,true,CommandAuthority.MISSION_COMMAND,true))
                .proposals().get(0).assignment();
        assertEquals(AssignmentKind.ATTACK_MOVE,assignment.kind());
        assertEquals(81,assignment.targetCellX()); assertEquals(75,assignment.targetCellY());
    }
    @Test void companyClearsItsReportedSwitchApproachBeforeReturningToInteriorWorkCover() {
        var base = frame(Faction.MARINE,true,CommandAuthority.MISSION_COMMAND);
        var guard = new CommanderContact(8,84,30,75,1,1,BeliefSource.DIRECT,1);
        var reported = new WaterUnderSealCommand.Frame(base,Phase.SOUTH_ROOM,81,42,3,List.of(1,2),true,List.of(guard));
        var assignment = new WaterUnderSealCommand(Faction.MARINE).plan(reported).proposals().get(0).assignment();
        assertEquals(AssignmentKind.DEFEND_AREA,assignment.kind());
        assertTrue(Math.hypot(assignment.targetCellX()-84,assignment.targetCellY()-30)<12);
        assertTrue(Math.hypot(assignment.targetCellX()-81,assignment.targetCellY()-42)<16);
        assertNotEquals(42,assignment.targetCellY());
    }
    @Test void playerInterventionKeepsAuthorityOverTheCompany() {
        var proposal = new WaterUnderSealCommand(Faction.MARINE).plan(frame(Faction.MARINE,true,CommandAuthority.PLAYER_INTERVENTION))
                .proposals().get(0);
        assertEquals(CommandProposal.Action.RETAIN,proposal.action());
    }
    @Test void mauledRoadElementReceivesPhysicalWithdrawalInsteadOfAnotherAttack() {
        var assignment = new WaterUnderSealCommand(Faction.DEFENDER).plan(frame(Faction.DEFENDER,true,CommandAuthority.MISSION_COMMAND,false,2))
                .proposals().get(0).assignment();
        assertEquals(AssignmentKind.WITHDRAW,assignment.kind());
        assertEquals(WaterUnderSealRoadExit.X,assignment.targetCellX());
        assertEquals(WaterUnderSealRoadExit.Y,assignment.targetCellY());
    }
    @Test void commissionedGunHasCompanyScreensBehindItsOwnFireLine() {
        var base = frame(Faction.MARINE,true,CommandAuthority.MISSION_COMMAND);
        var attack = new WaterUnderSealCommand.Frame(base,Phase.ATTACK,108,88,3,List.of(1,2),true);
        var assignment = new WaterUnderSealCommand(Faction.MARINE).plan(attack).proposals().get(0).assignment();
        assertEquals(99,assignment.targetCellX());
        assertEquals(83,assignment.targetCellY());
        assertTrue(Math.hypot(assignment.targetCellX()-108,assignment.targetCellY()-88)<20);
    }
    @Test void committedPiratesAttackTheirPublicRoadPosition() {
        var assignment = new WaterUnderSealCommand(Faction.DEFENDER).plan(frame(Faction.DEFENDER,true,CommandAuthority.MISSION_COMMAND))
                .proposals().get(0).assignment();
        assertEquals(AssignmentKind.ATTACK_MOVE,assignment.kind());
        assertEquals(111,assignment.targetCellX()); assertEquals(88,assignment.targetCellY());
    }
}
