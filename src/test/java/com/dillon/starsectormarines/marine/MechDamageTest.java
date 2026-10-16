package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechBodyPlan;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Turning a battle's frozen section record into the damage a mech carries home. */
class MechDamageTest {

    @Test void wholeSectionsLeaveNoDamage() {
        assertNull(MechDamage.of(ReportFixture.whole().build()));
        assertNull(MechDamage.of(null));
    }

    @Test void sectionLossesAreReadAsFractionsOfEachPool() {
        MechDamage damage = MechDamage.of(ReportFixture.whole()
                .section(MechSection.LEGS, 0.25f, 0f)
                .section(MechSection.TORSO, 1f, 0.5f)
                .build());

        assertEquals(0.75f, damage.structureLost(MechSection.LEGS), 1e-6f);
        assertEquals(1f, damage.armorLost(MechSection.LEGS), 1e-6f);
        assertEquals(0f, damage.structureLost(MechSection.TORSO), 1e-6f);
        assertEquals(0.5f, damage.armorLost(MechSection.TORSO), 1e-6f);
        assertEquals(0f, damage.wear(MechSection.HEAD), 1e-6f);
        assertEquals(0.75f * 0.75f + 0.25f, damage.wear(MechSection.LEGS), 1e-6f);
    }

    @Test void aDestroyedSectionIsWhollyRebuilt() {
        MechDamage damage = MechDamage.of(ReportFixture.whole()
                .destroyed(MechSection.LEFT_SHOULDER, MechWeaponComponent.SRM_15)
                .build());

        assertEquals(1f, damage.structureLost(MechSection.LEFT_SHOULDER), 1e-6f);
        assertEquals(1f, damage.wear(MechSection.LEFT_SHOULDER), 1e-6f);
    }

    @Test void aMountDeployedEmptyCarriesNoDamage() {
        // An unfitted section reports zero fractions: zero of nothing, not a lost section.
        MechDamage damage = MechDamage.of(ReportFixture.whole()
                .unfitted(MechSection.RIGHT_SHOULDER)
                .section(MechSection.HEAD, 0.5f, 0.5f)
                .build());

        assertEquals(0f, damage.wear(MechSection.RIGHT_SHOULDER), 1e-6f);
        assertEquals(1, damage.wearBySection().size());
    }

    @Test void chassisWearWeighsSectionsByTheirShareOfThePools() {
        MechDamage head = MechDamage.of(ReportFixture.whole().destroyed(MechSection.HEAD, null).build());
        MechDamage torso = MechDamage.of(ReportFixture.whole().section(MechSection.TORSO, 0f, 0f).build());

        assertEquals(MechBodyPlan.BULWARK.poolShare(MechSection.HEAD), head.wear(MechBodyPlan.BULWARK), 1e-6f);
        assertEquals(MechBodyPlan.BULWARK.poolShare(MechSection.TORSO), torso.wear(MechBodyPlan.BULWARK), 1e-6f);
    }

    @Test void theWorseOfTwoRecordsIsKeptSectionBySection() {
        MechDamage legs = MechDamage.of(ReportFixture.whole().section(MechSection.LEGS, 0.5f, 0f).build());
        MechDamage head = MechDamage.of(ReportFixture.whole()
                .section(MechSection.LEGS, 0.8f, 1f).section(MechSection.HEAD, 0f, 1f).build());

        MechDamage worst = MechDamage.worst(legs, head);

        assertEquals(0.5f, worst.structureLost(MechSection.LEGS), 1e-6f);
        assertEquals(1f, worst.armorLost(MechSection.LEGS), 1e-6f);
        assertEquals(1f, worst.structureLost(MechSection.HEAD), 1e-6f);
    }

    /** A Bulwark's frozen section record, built section by section. */
    static final class ReportFixture {
        private final boolean[] fitted = new boolean[MechSection.values().length];
        private final float[] structure = new float[MechSection.values().length];
        private final float[] armor = new float[MechSection.values().length];
        private final boolean[] destroyed = new boolean[MechSection.values().length];
        private final MechWeaponComponent[] lost = new MechWeaponComponent[MechMountSlot.values().length];

        static ReportFixture whole() {
            ReportFixture fixture = new ReportFixture();
            Arrays.fill(fixture.fitted, true);
            Arrays.fill(fixture.structure, 1f);
            Arrays.fill(fixture.armor, 1f);
            return fixture;
        }

        ReportFixture section(MechSection section, float structureLeft, float armorLeft) {
            structure[section.ordinal()] = structureLeft;
            armor[section.ordinal()] = armorLeft;
            return this;
        }

        ReportFixture destroyed(MechSection section, MechWeaponComponent weapon) {
            section(section, 0f, 0f);
            destroyed[section.ordinal()] = true;
            if (section.mount() != null) lost[section.mount().ordinal()] = weapon;
            return this;
        }

        ReportFixture unfitted(MechSection section) {
            fitted[section.ordinal()] = false;
            return section(section, 0f, 0f);
        }

        MechSections.Report build() {
            return new MechSections.Report("bulwark", fitted, structure, armor, destroyed, lost);
        }
    }
}
