package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MechPatternTest {

    @Test
    void parseRoundTripsEveryRegisteredPattern() {
        assertFalse(MechPattern.all().isEmpty());
        for (MechPattern pattern : MechPattern.all()) {
            assertEquals(pattern, MechPattern.parse(pattern.id()), pattern.id());
            assertNotNull(pattern.accessTier(), pattern.id());
        }
    }

    @Test
    void unknownAndGarbageIdsParseToNull() {
        assertNull(MechPattern.parse(null));
        assertNull(MechPattern.parse(""));
        assertNull(MechPattern.parse("garbage"));
        assertNull(MechPattern.parse("mech-pattern:"));
        assertNull(MechPattern.parse("mech-pattern:chassis:nonesuch"));
        assertNull(MechPattern.parse("mech-pattern:weapon:nonesuch"));
        assertNull(MechPattern.parse("mech-pattern:armor:hound"));
        assertNull(MechPattern.parse("chassis:hound"));
    }

    @Test
    void starterSetIsNotOfferable() {
        for (MechPattern starter : MechPattern.STARTER) {
            assertTrue(starter.starter());
            assertFalse(MechPattern.offerable().contains(starter), starter.id());
        }
        assertEquals(MechPattern.all().size() - MechPattern.STARTER.size(),
                MechPattern.offerable().size());
    }

    @Test
    void chassisTiersFollowWeightClass() {
        assertEquals(MechPattern.WeightClass.LIGHT, parse("mech-pattern:chassis:hound").weightClass());
        assertEquals(MechPattern.WeightClass.MEDIUM, parse("mech-pattern:chassis:sirocco").weightClass());
        assertEquals(MechPattern.WeightClass.HEAVY, parse("mech-pattern:chassis:bulwark").weightClass());
        assertEquals(EquipmentAccessTier.COMMON, parse("mech-pattern:chassis:hound").accessTier());
        assertEquals(EquipmentAccessTier.ADVANCED, parse("mech-pattern:chassis:sirocco").accessTier());
        assertEquals(EquipmentAccessTier.PRESTIGE, parse("mech-pattern:chassis:bulwark").accessTier());
    }

    @Test
    void weaponPatternsHaveNoWeightClass() {
        for (MechPattern pattern : MechPattern.all()) {
            if (pattern.kind() == MechPattern.Kind.WEAPON) assertNull(pattern.weightClass());
        }
    }

    private static MechPattern parse(String id) {
        MechPattern pattern = MechPattern.parse(id);
        assertNotNull(pattern, id);
        return pattern;
    }
}
