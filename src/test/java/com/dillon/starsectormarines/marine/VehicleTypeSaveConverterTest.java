package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A retired hull a save names loads as the hull it was carried to; every other name as itself. */
class VehicleTypeSaveConverterTest {

    private final VehicleTypeSaveConverter converter = new VehicleTypeSaveConverter();

    @Test
    void theDozerBreacherIsCarriedToTheOnager() {
        assertEquals(VehicleType.BREAKTHROUGH_TANK, converter.fromString(VehicleType.RETIRED_DOZER));
        assertEquals(VehicleType.BREAKTHROUGH_TANK, VehicleType.fromSavedName("dozer_breacher"));
        MechPattern pattern = MechPattern.parse(MechPattern.PREFIX + "vehicle:dozer_breacher");
        assertEquals(MechPattern.of(VehicleType.BREAKTHROUGH_TANK), pattern, "a dozer card teaches the Onager");
    }

    @Test
    void everyLiveHullRoundTripsByName() {
        for (VehicleType type : VehicleType.values()) {
            assertTrue(converter.canConvert(type.getClass()), type.name());
            assertEquals(type, converter.fromString(converter.toString(type)));
        }
        assertNull(VehicleType.fromSavedName("no_such_hull"));
        assertThrows(IllegalArgumentException.class, () -> converter.fromString("NO_SUCH_HULL"));
    }

    @Test
    void aLearnedDozerPatternIsTheOnagers() {
        MechBay bay = new MechBay();
        assertTrue(bay.learnPattern(MechPattern.PREFIX + "vehicle:dozer_breacher"));
        assertTrue(bay.canFabricate(VehicleType.BREAKTHROUGH_TANK));
    }
}
