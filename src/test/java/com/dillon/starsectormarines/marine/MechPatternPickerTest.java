package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechPatternPickerTest {

    private static final EquipmentAcquisitionEligibility.Progress OPENING =
            EquipmentAcquisitionEligibility.Progress.OPENING;
    private static final EquipmentAcquisitionEligibility.Progress VETERAN =
            new EquipmentAcquisitionEligibility.Progress(15, 20);

    @Test
    void patronOpensTiersByReputation() {
        assertEquals(EnumSet.of(EquipmentAccessTier.COMMON),
                tiers(drawAll("hegemony", FactionEquipmentSource.PATRON, OPENING)));
        assertTrue(tiers(drawAll("hegemony", FactionEquipmentSource.PATRON,
                new EquipmentAcquisitionEligibility.Progress(0, 20)))
                .contains(EquipmentAccessTier.PRESTIGE));
    }

    @Test
    void recoveryOpensTiersByVictories() {
        assertEquals(EnumSet.of(EquipmentAccessTier.COMMON),
                tiers(drawAll("hegemony", FactionEquipmentSource.RECOVERY,
                        new EquipmentAcquisitionEligibility.Progress(4, 20))));
        Set<EquipmentAccessTier> advanced = tiers(drawAll("hegemony",
                FactionEquipmentSource.RECOVERY,
                new EquipmentAcquisitionEligibility.Progress(5, 0)));
        assertTrue(advanced.contains(EquipmentAccessTier.ADVANCED));
        assertFalse(advanced.contains(EquipmentAccessTier.PRESTIGE));
        assertTrue(tiers(drawAll("hegemony", FactionEquipmentSource.RECOVERY, VETERAN))
                .contains(EquipmentAccessTier.PRESTIGE));
    }

    @Test
    void knownOrCarriedPatternsAndStartersAreNeverOffered() {
        Set<String> drawn = new HashSet<>(drawAll("tritachyon", FactionEquipmentSource.PATRON, VETERAN));
        for (MechPattern starter : MechPattern.STARTER) assertFalse(drawn.contains(starter.id()));
        assertEquals(MechPattern.offerable().size(), drawn.size());

        Set<String> held = Set.of(MechPattern.of(MechVariant.BULWARK).id(),
                MechPattern.of(MechWeaponComponent.PULSE_LASER).id());
        List<String> remaining = MechPatternPicker.pick("tritachyon",
                FactionEquipmentSource.PATRON, 100, 7L, held, VETERAN);
        assertTrue(remaining.stream().noneMatch(held::contains));

        Set<String> everything = new HashSet<>();
        for (MechPattern pattern : MechPattern.offerable()) everything.add(pattern.id());
        assertTrue(MechPatternPicker.pick("tritachyon", FactionEquipmentSource.PATRON, 1, 7L,
                everything, VETERAN).isEmpty());
    }

    @Test
    void selectionIsDeterministicForASeedAndVariesAcrossSeeds() {
        assertEquals(pick("hegemony", 3, 42L), pick("hegemony", 3, 42L));
        Set<List<String>> draws = new HashSet<>();
        for (long seed = 0; seed < 16; seed++) draws.add(pick("hegemony", 1, seed));
        assertTrue(draws.size() > 1);
    }

    @Test
    void kindFilterRestrictsToChassis() {
        List<String> chassis = MechPatternPicker.pick("hegemony", FactionEquipmentSource.PATRON,
                100, 3L, Set.of(), VETERAN, EnumSet.of(MechPattern.Kind.CHASSIS));
        assertEquals(Set.of(MechPattern.of(MechVariant.WARDEN).id(),
                MechPattern.of(MechVariant.BULWARK).id()), Set.copyOf(chassis));
    }

    @Test
    void factionsWeightTheHardwareTheyIssue() {
        MechPattern lance = MechPattern.of(MechWeaponComponent.THERMAL_LANCE);
        assertTrue(MechPatternPicker.weight("lions_guard", lance)
                > MechPatternPicker.weight("independent", lance));
        assertEquals(MechPatternPicker.BASE_WEIGHT, MechPatternPicker.weight(null, lance));
        assertTrue(MechPatternPicker.weight("tritachyon",
                MechPattern.of(MechWeaponComponent.SHOULDER_LASER_CANNON))
                > MechPatternPicker.weight("hegemony",
                MechPattern.of(MechWeaponComponent.SHOULDER_LASER_CANNON)));
    }

    @Test
    void factionsWithoutPlayerEquipmentOfferNoPatterns() {
        assertTrue(MechPatternPicker.pick("remnant", FactionEquipmentSource.RECOVERY, 5, 1L,
                Set.of(), VETERAN).isEmpty());
    }

    private static List<String> pick(String factionId, int limit, long seed) {
        return MechPatternPicker.pick(factionId, FactionEquipmentSource.PATRON, limit, seed,
                Set.of(), VETERAN);
    }

    private static List<String> drawAll(String factionId, FactionEquipmentSource source,
                                        EquipmentAcquisitionEligibility.Progress progress) {
        return MechPatternPicker.pick(factionId, source, 100, 11L, Set.of(), progress);
    }

    private static Set<EquipmentAccessTier> tiers(List<String> ids) {
        Set<EquipmentAccessTier> tiers = EnumSet.noneOf(EquipmentAccessTier.class);
        for (String id : ids) tiers.add(MechPattern.parse(id).accessTier());
        return tiers;
    }
}
