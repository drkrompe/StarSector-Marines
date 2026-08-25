package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.TestUnits;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommanderInfluenceServiceTest {

    @Test
    void factionPicturesUseOwnBeliefWithoutDiscoveringLiveEnemies() {
        BattleSimulation sim = compartmentSim();
        int marineSquadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        spawnStill(sim, "listener", Faction.MARINE, UnitType.MARINE, 5, 5, marineSquadId);
        spawnStill(sim, "civilian", Faction.CIVILIAN, UnitType.CIVILIAN, 6, 5, Squad.NO_SQUAD);
        long heard = spawnStill(sim, "heard", Faction.DEFENDER,
                UnitType.MARINE_RED, 12, 5, Squad.NO_SQUAD);
        long unseen = spawnStill(sim, "unseen", Faction.DEFENDER,
                UnitType.MARINE_RED, 30, 5, Squad.NO_SQUAD);
        sim.postShot(new ShotEvent(heard, 12.5f, 5.5f, 5.5f, 5.5f,
                false, Faction.DEFENDER, 0.1f));

        sim.advance(BattleSimulation.TICK_DT);

        CommanderInfluenceSnapshot marine = sim.getCommanderInfluence(Faction.MARINE);
        CommanderInfluenceSnapshot defender = sim.getCommanderInfluence(Faction.DEFENDER);
        assertEquals(1, marine.contacts().size());
        assertEquals(heard, marine.contacts().get(0).unitId());
        CommanderContact report = marine.contacts().get(0);
        assertEquals(report.confidence(),
                marine.hostileAtWorld(report.cellX(), report.cellY()), 0.0001f);
        assertEquals(0f, marine.hostileAtWorld(sim.world().cellX(unseen),
                sim.world().cellY(unseen)), 0.0001f,
                "an unobserved live enemy must not enter the hostile field");
        assertTrue(defender.contacts().isEmpty());
        assertEquals(0f, defender.maxHostile(), 0.0001f);
        assertEquals(1f, marine.friendlyAtWorld(5, 5), 0.0001f,
                "neutral civilians do not emit combat influence");
        assertTrue(defender.friendlyAtWorld(12, 5) > 0f);
        assertTrue(defender.friendlyAtWorld(30, 5) > 0f);
    }

    @Test
    void duplicateReportsMergeOnceWithLowestSquadTieBreak() {
        BattleSimulation sim = openSim(24, 12);
        int firstSquad = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        int secondSquad = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        spawnStill(sim, "first", Faction.MARINE, UnitType.MARINE, 5, 5, firstSquad);
        spawnStill(sim, "second", Faction.MARINE, UnitType.MARINE, 6, 5, secondSquad);
        long enemy = spawnStill(sim, "enemy", Faction.DEFENDER,
                UnitType.MARINE_RED, 10, 5, Squad.NO_SQUAD);

        sim.advance(BattleSimulation.TICK_DT);

        CommanderInfluenceSnapshot snapshot = sim.getCommanderInfluence(Faction.MARINE);
        assertEquals(1, snapshot.contacts().size());
        assertEquals(enemy, snapshot.contacts().get(0).unitId());
        assertEquals(firstSquad, snapshot.contacts().get(0).reporterSquadId());
        assertEquals(1f, snapshot.hostileAtWorld(10, 5), 0.0001f,
                "two identical reports must not double the hostile emission");
    }

    @Test
    void fixedCadencePublishesOneImmutableSnapshotEveryFifteenTicks() {
        BattleSimulation sim = openSim(24, 12);
        int squad = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        spawnStill(sim, "first", Faction.MARINE, UnitType.MARINE, 5, 5, squad);
        // Deliberately unkillable inside the 16-tick measurement window. The
        // marines have line of sight and open fire immediately, and a battle
        // that ends stops the tick counter this test reads — at shipped
        // lethality a stock MARINE_RED dies to two hits.
        spawnStill(sim, "enemy", Faction.DEFENDER,
                UnitType.MARINE_RED, 20, 5, Squad.NO_SQUAD, 10_000f);
        sim.advance(BattleSimulation.TICK_DT);
        CommanderInfluenceSnapshot initial = sim.getCommanderInfluence(Faction.MARINE);
        assertEquals(1, initial.updatedTick());
        assertEquals(1f, initial.friendlyAtWorld(5, 5), 0.0001f);
        assertEquals(0f, initial.friendlyAtWorld(-1, 5), 0.0001f);
        assertEquals(0f, initial.friendlyAtWorld(24, 5), 0.0001f);

        spawnStill(sim, "second", Faction.MARINE, UnitType.MARINE, 6, 5, squad);
        for (int i = 0; i < 14; i++) sim.advance(BattleSimulation.TICK_DT);

        assertEquals(1, sim.getCommanderInfluence(Faction.MARINE).updatedTick());
        assertEquals(1f, sim.getCommanderInfluence(Faction.MARINE)
                .friendlyAtWorld(5, 5), 0.0001f);
        sim.advance(BattleSimulation.TICK_DT);
        CommanderInfluenceSnapshot refreshed = sim.getCommanderInfluence(Faction.MARINE);
        assertEquals(16, refreshed.updatedTick());
        assertEquals(2f, refreshed.friendlyAtWorld(5, 5), 0.0001f);
        assertEquals(1f, initial.friendlyAtWorld(5, 5), 0.0001f,
                "published snapshots must remain immutable after later refreshes");
    }

    @Test
    void firstDiagnosticReadBuildsCurrentSnapshotThenUsesFixedCadence() {
        BattleSimulation sim = openSim(24, 12);
        int squad = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        spawnStill(sim, "first", Faction.MARINE, UnitType.MARINE, 5, 5, squad);
        spawnStill(sim, "enemy", Faction.DEFENDER,
                UnitType.MARINE_RED, 20, 5, Squad.NO_SQUAD, 10_000f);

        for (int i = 0; i < 30; i++) sim.advance(BattleSimulation.TICK_DT);

        CommanderInfluenceSnapshot firstRead = sim.getCommanderInfluence(Faction.MARINE);
        assertEquals(30, firstRead.updatedTick(),
                "an unused diagnostic must build from current state on its first read");
        for (int i = 0; i < 14; i++) sim.advance(BattleSimulation.TICK_DT);
        assertEquals(30, sim.getCommanderInfluence(Faction.MARINE).updatedTick());

        sim.advance(BattleSimulation.TICK_DT);
        assertEquals(45, sim.getCommanderInfluence(Faction.MARINE).updatedTick());
    }

    @Test
    void staleBeliefAboutADeadHostileIsNotPublishedAsActionableContact() {
        BattleSimulation sim = openSim(40, 12);
        for (int y = 0; y < 12; y++) sim.getGrid().setWalkable(20, y, false);
        int squad = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        spawnStill(sim, "observer", Faction.MARINE, UnitType.MARINE, 5, 5, squad);
        long enemy = spawnStill(sim, "enemy", Faction.DEFENDER,
                UnitType.MARINE_RED, 10, 5, Squad.NO_SQUAD, 10_000f);
        spawnStill(sim, "distant-survivor", Faction.DEFENDER,
                UnitType.MARINE_RED, 35, 5, Squad.NO_SQUAD, 10_000f);
        sim.advance(BattleSimulation.TICK_DT);
        assertEquals(1, sim.getCommanderInfluence(Faction.MARINE).contacts().size());

        TestUnits.kill(sim, enemy);
        for (int i = 0; i < CommanderInfluenceService.UPDATE_INTERVAL_TICKS; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertTrue(sim.getSquad(squad).hasBelievedContacts(),
                "the identity belief intentionally outlives the target");
        CommanderInfluenceSnapshot refreshed =
                sim.getCommanderInfluence(Faction.MARINE);
        assertEquals(16, refreshed.updatedTick());
        assertEquals(java.util.List.of(), refreshed.contacts().stream()
                        .map(CommanderContact::unitId).toList(),
                "dead identities cannot remain actionable commander facts");
    }

    private static BattleSimulation compartmentSim() {
        BattleSimulation sim = openSim(40, 12);
        for (int y = 0; y < sim.getGrid().getHeight(); y++) {
            sim.getGrid().setWalkable(8, y, false);
            sim.getGrid().setWalkable(24, y, false);
        }
        return sim;
    }

    private static BattleSimulation openSim(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static long spawnStill(BattleSimulation sim, String name, Faction faction,
                                   UnitType type, int x, int y, int squadId) {
        return spawnStill(sim, name, faction, type, x, y, squadId, -1f);
    }

    /** {@code maxHp <= 0} keeps the archetype's own pool; a positive value overrides it. */
    private static long spawnStill(BattleSimulation sim, String name, Faction faction,
                                   UnitType type, int x, int y, int squadId, float maxHp) {
        EntitySpec spec = new EntitySpec(name, faction, type, x, y);
        spec.moveSpeed = 0f;
        if (squadId != Squad.NO_SQUAD) spec.squad(squadId);
        if (maxHp > 0f) spec.health(maxHp);
        return sim.spawn(spec);
    }
}
