package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.battery.FireControl;
import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.battery.FortressBatteryService;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The aspect dependency rules, asked of {@link Fort} directly: which side the magazine and the power building work
 * for, what happens to the guns when each is lost, and that every rule is written for sides rather than for attackers
 * or defenders. Nothing here stands a world up.
 */
class FortAspectRulesTest {

    private static final long BATTERY = 1L, MAGAZINE = 2L, POWER = 3L, BUNKER = 4L;

    @BeforeAll
    static void data() {
        InstalledVanillaData.install();
    }

    private static Fort fort(Faction holder, boolean withMagazine, boolean withPower) {
        FortressBatteryService batteries = new FortressBatteryService();
        FortressBattery battery = batteries.register(BATTERY,
                TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.FORTRESS_BATTERY_STRUCTURE_ID),
                40f, 40f, holder);
        FortService service = new FortService();
        service.tagPlatform(0, battery);
        service.tagAspect(0, FortAspect.FIRE_CONTROL, BUNKER, holder);
        if (withMagazine) service.tagAspect(0, FortAspect.MAGAZINE, MAGAZINE, holder);
        if (withPower) service.tagAspect(0, FortAspect.POWER, POWER, holder);
        return service.forts().get(0);
    }

    /** Takes a place the way a hostile squad does: stand in it alone until the point changes hands. */
    private static void take(Fort.Place place, Faction taker) {
        FireControl control = place.control();
        for (int i = 0; i < 4000 && control.holder() != taker; i++) control.tick(0.1f, false, taker);
        assertEquals(taker, control.holder());
    }

    @Test
    void aFortWithNoMagazineOrPowerBuildingIsNeitherRationedNorSlowed() {
        Fort fort = fort(Faction.DEFENDER, false, false);
        assertTrue(fort.magazineSupplies(Faction.DEFENDER));
        assertTrue(fort.powered(Faction.DEFENDER));
        assertEquals(1f, fort.gunRate(Faction.DEFENDER));
        for (int i = 0; i < 100; i++) assertTrue(fort.takeRound(Faction.DEFENDER));
    }

    @Test
    void aWreckedMagazineLeavesTheReadyRoundsAndThenNothing() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        assertTrue(fort.magazineSupplies(Faction.DEFENDER));
        fort.place(FortAspect.MAGAZINE).markWrecked();
        assertFalse(fort.magazineSupplies(Faction.DEFENDER));
        int fired = 0;
        while (fort.takeRound(Faction.DEFENDER)) {
            fired++;
            assertTrue(fired <= 1000, "the pool must run out");
        }
        assertEquals((int) Fort.READY_ROUNDS, fired);
        assertFalse(fort.hasRounds(Faction.DEFENDER));
    }

    @Test
    void theMagazineWorksForWhoeverHoldsItAndOnlyThem() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        take(fort.place(FortAspect.MAGAZINE), Faction.MARINE);
        // The guns are still the garrison's, but the store is the company's now: they are rationed, and a battery the
        // company held would be fed by it.
        assertFalse(fort.magazineSupplies(Faction.DEFENDER));
        assertTrue(fort.magazineSupplies(Faction.MARINE));
        assertTrue(fort.takeRound(Faction.MARINE));
        assertEquals(Fort.READY_ROUNDS, fort.readyRounds(), "the free rounds do not touch the pool");
        assertTrue(fort.takeRound(Faction.DEFENDER));
        assertEquals(Fort.READY_ROUNDS - 1f, fort.readyRounds());
    }

    @Test
    void aTakenMagazineCanBeRetakenAndFeedsTheGunsAgain() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        take(fort.place(FortAspect.MAGAZINE), Faction.MARINE);
        take(fort.place(FortAspect.MAGAZINE), Faction.DEFENDER);
        assertTrue(fort.magazineSupplies(Faction.DEFENDER));
    }

    @Test
    void aWreckedMagazineCannotBeTakenBackIntoService() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        fort.place(FortAspect.MAGAZINE).markWrecked();
        take(fort.place(FortAspect.MAGAZINE), Faction.MARINE);
        assertFalse(fort.magazineSupplies(Faction.MARINE), "wrecked stays wrecked, whoever stands in it");
    }

    @Test
    void powerSlowsTheGunsWhenItIsLostContestedOrTaken() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        assertEquals(1f, fort.gunRate(Faction.DEFENDER));

        // Contested: nobody's until it is settled.
        fort.place(FortAspect.POWER).control().tick(0.1f, false, Faction.MARINE);
        assertTrue(fort.place(FortAspect.POWER).control().contested());
        assertEquals(Fort.UNPOWERED_RATE, fort.gunRate(Faction.DEFENDER));

        // Taken: it powers the company's guns and nobody else's.
        take(fort.place(FortAspect.POWER), Faction.MARINE);
        assertEquals(Fort.UNPOWERED_RATE, fort.gunRate(Faction.DEFENDER));
        assertEquals(1f, fort.gunRate(Faction.MARINE));
    }

    @Test
    void aWreckedPowerBuildingSlowsEveryoneAndStaysWrecked() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        fort.place(FortAspect.POWER).markWrecked();
        assertEquals(Fort.UNPOWERED_RATE, fort.gunRate(Faction.DEFENDER));
        take(fort.place(FortAspect.POWER), Faction.MARINE);
        assertEquals(Fort.UNPOWERED_RATE, fort.gunRate(Faction.MARINE));
    }

    @Test
    void powerAndMagazineAreIndependent() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        fort.place(FortAspect.POWER).markWrecked();
        assertTrue(fort.magazineSupplies(Faction.DEFENDER), "cutting the power does not ration the guns");
        fort = fort(Faction.DEFENDER, true, true);
        fort.place(FortAspect.MAGAZINE).markWrecked();
        assertEquals(1f, fort.gunRate(Faction.DEFENDER), "wrecking the magazine does not slow the guns");
    }

    @Test
    void aWreckedBunkerSilencesTheBatteryForGood() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        FortressBattery battery = fort.battery();
        assertTrue(battery.operational());
        fort.place(FortAspect.FIRE_CONTROL).markWrecked();
        assertFalse(fort.fireControlIntact());
        assertFalse(battery.operational());
    }

    @Test
    void aBatteryWithNoRoundsIsNotOperationalSoItsEnvelopeIsWithdrawn() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        FortressBattery battery = fort.battery();
        fort.place(FortAspect.MAGAZINE).markWrecked();
        while (fort.takeRound(Faction.DEFENDER)) { /* fire them out */ }
        assertFalse(battery.operational());
    }

    @Test
    void theBatteryNeverNeedsItsFortToBeBare() {
        FortressBatteryService batteries = new FortressBatteryService();
        FortressBattery bare = batteries.register(9L,
                TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.FORTRESS_BATTERY_STRUCTURE_ID),
                10f, 10f, Faction.DEFENDER);
        assertTrue(bare.fort() == null);
        assertTrue(bare.operational(), "a bare structure is unchanged by forts");
    }
}
