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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The anti-air aspect's state, asked of {@link Fort} directly: a yard that answers to its holder, goes silent when
 * contested or wrecked, slows without the fort's power, and a fort without the aspect imposes nothing.
 */
class FortAirDefenceRulesTest {

    private static final long BATTERY = 1L, YARD = 2L, POWER = 3L, MOUNT = 10L, FLAK = 11L;

    @BeforeAll
    static void data() {
        InstalledVanillaData.install();
    }

    private static FortService service(Faction holder, boolean withYard, boolean withPower) {
        FortressBatteryService batteries = new FortressBatteryService();
        FortressBattery battery = batteries.register(BATTERY,
                TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.FORTRESS_BATTERY_STRUCTURE_ID),
                40f, 40f, holder);
        FortService service = new FortService();
        service.tagPlatform(0, battery);
        if (withYard) {
            service.tagAspect(0, FortAspect.AIR_DEFENCE, YARD, holder);
            service.tagAirMount(0, MOUNT);
            service.tagAirMount(0, FLAK);
        }
        if (withPower) service.tagAspect(0, FortAspect.POWER, POWER, holder);
        return service;
    }

    private static void take(Fort.Place place, Faction taker) {
        FireControl control = place.control();
        for (int i = 0; i < 4000 && control.holder() != taker; i++) control.tick(0.1f, false, taker);
        assertEquals(taker, control.holder());
    }

    @Test
    void aFortWithoutTheAspectImposesNothingOnAMount() {
        Fort fort = service(Faction.DEFENDER, false, true).forts().get(0);
        assertEquals(1f, fort.airDefenceRate(Faction.DEFENDER));
        assertEquals(0, fort.airMountIds().size());
    }

    @Test
    void mountsAreLookedUpByTheFortTheyBelongTo() {
        FortService service = service(Faction.DEFENDER, true, true);
        assertSame(service.forts().get(0), service.airMountFort(MOUNT));
        assertSame(service.forts().get(0), service.airMountFort(FLAK));
        assertNull(service.airMountFort(999L), "a gun that is no fort's anti-air");
    }

    @Test
    void theYardFiresFullRateForItsHolderWithPower() {
        Fort fort = service(Faction.DEFENDER, true, true).forts().get(0);
        assertEquals(1f, fort.airDefenceRate(Faction.DEFENDER));
        assertEquals(0f, fort.airDefenceRate(Faction.MARINE), "the yard does not obey a side that does not hold it");
    }

    @Test
    void withoutThePowerTheMountsRunAtAQuarter() {
        Fort fort = service(Faction.DEFENDER, true, true).forts().get(0);
        fort.place(FortAspect.POWER).markWrecked();
        assertEquals(Fort.UNPOWERED_RATE, fort.airDefenceRate(Faction.DEFENDER));
    }

    @Test
    void aWreckedYardSilencesItsMountsForGoodForEverySide() {
        Fort fort = service(Faction.DEFENDER, true, true).forts().get(0);
        fort.place(FortAspect.AIR_DEFENCE).markWrecked();
        assertEquals(0f, fort.airDefenceRate(Faction.DEFENDER));
        assertEquals(0f, fort.airDefenceRate(Faction.MARINE));
        assertEquals(true, fort.airDefenceWrecked());
    }

    @Test
    void aTakenYardFiresForItsNewHolderAndSilencesTheOldOne() {
        Fort fort = service(Faction.DEFENDER, true, true).forts().get(0);
        Fort.Place yard = fort.place(FortAspect.AIR_DEFENCE);
        for (int i = 0; i < 4000 && !yard.control().contested(); i++) yard.control().tick(0.1f, false, Faction.MARINE);
        assertEquals(0f, fort.airDefenceRate(Faction.DEFENDER), "a contested yard is silent");
        take(yard, Faction.MARINE);
        assertEquals(0f, fort.airDefenceRate(Faction.DEFENDER), "the garrison no longer commands it");
        // The power plant is still the garrison's, so the taker's mounts are slowed: the plant is the other side's to withhold.
        assertEquals(Fort.UNPOWERED_RATE, fort.airDefenceRate(Faction.MARINE));
        take(fort.place(FortAspect.POWER), Faction.MARINE);
        assertEquals(1f, fort.airDefenceRate(Faction.MARINE));
    }
}
