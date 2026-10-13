package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.SalvageRightsProgress.Phase;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SalvageRightsCommandTest {
    private static final PointOfInterest BUILDING=new PointOfInterest(PointOfInterest.Kind.DEPOT,3,3,13,9,2,6,4,4);
    private static CommandTopology topology() {
        var grid=new NavigationGrid(18,14);
        for(int y=0;y<14;y++)for(int x=0;x<18;x++)grid.setWalkableFloor(x,y);
        for(int y=3;y<=9;y++)for(int x=3;x<=13;x++)grid.setWalkable(x,y,x!=3 && x!=13 && y!=3 && y!=9);
        grid.setWalkableFloor(3,6);grid.setDoorway(3,6,true);
        var graph=new ZoneGraph(grid);
        graph.rebuild();
        var view=(BattleView)Proxy.newProxyInstance(BattleView.class.getClassLoader(),new Class<?>[]{BattleView.class},
                (proxy,method,args)->switch(method.getName()) {
                    case "getGrid"->grid;case "getZoneGraph"->graph;
                    default->throw new AssertionError(method.getName());
                });
        return CommandTopology.freeze(view);
    }
    private static CommandSquadState squad(int zone,ObjectiveAssignment old,CommandAuthority authority) {
        var directive=new CommandDirective(1,Faction.MARINE,SalvageRightsCommand.issuer(Faction.MARINE),authority,"fixture",old,0,-1,CommandDirective.Status.ACTIVE,"");
        return new CommandSquadState(1,Faction.MARINE,4,4.5f,4.5f,4,4,zone,UnitRole.PATROL,false,null,old,directive,0,new int[]{zone});
    }
    private static CommandProposal proposal(CommandTopology topology,CommandSquadState squad) {
        var base=new CommandFrame(75,Faction.MARINE,List.of(squad),null,topology,new CommandAssignmentSnapshot(Map.of()));
        return new SalvageRightsCommand(Faction.MARINE,BUILDING,BUILDING,3)
                .plan(new SalvageRightsCommand.Frame(base,Phase.WORKSHOP)).proposals().get(0);
    }
    @Test void companyEntersTheRoomThenClearsItInsteadOfHoldingTheCorner() {
        var topology=topology();int room=topology.zoneIdAt(4,4);
        assertEquals(1,SalvageRightsCommand.buildingRooms(topology,BUILDING).size());
        var approach=proposal(topology,squad(topology.zoneIdAt(1,1),null,CommandAuthority.MISSION_COMMAND)).assignment();
        assertEquals(AssignmentKind.ATTACK_MOVE,approach.kind());
        assertEquals(8,approach.targetCellX());assertEquals(6,approach.targetCellY());
        var clear=proposal(topology,squad(room,approach,CommandAuthority.MISSION_COMMAND)).assignment();
        assertEquals(AssignmentKind.CLEAR_ZONE,clear.kind());assertEquals(room,clear.targetZoneId());
        assertSame(clear,proposal(topology,squad(room,clear,CommandAuthority.MISSION_COMMAND)).assignment());
        assertEquals(AssignmentKind.ATTACK_MOVE,proposal(topology,squad(topology.zoneIdAt(1,1),clear,CommandAuthority.MISSION_COMMAND)).assignment().kind());
    }
    @Test void playerOwnershipIsPreserved() {
        assertEquals(CommandProposal.Action.RETAIN,proposal(topology(),squad(-1,null,CommandAuthority.PLAYER_INTERVENTION)).action());
    }
}
