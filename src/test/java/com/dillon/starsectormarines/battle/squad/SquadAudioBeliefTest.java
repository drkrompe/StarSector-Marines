package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.mech.MechWeapon;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.perception.NoiseKind;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadAudioBeliefTest {

    @Test
    void directFireCreatesImperfectSourceLinkedBeliefForEitherFaction() {
        assertDirectFireBelief(Faction.MARINE, Faction.DEFENDER);
        assertDirectFireBelief(Faction.DEFENDER, Faction.MARINE);
    }

    @Test
    void oneShotIsConsumedOnceAndDirectSightSupersedesAudio() {
        Fixture fixture = fixture(Faction.MARINE, Faction.DEFENDER);
        fixture.sim.postShot(directShot(fixture.shooter, Faction.DEFENDER));
        fixture.sim.advance(BattleSimulation.TICK_DT);
        BelievedContact heard = fixture.squad.believedContact(fixture.shooter);
        int heardTick = heard.lastSeenTick();
        float heardConfidence = heard.confidence();

        fixture.sim.advance(BattleSimulation.TICK_DT);

        BelievedContact fading = fixture.squad.believedContact(fixture.shooter);
        assertEquals(heardTick, fading.lastSeenTick(),
                "a lingering tracer must not replay its launch noise");
        assertTrue(fading.confidence() < heardConfidence);

        openWall(fixture.sim);
        fixture.sim.advance(BattleSimulation.TICK_DT);
        BelievedContact seen = fixture.squad.believedContact(fixture.shooter);
        assertEquals(BeliefSource.DIRECT, seen.source());
        assertEquals(1f, seen.confidence());
        assertEquals(fixture.sim.world().cellX(fixture.shooter), seen.lastSeenCellX());
        assertEquals(fixture.sim.world().cellY(fixture.shooter), seen.lastSeenCellY());
    }

    @Test
    void friendlyAndIndirectNoiseNeverRevealAHostileSource() {
        Fixture fixture = fixture(Faction.MARINE, Faction.DEFENDER);
        fixture.sim.postShot(directShot(fixture.observer, Faction.MARINE));
        fixture.sim.advance(BattleSimulation.TICK_DT);
        assertTrue(fixture.squad.believedContacts().isEmpty());

        fixture.sim.postShot(new ShotEvent(fixture.shooter,
                9.5f, 5.5f, 12.5f, 5.5f, false, Faction.DEFENDER, 0.1f,
                null, null, null, MechWeapon.LRM_ARTILLERY, 1f));
        fixture.sim.advance(BattleSimulation.TICK_DT);

        assertTrue(fixture.squad.believedContacts().isEmpty());
        assertNotNull(fixture.squad.audibleBearing());
        assertEquals(0L, fixture.squad.audibleBearing().sourceUnitId());
    }

    @Test
    void detonationProducesOnlyAnAnonymousBearing() {
        Fixture fixture = fixture(Faction.MARINE, Faction.DEFENDER);
        fixture.sim.detonateNow(new PendingDetonation(
                CombatTelemetryService.NO_ATTACKER,
                9.5f, 5.5f, 0f, 0f, 0f, 1f,
                0, Faction.DEFENDER, false));

        fixture.sim.advance(BattleSimulation.TICK_DT);

        assertTrue(fixture.squad.believedContacts().isEmpty());
        assertNotNull(fixture.squad.audibleBearing());
        assertEquals(0L, fixture.squad.audibleBearing().sourceUnitId());
        assertEquals(NoiseKind.DETONATION,
                fixture.squad.audibleBearing().kind());
    }

    private static void assertDirectFireBelief(Faction listener, Faction shooterFaction) {
        Fixture fixture = fixture(listener, shooterFaction);
        fixture.sim.postShot(directShot(fixture.shooter, shooterFaction));

        fixture.sim.advance(BattleSimulation.TICK_DT);

        BelievedContact contact = fixture.squad.believedContact(fixture.shooter);
        assertNotNull(contact);
        assertEquals(BeliefSource.AUDIO, contact.source());
        assertTrue(contact.confidence() >= 0.4f && contact.confidence() <= 0.7f);
        assertTrue(contact.lastSeenCellX() != 9 || contact.lastSeenCellY() != 5);
        assertEquals(SquadAlertLevel.SUSPICIOUS, fixture.squad.alertLevel);
        WorldState state = WorldStateBuilder.build(fixture.squad, fixture.sim);
        assertTrue(state.get(Predicate.HAS_TARGET));
        assertFalse(state.get(Predicate.HAS_LOS_TO_TARGET));
    }

    private static ShotEvent directShot(long shooter, Faction faction) {
        return new ShotEvent(shooter, 9.5f, 5.5f, 5.5f, 5.5f,
                false, faction, 0.1f);
    }

    private static Fixture fixture(Faction listenerFaction, Faction shooterFaction) {
        NavigationGrid grid = new NavigationGrid(24, 12);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
            grid.setWalkable(7, y, false);
        }
        BattleSimulation sim = new BattleSimulation(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
        int squadId = sim.mintSquad(listenerFaction, UnitType.MARINE);
        EntitySpec observerSpec = new EntitySpec("listener", listenerFaction,
                UnitType.MARINE, 5, 5).squad(squadId);
        observerSpec.moveSpeed = 0f;
        long observer = sim.spawn(observerSpec);
        EntitySpec shooterSpec = new EntitySpec("shooter", shooterFaction,
                UnitType.MARINE, 9, 5);
        shooterSpec.moveSpeed = 0f;
        long shooter = sim.spawn(shooterSpec);
        return new Fixture(sim, sim.getSquad(squadId), observer, shooter);
    }

    private static void openWall(BattleSimulation sim) {
        for (int y = 0; y < sim.getGrid().getHeight(); y++) {
            sim.getGrid().setWalkableFloor(7, y);
        }
    }

    private record Fixture(BattleSimulation sim, Squad squad,
                           long observer, long shooter) {}
}
