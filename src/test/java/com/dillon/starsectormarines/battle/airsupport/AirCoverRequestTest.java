package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.airsupport.AirPoolService.Outcome;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A request for cover: what the director refuses, and what the pool's ledger shows once it is accepted. */
class AirCoverRequestTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledWeaponSlots.install();
        InstalledVanillaData.install();
    }

    @AfterAll
    static void release() {
        TurretSlotResolver.useHullSpecs(null);
    }

    private static BattleSimulation sim(int fighters) {
        SceneBuilder builder = SceneBuilder.openGround(80, 40).missionCompletion(false);
        builder.squad("observers").faction(Faction.MARINE).size(2).at(10, 20).primary("weapon.smg")
                .special("special.comms-radio", 1).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, 10, 20)).done();
        builder.squad("garrison").faction(Faction.DEFENDER).size(2).at(70, 5).primary("weapon.smg").stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, 70, 5)).done();
        SceneWorld world = builder.build();
        world.sim().setAirPool(new AirPoolService(AirPoolDeclaration.NONE.with(Faction.MARINE,
                AirPoolAllowance.of(new AirPoolStock(0, 0, 0f), new AirPoolStock(fighters, fighters, 0f), false))));
        return world.sim();
    }

    @Test
    void theLastFighterIsKeptBackForAStrafe() {
        BattleSimulation sim = sim(1);
        assertEquals(Outcome.NO_AIRFRAME,
                sim.airPool().director(Faction.MARINE).cover(sim, sim.airPool(), 12.5f, 20.5f, u -> true));
        assertEquals(1, sim.airPool().available(Faction.MARINE, AirPoolKind.FIGHTER));
    }

    @Test
    void anAcceptedRequestReservesAFighterAndIsTalliedAsCover() {
        BattleSimulation sim = sim(2);
        AirPoolService pool = sim.airPool();
        assertEquals(Outcome.CALLING, pool.director(Faction.MARINE).cover(sim, pool, 12.5f, 20.5f, u -> true));
        assertEquals(1, pool.available(Faction.MARINE, AirPoolKind.FIGHTER));
        assertEquals(1, pool.tally(Faction.MARINE, AirMission.COVER).called());
        assertEquals(0, pool.tally(Faction.MARINE, AirMission.STRAFE).called());
    }

    @Test
    void aSecondRequestOverTheSameGroundIsRefusedWhileTheFirstStands() {
        BattleSimulation sim = sim(3);
        AirPoolService pool = sim.airPool();
        assertEquals(Outcome.CALLING, pool.director(Faction.MARINE).cover(sim, pool, 12.5f, 20.5f, u -> true));
        Outcome second = pool.director(Faction.MARINE).cover(sim, pool, 15.5f, 20.5f, u -> true);
        assertTrue(second == Outcome.ALREADY_COVERED || second == Outcome.NO_OPERATOR, "was " + second);
        assertEquals(2, pool.available(Faction.MARINE, AirPoolKind.FIGHTER));
    }
}
