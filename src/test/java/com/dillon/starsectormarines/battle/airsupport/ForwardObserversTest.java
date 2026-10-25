package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.EnemyConcentration;
import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.evacuation.SwarmBores;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Forward observers on the allied side: a post issues one radio into its formation and never replaces it, the allied
 * officer strikes a mass only one of them has in sight, and the strike respects the company standing by it and the
 * fliers no bomb can reach.
 */
class ForwardObserversTest {

    private static final String CARBINE = "weapon.smg";

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

    private static SceneBuilder garrison(SceneBuilder builder) {
        builder.squad("garrison").faction(Faction.ALLY).type(UnitType.MILITIA).size(3).at(10, 20).primary(CARBINE)
                .stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, 10, 20)).done();
        return builder;
    }

    private static void run(BattleSimulation sim, int ticks) {
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    @Test
    void aPostIssuesOneRadioToASoldierOtherThanTheLeaderAndNeverReplacesIt() {
        SceneWorld world = garrison(SceneBuilder.openGround(40, 40).missionCompletion(false)).build();
        BattleSimulation sim = world.sim();
        int squad = world.squadId("garrison");
        ForwardObservers posts = new ForwardObservers(Faction.ALLY, List.of(s -> List.of(squad)));
        posts.tick(sim);
        long observer = posts.observer(0);
        assertNotEquals(0L, observer);
        assertTrue(CommsKit.carried(sim.world(), observer));
        assertNotEquals(sim.getSquad(squad).leaderId, observer, "the leader keeps his own kit");

        sim.world().setHp(observer, 0f);
        posts.tick(sim);
        assertEquals(observer, posts.observer(0), "a lost observer is lost: the post does not issue again");
        assertEquals(1, posts.posted());
    }

    @Test
    void aPostIgnoresAnotherSidesSquadsAndWaitsForItsOwn() {
        SceneWorld world = garrison(SceneBuilder.openGround(40, 40).missionCompletion(false)).build();
        int squad = world.squadId("garrison");
        assertEquals(0L, ForwardObservers.issue(world.sim(), Faction.MARINE, List.of(squad)));
        assertEquals(0L, ForwardObservers.issue(world.sim(), Faction.ALLY, List.of()));
    }

    /** An allied observer at (10, 20) with a wing on call, and a swarm mass in the open at (34, 20). */
    private static SceneWorld strikeScene(boolean companyBesideTheMass, boolean massIsFliers) {
        SceneBuilder builder = garrison(SceneBuilder.openGround(60, 40).missionCompletion(false));
        builder.squad("mass").faction(Faction.DEFENDER).type(massIsFliers ? UnitType.SWARM_HARRIER : UnitType.MILITIA)
                .size(6).at(34, 20).primary(CARBINE).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, 34, 20)).done();
        if (companyBesideTheMass) {
            builder.squad("company").faction(Faction.MARINE).size(2).at(30, 24).primary(CARBINE).stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, 30, 24)).done();
        }
        SceneWorld world = builder.build();
        int squad = world.squadId("garrison");
        AlliedAirWing.install(world.sim(), 2f, 20f, List.of(s -> List.of(squad)));
        return world;
    }

    /** Strikes the wing was called for, bomb or gun run, over two of the officer's looks. */
    private static int strikesCalled(SceneWorld world) {
        run(world.sim(), AirStrikeDirector.PERIOD_TICKS * 2 + 1);
        AirPoolService pool = world.sim().airPool();
        return pool.tally(Faction.ALLY, AirMission.BOMB).called() + pool.tally(Faction.ALLY, AirMission.STRAFE).called();
    }

    @Test
    void theAlliedOfficerStrikesAMassItsObserverSees() {
        assertEquals(1, strikesCalled(strikeScene(false, false)));
    }

    @Test
    void anAlliedStrikeIsNeverPlacedOnTheCompany() {
        SceneWorld world = strikeScene(true, false);
        assertTrue(world.sim().airPool().director(Faction.ALLY).dangerClose(world.sim(), 34.5f, 20.5f));
        assertEquals(0, strikesCalled(world));
    }

    @Test
    void aPackOfFliersIsNoGroundForABomb() {
        SceneWorld world = strikeScene(false, true);
        assertEquals(0L, EnemyConcentration.densestOnGround(world.sim(), Faction.ALLY, u -> true));
        assertFalse(EnemyConcentration.densest(world.sim(), Faction.ALLY, u -> true) == 0L,
                "the plain count would have taken the pack for a target");
        assertEquals(0, strikesCalled(world));
    }

    @Test
    void aKnownAntiAirMountThreatensAsFarAsItsOwnWeaponReaches() {
        SceneWorld world = SceneBuilder.openGround(60, 40).missionCompletion(false).build();
        BattleSimulation sim = world.sim();
        long maw = sim.spawn(SwarmBores.bodySpec("maw colony", "structure.spore-colony-maw", 40, 20));
        long fissure = sim.spawn(SwarmBores.bodySpec("fissure colony", "structure.spore-colony-fissure", 20, 36));
        // Twenty cells off: past the bubble, inside a maw-grade colony's reach and outside a fissure-grade one's.
        assertEquals(1, AaRisk.at(sim, Faction.ALLY, 20.5f, 20.5f, u -> true).antiAirMounts());
        assertEquals(0, AaRisk.at(sim, Faction.ALLY, 20.5f, 20.5f, u -> u != maw).guns(),
                "a colony nobody has seen is a surprise, and the fissure is out of its reach");
        assertTrue(sim.world().isAlive(fissure));
    }

    @Test
    void groundWhereTheSideLostAnAirframeIsBelievedDefendedForAWhile() {
        AirStrikeDirector officer = new AirStrikeDirector(Faction.ALLY);
        officer.noteLoss(100f, 50f, 0);
        assertEquals(1f, officer.lossesNear(110f, 50f, 30));
        assertEquals(0f, officer.lossesNear(140f, 50f, 30), "far from where it fell");
        assertEquals(0f, officer.lossesNear(110f, 50f, AirStrikeDirector.LOSS_TICKS + 1), "forgotten after a while");
    }
}
