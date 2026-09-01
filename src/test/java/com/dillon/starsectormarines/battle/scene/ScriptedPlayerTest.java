package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** That a scripted order fires once, on its tick, and lands in the real mailbox. */
class ScriptedPlayerTest {

    private static final int W = 24;
    private static final int H = 12;

    private static SceneBuilder arena() {
        return SceneBuilder.openGround(W, H)
                .unit("far", Faction.DEFENDER, UnitType.MARINE, W - 2, H - 2,
                        spec -> spec.moveSpeed(0f));
    }

    @Test
    void actionsFireOnceOnTheirTickInInsertionOrder() {
        SceneWorld world = arena().build();
        List<String> log = new ArrayList<>();
        ScriptedPlayer player = ScriptedPlayer.on(world)
                .at(3, sim -> log.add("first"))
                .at(1, sim -> log.add("early"))
                .at(3, sim -> log.add("second"));

        assertEquals(3, player.pending());
        player.tick(world.sim(), 0);
        assertEquals(List.of(), log);

        player.tick(world.sim(), 1);
        assertEquals(List.of("early"), log);
        assertEquals(2, player.pending());

        player.tick(world.sim(), 3);
        player.tick(world.sim(), 3);
        assertEquals(List.of("early", "first", "second"), log,
                "a repeated tick must not re-fire, and order is insertion order");
        assertEquals(0, player.pending());
    }

    @Test
    void moveSquadReachesTheRealOrderService() {
        SceneWorld world = arena()
                .squad("alpha").size(3).at(5, 5).done()
                .build();
        BattleSimulation sim = world.sim();
        ScriptedPlayer player = ScriptedPlayer.on(world).moveSquad(0, "alpha", 18, 6);

        player.tick(sim, 0);
        sim.advance(BattleSimulation.TICK_DT);

        SquadMoveOrderService.ActiveOrder order =
                sim.getSquadMoveOrderService().activeOrder(world.squadId("alpha"));
        assertInstanceOf(SquadMoveOrderService.ActiveMoveOrder.class, order);
        assertEquals(18, order.requestedX());
        assertEquals(6, order.requestedY());
    }

    @Test
    void defendAreaReachesTheRealOrderService() {
        SceneWorld world = arena()
                .squad("alpha").size(3).at(5, 5).done()
                .build();
        BattleSimulation sim = world.sim();
        ScriptedPlayer.on(world).defendArea(0, "alpha", 14, 6).tick(sim, 0);
        sim.advance(BattleSimulation.TICK_DT);

        SquadMoveOrderService.ActiveOrder order =
                sim.getSquadMoveOrderService().activeOrder(world.squadId("alpha"));
        SquadMoveOrderService.ActiveDefendAreaOrder defend = assertInstanceOf(
                SquadMoveOrderService.ActiveDefendAreaOrder.class, order);
        assertEquals(14, defend.requestedX());
        assertEquals(SquadMoveOrderService.DEFEND_AREA_RADIUS_CELLS, defend.radiusCells());
    }

    @Test
    void moveMechReachesTheChassisScopedService() {
        SceneWorld world = arena()
                .squad("lance").size(2).at(5, 5)
                .mech(MechVariant.BULWARK, MechRole.BALANCED)
                .done()
                .build();
        BattleSimulation sim = world.sim();
        ScriptedPlayer.on(world).moveMech(0, "lance", 1, 17, 7).tick(sim, 0);
        sim.advance(BattleSimulation.TICK_DT);

        assertNotNull(sim.getMechMoveOrderService()
                        .activeOrder(world.members("lance")[1]),
                "the order is scoped to one chassis, not the squad");
        assertEquals(null, sim.getMechMoveOrderService()
                .activeOrder(world.members("lance")[0]));
    }
}
