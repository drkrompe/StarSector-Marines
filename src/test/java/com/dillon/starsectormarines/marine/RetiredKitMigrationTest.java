package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Saves, patterns and ladder lines that name a retired vehicle kit read as the internal it became. */
class RetiredKitMigrationTest {

    @Test
    void aKitIdResolvesToItsInternalAndItsPatternToTheInternalsPattern() {
        assertSame(MechInternal.ACCELERATED_LOADER, MechInternal.findById("kit.accelerated-loader"));
        assertEquals(MechPattern.of(MechInternal.ACCELERATED_LOADER),
                MechPattern.parse(MechPattern.PREFIX + "kit:kit.accelerated-loader"));
    }

    @Test
    void aHullSavedWithItsSealedLoaderCarriesTheStandardLoaderAndKitsThatNoLongerPackComeOff() throws Exception {
        CampaignVehicle technical = saved(VehicleType.MILITIA_TECHNICAL, List.of("kit.ready-rack"),
                List.of(MechInternal.FACTORY_CAPACITOR_BANK.id));
        assertEquals(List.of(MechInternal.FACTORY_CAPACITOR_BANK, MechInternal.STANDARD_LOADER), technical.rackInternals());
        assertTrue(technical.kits().isEmpty(), "the Light hull is full with bank and loader; the kit is a spare");

        CampaignVehicle truck = saved(VehicleType.SUPPLY_TRUCK, List.of(), List.of());
        assertTrue(truck.rackInternals().isEmpty(), "an unarmed hull had no loader to keep");
    }

    @Test
    void anAcceleratedLoaderKitBecomesTheHullsLoader() throws Exception {
        CampaignVehicle tank = saved(VehicleType.BREAKTHROUGH_TANK, List.of("kit.accelerated-loader", "kit.spall-liner"),
                List.of(MechInternal.FACTORY_CAPACITOR_BANK.id));
        assertEquals(List.of(MechInternal.FACTORY_CAPACITOR_BANK, MechInternal.ACCELERATED_LOADER), tank.rackInternals());
        assertEquals(1, tank.kits().size(), "the other kit stays on the rack");
    }

    @Test
    void aHullBuiltUnderTheLoaderRuleKeepsAnEmptyCompartmentEmpty() throws Exception {
        CampaignVehicle tank = new CampaignVehicle("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK);
        assertTrue(tank.removeRack(MechInternal.STANDARD_LOADER));
        resolve(tank);
        assertEquals(List.of(MechInternal.FACTORY_CAPACITOR_BANK), tank.rackInternals());
    }

    @Test
    void spareKitsAndLearnedKitPatternsJoinTheInternals() throws Exception {
        MechBay bay = new MechBay();
        Map<String, Integer> kits = new HashMap<>();
        kits.put("kit.accelerated-loader", 2);
        set(bay, "ownedKits", kits);
        @SuppressWarnings("unchecked")
        Set<String> known = (Set<String>) get(bay, "knownPatternIds");
        known.add(MechPattern.PREFIX + "kit:kit.accelerated-loader");
        int before = bay.ownedInternal(MechInternal.ACCELERATED_LOADER.id);

        Method resolve = MechBay.class.getDeclaredMethod("readResolve");
        resolve.setAccessible(true);
        resolve.invoke(bay);

        assertEquals(before + 2, bay.ownedInternal(MechInternal.ACCELERATED_LOADER.id));
        assertEquals(0, bay.ownedKit("kit.accelerated-loader"));
        assertTrue(bay.knowsPattern(MechPattern.of(MechInternal.ACCELERATED_LOADER).id()));
        assertFalse(bay.knownPatternIds().contains(MechPattern.PREFIX + "kit:kit.accelerated-loader"));
    }

    /** A vehicle as a save from before the loader was a part wrote it, read back. */
    private static CampaignVehicle saved(VehicleType type, List<String> kitIds, List<String> rackIds) throws Exception {
        CampaignVehicle vehicle = new CampaignVehicle("v", "Old", type);
        set(vehicle, "kitIds", new ArrayList<>(kitIds));
        set(vehicle, "rackInternalIds", new ArrayList<>(rackIds));
        set(vehicle, "fitVersion", 0);
        resolve(vehicle);
        return vehicle;
    }

    private static void resolve(CampaignVehicle vehicle) throws Exception {
        Method resolve = CampaignVehicle.class.getDeclaredMethod("readResolve");
        resolve.setAccessible(true);
        resolve.invoke(vehicle);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
