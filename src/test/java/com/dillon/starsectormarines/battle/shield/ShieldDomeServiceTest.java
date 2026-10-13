package com.dillon.starsectormarines.battle.shield;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dome as a unit: what it takes, what it lets through, how its pool fills
 * and overloads, who holds it. A whole-battle question (ship fire against a
 * dome, then landing) is the {@code shield-dome} scene's.
 */
class ShieldDomeServiceTest {

    private static final int GEN_X = 30;
    private static final int GEN_Y = 20;

    private static BattleSimulation arena() {
        int w = 60;
        int h = 40;
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, topology);
        // Both sides present and far from the generator, so the sim keeps ticking.
        sim.spawn(new EntitySpec("m-far", Faction.MARINE, UnitType.MARINE, 2, 2).moveSpeed(0f));
        sim.spawn(new EntitySpec("d-far", Faction.DEFENDER, UnitType.MARINE, 57, 37).moveSpeed(0f));
        return sim;
    }

    private static DomeSpec spec() {
        return TurretCatalogRegistry.requireStructure(ShieldGenerator.STRUCTURE_ID).dome;
    }

    private static PendingDetonation ship(Faction from, float x, float y, float damage) {
        return new PendingDetonation(0L, x, y, 0f, 3f, damage, 8f, Math.round(damage), from, false)
                .asShipGrade();
    }

    private static void run(BattleSimulation sim, float seconds) {
        int ticks = Math.round(seconds / BattleSimulation.TICK_DT);
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    private static ShieldDomeService.DomeView only(BattleSimulation sim) {
        assertEquals(1, sim.shieldDomes().domes().size());
        return sim.shieldDomes().domes().get(0);
    }

    @Test
    void theCatalogDefinesTheGeneratorAsAThreeByThreeWithADome() {
        StructureDef def = TurretCatalogRegistry.requireStructure(ShieldGenerator.STRUCTURE_ID);
        assertTrue(def.isShieldGenerator());
        assertEquals(3, def.footprintCellsX);
        assertEquals(3, def.footprintCellsY);
        assertNotNull(def.dome);
        assertNull(TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.VULCAN_STRUCTURE_ID).dome);
    }

    @Test
    void spawningAGeneratorRaisesItsDomeForItsOwnSide() {
        BattleSimulation sim = arena();
        sim.spawn(ShieldGenerator.create("g", Faction.DEFENDER, GEN_X, GEN_Y));
        run(sim, 0.1f);
        ShieldDomeService.DomeView dome = only(sim);
        assertEquals(Faction.DEFENDER, dome.holder());
        assertEquals(spec().radiusCells(), dome.radius(), 1e-4f);
        assertFalse(dome.overloaded());
        assertEquals(0f, dome.flux(), 1e-3f);
    }

    @Test
    void takesHostileShipOrdnanceInsideAndLetsEverythingElsePass() {
        BattleSimulation sim = arena();
        sim.spawn(ShieldGenerator.create("g", Faction.DEFENDER, GEN_X, GEN_Y));
        ShieldDomeService domes = sim.shieldDomes();

        assertTrue(domes.absorbs(ship(Faction.MARINE, GEN_X + 5f, GEN_Y, 100f)), "hostile ship round inside");
        assertFalse(domes.absorbs(ship(Faction.DEFENDER, GEN_X + 5f, GEN_Y, 100f)), "its own side fires through it");
        assertFalse(domes.absorbs(ship(Faction.MARINE, GEN_X + spec().radiusCells() + 2f, GEN_Y, 100f)), "outside");
        PendingDetonation rocket = new PendingDetonation(0L, GEN_X + 5f, GEN_Y, 0f, 3f, 100f, 8f, 100,
                Faction.MARINE, false);
        assertFalse(domes.absorbs(rocket), "ordinary ordnance is not ship-grade");
        run(sim, 0.1f);
        assertEquals(1, only(sim).hits());
        assertEquals(100f, only(sim).absorbedFlux(), 1e-3f);
    }

    @Test
    void coverageForAPreviewFindsAnUpDomeOverThePointAndNoneForItsOwnSideOrOutside() {
        BattleSimulation sim = arena();
        sim.spawn(ShieldGenerator.create("g", Faction.DEFENDER, GEN_X, GEN_Y));
        run(sim, 0.1f);
        ShieldDomeService domes = sim.shieldDomes();
        assertNotNull(domes.coverageAt(GEN_X + 5f, GEN_Y, Faction.MARINE));
        assertNull(domes.coverageAt(GEN_X + 5f, GEN_Y, Faction.DEFENDER), "its holder is not stopped");
        assertNull(domes.coverageAt(GEN_X + spec().radiusCells() + 2f, GEN_Y, Faction.MARINE));
        domes.absorbs(ship(Faction.MARINE, GEN_X, GEN_Y, spec().fluxCapacity() + 1f));
        run(sim, 0.1f);
        ShieldDomeService.DomeView down = domes.coverageAt(GEN_X, GEN_Y, Faction.MARINE);
        assertNotNull(down, "a dome that is down still covers the point");
        assertTrue(down.overloaded());
    }

    @Test
    void anAlliedShipsRoundIsNotStoppedByTheMarinesDomeEither() {
        BattleSimulation sim = arena();
        sim.spawn(ShieldGenerator.create("g", Faction.MARINE, GEN_X, GEN_Y));
        assertFalse(sim.shieldDomes().absorbs(ship(Faction.ALLY, GEN_X, GEN_Y, 50f)));
        assertTrue(sim.shieldDomes().absorbs(ship(Faction.DEFENDER, GEN_X, GEN_Y, 50f)),
                "either side may hold a dome; it stops whoever is hostile to the holder");
    }

    @Test
    void aDomeThatFillsOverloadsThenRecoversEmpty() {
        BattleSimulation sim = arena();
        sim.spawn(ShieldGenerator.create("g", Faction.DEFENDER, GEN_X, GEN_Y));
        ShieldDomeService domes = sim.shieldDomes();
        float capacity = spec().fluxCapacity();

        assertTrue(domes.absorbs(ship(Faction.MARINE, GEN_X, GEN_Y, capacity + 1f)),
                "the round that fills the pool is itself absorbed");
        run(sim, 0.1f);
        assertTrue(only(sim).overloaded());
        assertEquals(1, only(sim).overloads());
        assertFalse(domes.absorbs(ship(Faction.MARINE, GEN_X, GEN_Y, 100f)), "in the gap, rounds land");

        run(sim, spec().overloadSeconds() + 0.5f);
        assertFalse(only(sim).overloaded());
        assertEquals(0f, only(sim).flux(), 1e-3f);
        assertTrue(domes.absorbs(ship(Faction.MARINE, GEN_X, GEN_Y, 100f)), "back up");
    }

    @Test
    void thePoolDrainsWhileTheDomeIsUp() {
        BattleSimulation sim = arena();
        sim.spawn(ShieldGenerator.create("g", Faction.DEFENDER, GEN_X, GEN_Y));
        sim.shieldDomes().absorbs(ship(Faction.MARINE, GEN_X, GEN_Y, 300f));
        run(sim, 0.1f);
        float after = only(sim).flux();
        run(sim, 2f);
        assertTrue(only(sim).flux() < after - spec().dissipationPerSecond(), "drained by about two seconds of dissipation");
        assertFalse(only(sim).overloaded());
    }

    @Test
    void destroyingTheGeneratorDropsTheDome() {
        BattleSimulation sim = arena();
        long generator = sim.spawn(ShieldGenerator.create("g", Faction.DEFENDER, GEN_X, GEN_Y));
        run(sim, 0.1f);
        assertEquals(1, sim.shieldDomes().domes().size());
        sim.applyDamage(generator, 1_000_000f, 3.5f, 0f);
        run(sim, 0.2f);
        assertTrue(sim.shieldDomes().domes().isEmpty());
        assertFalse(sim.shieldDomes().absorbs(ship(Faction.MARINE, GEN_X, GEN_Y, 100f)));
    }

    @Test
    void anUncontestedSideStandingByItTakesTheGeneratorAndItRebootsForThem() {
        BattleSimulation sim = arena();
        sim.spawn(ShieldGenerator.create("g", Faction.DEFENDER, GEN_X, GEN_Y));
        sim.spawn(new EntitySpec("raider", Faction.MARINE, UnitType.MARINE, GEN_X + 3, GEN_Y).moveSpeed(0f));

        run(sim, spec().captureSeconds() * 0.5f);
        assertEquals(Faction.DEFENDER, only(sim).holder());
        assertTrue(only(sim).captureProgress() > 0.3f && only(sim).captureProgress() < 0.7f,
                "progress " + only(sim).captureProgress());

        run(sim, spec().captureSeconds());
        assertEquals(Faction.MARINE, only(sim).holder());
        assertEquals(1, only(sim).captures());
        assertTrue(only(sim).overloaded(), "a taken generator reboots before it protects its new holder");

        run(sim, ShieldDomeService.REBOOT_SECONDS + 0.5f);
        assertFalse(only(sim).overloaded());
        assertTrue(sim.shieldDomes().absorbs(ship(Faction.DEFENDER, GEN_X, GEN_Y, 50f)),
                "now it stops the old holder's ships");
        assertFalse(sim.shieldDomes().absorbs(ship(Faction.MARINE, GEN_X, GEN_Y, 50f)));
    }

    @Test
    void aTakenGeneratorChangesSidesSoItsOldSidesEnemiesStopShootingItAndItIsNotAPerson() {
        BattleSimulation sim = arena();
        long gen = sim.spawn(ShieldGenerator.create("g", Faction.DEFENDER, GEN_X, GEN_Y));
        sim.spawn(new EntitySpec("raider", Faction.MARINE, UnitType.MARINE, GEN_X + 3, GEN_Y).moveSpeed(0f));
        assertEquals(Faction.DEFENDER, sim.identity().faction(gen));
        assertFalse(sim.isCapturedStructure(gen));
        int marinesBefore = sim.getRoster().factionLiveCount(Faction.MARINE);

        run(sim, spec().captureSeconds() * 1.5f);

        assertEquals(Faction.MARINE, sim.identity().faction(gen), "the entity follows its holder");
        assertEquals(Faction.MARINE, only(sim).holder());
        assertTrue(sim.isCapturedStructure(gen));
        assertEquals(marinesBefore + 1, sim.getRoster().factionLiveCount(Faction.MARINE));
        assertEquals(1, sim.getRoster().factionLiveCount(Faction.DEFENDER), "only the far defender is left");
    }

    @Test
    void theHoldersOwnPeoplePresentStopACaptureAndWindItBack() {
        BattleSimulation sim = arena();
        sim.spawn(ShieldGenerator.create("g", Faction.DEFENDER, GEN_X, GEN_Y));
        long raider = sim.spawn(new EntitySpec("raider", Faction.MARINE, UnitType.MARINE, GEN_X + 3, GEN_Y).moveSpeed(0f));
        run(sim, spec().captureSeconds() * 0.5f);
        float started = only(sim).captureProgress();
        assertTrue(started > 0f);

        // A guard arrives: the capture pauses while both are there, so it cannot complete.
        sim.spawn(new EntitySpec("guard", Faction.DEFENDER, UnitType.MARINE, GEN_X - 3, GEN_Y).moveSpeed(0f));
        run(sim, spec().captureSeconds());
        assertEquals(Faction.DEFENDER, only(sim).holder());
        assertEquals(started, only(sim).captureProgress(), 0.11f);

        // The raider falls: the holder's people alone wind the capture back.
        sim.applyDamage(raider, 1_000_000f, 3.5f, 0f);
        run(sim, spec().captureSeconds());
        assertEquals(0f, only(sim).captureProgress(), 1e-4f);
        assertEquals(Faction.DEFENDER, only(sim).holder());
    }

    @Test
    void anAbsorbedRoundIsKeptAsAnImpactWithItsCostAndAgesUntilItsLifeIsSpent() {
        BattleSimulation sim = arena();
        sim.spawn(ShieldGenerator.create("g", Faction.DEFENDER, GEN_X, GEN_Y));
        ShieldDomeService domes = sim.shieldDomes();
        domes.absorbs(ship(Faction.MARINE, GEN_X + 4f, GEN_Y + 2f, 120f));
        run(sim, 0.05f);

        assertEquals(1, domes.recentHits().size());
        ShieldDomeService.HitView hit = domes.recentHits().get(0);
        assertEquals(only(sim).entityId(), hit.domeId());
        assertEquals(120f, hit.cost(), 1e-3f);
        assertEquals(GEN_X + 4f, hit.x(), 1e-4f);

        run(sim, 1.0f);
        ShieldDomeService.HitView aged = domes.recentHits().get(0);
        assertEquals(0f, aged.freshness(), 1e-4f, "the flash is long gone");
        assertTrue(aged.ageSeconds() > 1.0f, "but the impact is still crossing the dome: " + aged.ageSeconds());

        run(sim, ShieldDomeService.IMPACT_SECONDS);
        assertTrue(domes.recentHits().isEmpty(), "dropped once its life is spent");
    }

    @Test
    void aDomeKeepsAtMostTheCapOfImpactsAndDropsTheOldest() {
        BattleSimulation sim = arena();
        sim.spawn(ShieldGenerator.create("g", Faction.DEFENDER, GEN_X, GEN_Y));
        ShieldDomeService domes = sim.shieldDomes();
        int rounds = ShieldDomeService.MAX_IMPACTS_PER_DOME + 6;
        for (int i = 0; i < rounds; i++) {
            // Each round lands one cell further east, so which survive is readable from x.
            assertTrue(domes.absorbs(ship(Faction.MARINE, GEN_X - 8f + i * 0.5f, GEN_Y, 5f)));
        }
        run(sim, 0.02f);
        var hits = domes.recentHits();
        assertEquals(ShieldDomeService.MAX_IMPACTS_PER_DOME, hits.size());
        assertEquals(GEN_X - 8f + 6 * 0.5f, hits.get(0).x(), 1e-4f, "the six oldest were dropped");
        assertEquals(GEN_X - 8f + (rounds - 1) * 0.5f, hits.get(hits.size() - 1).x(), 1e-4f);
    }
}
