package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService.EventType;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService.Outcome;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The air pool's ledger and the gate on who can task it: regeneration to the establishment, an operator who must be
 * alive, unsuppressed and sure of the ground, an airframe that is reserved at the call and comes back or is lost at
 * the end of its sortie.
 */
class AirPoolServiceTest {

    private static final String CARBINE = "weapon.smg";
    private static final int W = 80;
    private static final int H = 40;

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

    private static AirPoolDeclaration pool(int bombers, int cap, float regen) {
        return AirPoolDeclaration.NONE.with(Faction.MARINE,
                AirPoolAllowance.of(new AirPoolStock(bombers, cap, regen), new AirPoolStock(1, 1, regen), false));
    }

    /** A scene with one radio operator at (10, 20), a defender at (40, 20), and the given pool installed. */
    private static SceneWorld scene(AirPoolDeclaration declaration, boolean wallBetween) {
        SceneBuilder builder = SceneBuilder.openGround(W, H).missionCompletion(false);
        builder.squad("observers").faction(Faction.MARINE).size(2).at(10, 20).primary(CARBINE)
                .special("special.comms-radio", 1).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, 10, 20)).done();
        builder.squad("garrison").faction(Faction.DEFENDER).size(4).at(40, 20).primary(CARBINE).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, 40, 20)).done();
        if (wallBetween) builder.wall(25, 0, 26, H - 1);
        SceneWorld world = builder.build();
        world.sim().setAirPool(new AirPoolService(declaration));
        return world;
    }

    private static long operator(SceneWorld world) {
        return world.members("observers")[0];
    }

    private static void run(BattleSimulation sim, int ticks) {
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    @Test
    void regeneratesToTheEstablishmentAndNoFurther() {
        SceneWorld world = scene(pool(0, 2, 10f), false);
        BattleSimulation sim = world.sim();
        AirPoolService pool = sim.airPool();
        assertEquals(0, pool.available(Faction.MARINE, AirPoolKind.BOMBER));
        run(sim, 29 * 10);
        assertEquals(0, pool.available(Faction.MARINE, AirPoolKind.BOMBER), "not yet: ten seconds have not passed");
        run(sim, 3 * 10);
        assertEquals(1, pool.available(Faction.MARINE, AirPoolKind.BOMBER));
        run(sim, 30 * 30);
        assertEquals(2, pool.available(Faction.MARINE, AirPoolKind.BOMBER), "stops at the establishment");
    }

    @Test
    void aSideWithNoDeclaredRegenerationNeverReplacesALoss() {
        SceneWorld world = scene(pool(1, 2, 0f), false);
        run(world.sim(), 30 * 120);
        assertEquals(1, world.sim().airPool().available(Faction.MARINE, AirPoolKind.BOMBER));
    }

    @Test
    void anOperatorWithSightCanTaskAndNobodyElseCan() {
        SceneWorld world = scene(pool(1, 1, 0f), false);
        BattleSimulation sim = world.sim();
        assertEquals(Outcome.CALLING,
                sim.airPool().request(sim, Faction.MARINE, AirPoolKind.BOMBER, 30.5f, 20.5f, false));
        // The only operator is now on the net: the same side cannot place a second call at once.
        assertEquals(Outcome.NO_AIRFRAME,
                sim.airPool().request(sim, Faction.MARINE, AirPoolKind.BOMBER, 31.5f, 20.5f, false));
        assertEquals(Outcome.NO_AIRFRAME,
                sim.airPool().request(sim, Faction.DEFENDER, AirPoolKind.BOMBER, 10.5f, 20.5f, true),
                "a side with no pool has nothing to task");
    }

    @Test
    void aWallBetweenTheOperatorAndTheGroundMakesItUnseenUntilSomeoneBelievesIt() {
        SceneWorld world = scene(pool(1, 1, 0f), true);
        BattleSimulation sim = world.sim();
        assertNull(CommsOperators.best(sim, Faction.MARINE, 40.5f, 20.5f, false),
                "no line to the ground and no belief: an operator cannot designate what it cannot see");
        assertNotNull(CommsOperators.best(sim, Faction.MARINE, 40.5f, 20.5f, true),
                "a belief the caller vouches for, inside radio range, is enough");
        assertNull(CommsOperators.best(sim, Faction.MARINE, 10.5f + 150f, 20.5f, true), "beyond radio range");
    }

    @Test
    void aDeadOperatorCannotTask() {
        SceneWorld world = scene(pool(1, 1, 0f), false);
        BattleSimulation sim = world.sim();
        sim.applyDamage(operator(world), 10_000f, 100f);
        run(sim, 2);
        assertNull(CommsOperators.best(sim, Faction.MARINE, 30.5f, 20.5f, false));
        assertEquals(Outcome.NO_OPERATOR,
                sim.airPool().request(sim, Faction.MARINE, AirPoolKind.BOMBER, 30.5f, 20.5f, false));
        assertEquals(1, sim.airPool().available(Faction.MARINE, AirPoolKind.BOMBER), "nothing was reserved");
    }

    @Test
    void anOperatorUnderHeavyFireIsTooBusyToWorkTheRadio() {
        SceneWorld world = scene(pool(1, 1, 0f), false);
        BattleSimulation sim = world.sim();
        long op = operator(world);
        assertNotNull(CommsOperators.best(sim, Faction.MARINE, 30.5f, 20.5f, false));
        for (int i = 0; i < 4; i++) sim.getRoster().combat().recordIncomingFire(op, 40, 20, sim.getSimTickIndex());
        assertNull(CommsOperators.best(sim, Faction.MARINE, 30.5f, 20.5f, false), "suppressed");
    }

    @Test
    void aCallDroppedBecauseTheOperatorDiedHandsTheAirframeBack() {
        SceneWorld world = scene(pool(1, 1, 0f), false);
        BattleSimulation sim = world.sim();
        AirPoolService pool = sim.airPool();
        assertEquals(Outcome.CALLING, pool.request(sim, Faction.MARINE, AirPoolKind.BOMBER, 30.5f, 20.5f, false));
        assertEquals(0, pool.available(Faction.MARINE, AirPoolKind.BOMBER), "reserved at the call");
        sim.applyDamage(operator(world), 10_000f, 100f);
        run(sim, 30 * 6);
        assertEquals(1, pool.available(Faction.MARINE, AirPoolKind.BOMBER), "the call never went out");
        assertEquals(0, sim.getAirEntityIds().length, "no aircraft was launched");
        assertEquals(EventType.CALL_DROPPED, pool.events().get(pool.events().size() - 1).type());
    }

    @Test
    void aSortieThatLandsReturnsAndOneShotDownIsLostUntilRegeneration() {
        SceneWorld world = scene(pool(2, 2, 20f), false);
        BattleSimulation sim = world.sim();
        AirPoolService pool = sim.airPool();
        assertEquals(Outcome.CALLING, pool.request(sim, Faction.MARINE, AirPoolKind.BOMBER, 30.5f, 20.5f, false));
        run(sim, 30 * 5);
        long[] air = sim.getAirEntityIds();
        assertEquals(1, air.length, "the call went out and an aircraft is flying");
        ShuttleMission mission = sim.world().mission(air[0]);
        assertTrue(mission.poolSortie && mission.strikeSortie);
        assertEquals(1, pool.outFlying(Faction.MARINE, AirPoolKind.BOMBER));

        // It is shot down: lost to the pool, not handed back.
        mission.destroyed = true;
        run(sim, 2);
        assertEquals(1, pool.lost(Faction.MARINE, AirPoolKind.BOMBER));
        assertEquals(0, pool.outFlying(Faction.MARINE, AirPoolKind.BOMBER));
        assertEquals(1, pool.available(Faction.MARINE, AirPoolKind.BOMBER), "only the one in stock is left");
        // Regeneration replaces it, at the declared rate.
        run(sim, 30 * 21);
        assertEquals(2, pool.available(Faction.MARINE, AirPoolKind.BOMBER));
    }

    @Test
    void aSortieThatGetsHomeComesBackToThePool() {
        SceneWorld world = scene(pool(1, 1, 0f), false);
        BattleSimulation sim = world.sim();
        AirPoolService pool = sim.airPool();
        assertEquals(Outcome.CALLING, pool.request(sim, Faction.MARINE, AirPoolKind.BOMBER, 30.5f, 20.5f, false));
        run(sim, 30 * 5);
        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        assertEquals(0, pool.available(Faction.MARINE, AirPoolKind.BOMBER));
        mission.state = ShuttleState.GONE;
        run(sim, 2);
        assertEquals(1, pool.available(Faction.MARINE, AirPoolKind.BOMBER));
        assertEquals(0, pool.lost(Faction.MARINE, AirPoolKind.BOMBER));
    }

    @Test
    void aBadlyHitSortieBreaksOffAndReturnsRatherThanDroppingItsBombs() {
        SceneWorld world = scene(pool(1, 1, 0f), false);
        BattleSimulation sim = world.sim();
        AirPoolService pool = sim.airPool();
        assertEquals(Outcome.CALLING, pool.request(sim, Faction.MARINE, AirPoolKind.BOMBER, 30.5f, 20.5f, false));
        run(sim, 30 * 5);
        long craft = sim.getAirEntityIds()[0];
        ShuttleMission mission = sim.world().mission(craft);
        assertFalse(mission.aborted);
        // Hit hard on the way in, not destroyed.
        sim.world().setHp(craft, sim.world().airframe(craft).maxHp() * 0.3f);
        run(sim, 2);
        assertTrue(mission.aborted, "below the abort share it breaks off");
        assertTrue(mission.state == ShuttleState.DEPARTING || mission.state == ShuttleState.GONE,
                "turned for home: " + mission.state);
        assertEquals(0, mission.roundsLeftThisPass, "nothing left to drop");
        // It leaves the map and the pool gets the airframe back, counted as driven off.
        run(sim, 30 * 90);
        assertEquals(1, pool.available(Faction.MARINE, AirPoolKind.BOMBER));
        assertEquals(1, pool.readout(Faction.MARINE).stream()
                .filter(r -> r.kind() == AirPoolKind.BOMBER).findFirst().orElseThrow().aborted());
        assertNotEquals(0, pool.events().size());
    }

    @Test
    void aRiskEstimateCountsOnlyTheGunsTheSideHasSightedAndWeightsAntiAirHigher() {
        SceneBuilder builder = SceneBuilder.openGround(W, H).missionCompletion(false);
        builder.squad("garrison").faction(Faction.DEFENDER).size(1).at(60, 30).primary(CARBINE).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, 60, 30)).done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        long mg = sim.spawn(MapTurret.create("mg", Faction.DEFENDER,
                "structure.turret-heavy-mg", 40, 20));
        long aa = sim.spawn(MapTurret.create("aa", Faction.DEFENDER,
                TurretCatalogRegistry.ANTI_AIR_STRUCTURE_ID, 42, 20));
        AaRisk.Assessment none = AaRisk.at(sim, Faction.MARINE, 41f, 20f, id -> false);
        AaRisk.Assessment one = AaRisk.at(sim, Faction.MARINE, 41f, 20f, id -> id == mg);
        AaRisk.Assessment both = AaRisk.at(sim, Faction.MARINE, 41f, 20f, id -> id == mg || id == aa);
        assertEquals(0, none.guns());
        assertEquals(1, one.guns());
        assertEquals(0, one.antiAirMounts());
        assertEquals(2, both.guns());
        assertEquals(1, both.antiAirMounts());
        assertTrue(both.expectedLossShare() > one.expectedLossShare());
        assertTrue(one.expectedLossShare() > none.expectedLossShare());
        assertEquals(AaRisk.NONE, AaRisk.at(sim, Faction.MARINE, 41f, 20f, null));
        assertEquals(0, AaRisk.at(sim, Faction.DEFENDER, 41f, 20f, id -> true).guns(), "a side's own guns are no risk");
    }
}
