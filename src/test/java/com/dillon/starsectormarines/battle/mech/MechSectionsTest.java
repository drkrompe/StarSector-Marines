package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.mech.MechSections.Hit;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Section pools, resolution, spill into the torso, and the torso being the only death. */
class MechSectionsTest {

    private static final MechVariant VARIANT = MechVariant.HOUND;
    private static final float RATING = VARIANT.armorRating;
    private static final float PEN = 14f;

    private final DurabilityModel.Resolution scratch = new DurabilityModel.Resolution();
    private final Hit hit = new Hit();

    private static MechSections houndSections() {
        MechLoadoutComponent loadout = VARIANT.createLoadout(null);
        return new MechSections(VARIANT.bodyPlan(), loadout.mounts(),
                VARIANT.maxStructure, VARIANT.maxStructure, VARIANT.armorCapacity, VARIANT.armorCapacity);
    }

    /** Damage that exactly strips {@code section}'s armor, with nothing over. */
    private static float armorBreaker(MechSections sections, MechSection section) {
        return sections.armor(section) / DurabilityModel.armorEfficiency(PEN, RATING);
    }

    /** Damage that destroys {@code section} and spills {@code extra} past it. */
    private static float destroying(MechSections sections, MechSection section, float extra) {
        return armorBreaker(sections, section) + sections.structure(section) + extra;
    }

    private void strike(MechSections sections, MechSection section, float damage) {
        sections.resolve(section, damage, PEN, 0f, RATING, scratch, hit);
    }

    @Test
    void theTorsoArcPlateGovernsTheTorsoAndNothingElse() {
        MechSections flat = houndSections();
        MechSections thin = houndSections();
        float torsoArmor = flat.armor(MechSection.TORSO);
        flat.resolve(MechSection.TORSO, 20f, PEN, 0f, RATING, RATING, 1f, scratch, hit);
        float flatStrip = torsoArmor - flat.armor(MechSection.TORSO);
        thin.resolve(MechSection.TORSO, 20f, PEN, 0f, RATING, RATING, .5f, scratch, hit);
        assertEquals(2f * flatStrip, torsoArmor - thin.armor(MechSection.TORSO), 1e-3f);

        // A leg hit meets the chassis plate whatever the torso's arc.
        MechSections legs = houndSections();
        float legsArmor = legs.armor(MechSection.LEGS);
        legs.resolve(MechSection.LEGS, 10f, PEN, 0f, RATING, RATING, .5f, scratch, hit);
        MechSections legsFlat = houndSections();
        legsFlat.resolve(MechSection.LEGS, 10f, PEN, 0f, RATING, scratch, hit);
        assertEquals(legsArmor - legsFlat.armor(MechSection.LEGS), legsArmor - legs.armor(MechSection.LEGS), 1e-4f);
    }

    @Test
    void aHitFromBehindSpendsTheRearPlateAndLeavesTheFrontWhole() {
        MechSections sections = houndSections();
        float front = sections.armor(MechSection.TORSO);
        float rear = sections.rearArmor();
        assertTrue(rear > 0f, "the torso carries a rear plate");
        assertEquals(sections.armor(MechSection.TORSO) * MechSections.REAR_SHARE, rear, 1e-3f,
                "the rear plate is held on top of the torso's");
        sections.resolve(MechSection.TORSO, 20f, PEN, 0f, RATING, RATING, 1f, true, scratch, hit);
        assertEquals(front, sections.armor(MechSection.TORSO));
        assertTrue(sections.rearArmor() < rear);
        sections.resolve(MechSection.TORSO, 20f, PEN, 0f, RATING, RATING, 1f, false, scratch, hit);
        assertTrue(sections.armor(MechSection.TORSO) < front);
    }

    @Test
    void whatSpillsIntoTheTorsoMeetsTheTorsoArcPlate() {
        MechSections sections = houndSections();
        float torsoArmor = sections.armor(MechSection.TORSO);
        float spill = 12f;
        float damage = destroying(sections, MechSection.LEGS, spill);
        sections.resolve(MechSection.LEGS, damage, PEN, 0f, RATING, RATING, .5f, scratch, hit);
        assertEquals(spill * DurabilityModel.armorEfficiency(PEN, RATING) * 2f,
                torsoArmor - sections.armor(MechSection.TORSO), 1e-2f);
    }

    @Test
    void poolsSumToTheChassisExactlyAndTheEmptyShoulderFoldsIntoTheTorso() {
        MechSections sections = houndSections();

        float structure = 0f, armor = 0f, maxStructure = 0f, maxArmor = 0f;
        for (MechSection section : MechSection.values()) {
            structure += sections.structure(section);
            armor += sections.armor(section);
            maxStructure += sections.maxStructure(section);
            maxArmor += sections.maxArmor(section);
        }
        assertEquals(VARIANT.maxStructure, structure, 1e-3f);
        assertEquals(VARIANT.armorCapacity, armor, 1e-3f);
        assertEquals(VARIANT.maxStructure, maxStructure, 1e-3f);
        assertEquals(VARIANT.armorCapacity, maxArmor, 1e-3f);
        assertEquals(VARIANT.maxStructure, sections.totalStructure(), 1e-3f);
        assertEquals(VARIANT.armorCapacity + sections.rearArmor(), sections.totalArmor(), 1e-3f);

        MechBodyPlan plan = VARIANT.bodyPlan();
        assertFalse(sections.fitted(MechSection.RIGHT_SHOULDER));
        assertEquals(0f, sections.structure(MechSection.RIGHT_SHOULDER));
        assertEquals(0f, sections.armor(MechSection.RIGHT_SHOULDER));
        float torsoShare = plan.poolShare(MechSection.TORSO) + plan.poolShare(MechSection.RIGHT_SHOULDER);
        assertEquals(VARIANT.maxStructure * torsoShare, sections.structure(MechSection.TORSO), 1e-3f);
        assertEquals(VARIANT.armorCapacity * torsoShare, sections.armor(MechSection.TORSO), 1e-3f);
    }

    @Test
    void aHitOnTheLegsWearsTheirArmorBeforeTheirStructure() {
        MechSections sections = houndSections();
        float legsStructure = sections.structure(MechSection.LEGS);
        float legsArmor = sections.armor(MechSection.LEGS);

        strike(sections, MechSection.LEGS, 10f);

        assertTrue(sections.armor(MechSection.LEGS) < legsArmor);
        assertEquals(legsStructure, sections.structure(MechSection.LEGS));
        assertEquals(0f, hit.structureDamage());
        assertSame(MechSection.LEGS, hit.section());
        assertFalse(sections.destroyed(MechSection.LEGS));
    }

    @Test
    void whatTheLegsCannotHoldMeetsTheTorsoArmorBeforeItsStructure() {
        MechSections sections = houndSections();
        float legsStructure = sections.structure(MechSection.LEGS);
        float torsoStructure = sections.structure(MechSection.TORSO);
        float torsoArmor = sections.armor(MechSection.TORSO);
        float efficiency = DurabilityModel.armorEfficiency(PEN, RATING);

        strike(sections, MechSection.LEGS, destroying(sections, MechSection.LEGS, 40f));

        assertEquals(0f, sections.armor(MechSection.LEGS), 1e-3f);
        assertEquals(0f, sections.structure(MechSection.LEGS), 1e-3f);
        assertEquals(legsStructure, hit.sectionStructureDamage(), 1e-3f);
        assertTrue(hit.sectionDestroyed());
        assertTrue(sections.crippled());
        assertEquals(torsoArmor - 40f * efficiency, sections.armor(MechSection.TORSO), 1e-2f,
                "the overflow wears the torso's plate");
        assertEquals(0f, hit.spill(), 1e-3f);
        assertEquals(torsoStructure, sections.structure(MechSection.TORSO), 1e-3f);
    }

    @Test
    void overflowPastTheTorsoArmorTakesTorsoStructure() {
        MechSections sections = houndSections();
        float torsoStructure = sections.structure(MechSection.TORSO);
        float beyond = armorBreaker(sections, MechSection.TORSO) + 30f;

        strike(sections, MechSection.LEGS, destroying(sections, MechSection.LEGS, beyond));

        assertEquals(0f, sections.armor(MechSection.TORSO), 1e-2f);
        assertEquals(30f, hit.spill(), 1e-2f);
        assertEquals(torsoStructure - 30f, sections.structure(MechSection.TORSO), 1e-2f);
        assertFalse(hit.torsoDestroyed());
    }

    @Test
    void aHitOnADestroyedSectionGoesThroughTheHoleToTheTorso() {
        MechSections sections = houndSections();
        strike(sections, MechSection.LEGS, destroying(sections, MechSection.LEGS, 0f));
        float torsoArmor = sections.armor(MechSection.TORSO);
        float torsoStructure = sections.structure(MechSection.TORSO);

        strike(sections, MechSection.LEGS, 20f);

        assertEquals(0f, hit.sectionStructureDamage());
        assertFalse(hit.sectionDestroyed(), "it was already gone");
        assertEquals(torsoArmor - 20f * DurabilityModel.armorEfficiency(PEN, RATING),
                sections.armor(MechSection.TORSO), 1e-3f);
        assertEquals(torsoStructure, sections.structure(MechSection.TORSO), 1e-3f);
    }

    @Test
    void everyOtherSectionCanBeDestroyedWithoutKillingTheMech() {
        MechSections sections = houndSections();
        MechSection[] lost = {MechSection.LEGS, MechSection.ARMS, MechSection.LEFT_SHOULDER, MechSection.HEAD};

        for (MechSection section : lost) {
            strike(sections, section, destroying(sections, section, 0.01f));
            assertTrue(sections.destroyed(section), section.name());
            assertFalse(sections.torsoDestroyed(), section.name());
        }

        assertTrue(sections.crippled());
        assertTrue(sections.sensorsLost());
        assertFalse(sections.torsoDestroyed());
        assertFalse(sections.report().chassisLost());
        assertTrue(sections.structure(MechSection.TORSO) > 0f);
    }

    @Test
    void onlyTheTorsoReachingZeroKillsAndSoDoesSpillingPastARuinedSection() {
        MechSections direct = houndSections();
        strike(direct, MechSection.TORSO, destroying(direct, MechSection.TORSO, 100f));
        assertTrue(direct.torsoDestroyed());
        assertTrue(hit.torsoDestroyed());
        assertTrue(direct.report().chassisLost());

        MechSections spilled = houndSections();
        float torso = spilled.structure(MechSection.TORSO);
        strike(spilled, MechSection.HEAD, destroying(spilled, MechSection.HEAD,
                armorBreaker(spilled, MechSection.TORSO) + torso + 500f));
        assertTrue(spilled.torsoDestroyed(), "a head hit that spills the whole torso away still kills");
        assertEquals(0f, spilled.structure(MechSection.TORSO));
    }

    @Test
    void theReportSaysWhatWasLost() {
        MechSections sections = houndSections();
        strike(sections, MechSection.ARMS, destroying(sections, MechSection.ARMS, 0f));

        MechSections.Report report = sections.report();
        assertTrue(report.destroyed()[MechSection.ARMS.ordinal()]);
        assertEquals(0f, report.structureFraction()[MechSection.ARMS.ordinal()], 1e-4f);
        assertEquals(1f, report.structureFraction()[MechSection.TORSO.ordinal()], 1e-4f);
        assertFalse(report.fitted()[MechSection.RIGHT_SHOULDER.ordinal()]);
        assertNull(report.lostMounts()[MechMountSlot.ARMS.ordinal()]);
    }

    @Test
    void carriedWearIsLaidOnEachSectionAndNoSectionDropsDestroyed() {
        MechSections sections = houndSections();
        float[] structure = new float[MechSection.VALUES.length];
        float[] armor = new float[MechSection.VALUES.length];
        structure[MechSection.HEAD.ordinal()] = 0.5f;
        armor[MechSection.TORSO.ordinal()] = 0.25f;
        structure[MechSection.LEGS.ordinal()] = 1f;

        sections.carryWear(new MechCarriedWear(structure, armor));

        assertEquals(0.5f * sections.maxStructure(MechSection.HEAD), sections.structure(MechSection.HEAD), 1e-4f);
        assertEquals(0.75f * sections.maxArmor(MechSection.TORSO), sections.armor(MechSection.TORSO), 1e-4f);
        assertEquals(0.75f * sections.maxRearArmor(), sections.rearArmor(), 1e-4f);
        assertEquals(MechSections.CARRIED_STRUCTURE_FLOOR * sections.maxStructure(MechSection.LEGS),
                sections.structure(MechSection.LEGS), 1e-4f);
        assertFalse(sections.destroyed(MechSection.LEGS));
        assertEquals(sections.maxStructure(MechSection.TORSO), sections.structure(MechSection.TORSO), 1e-4f);
    }
}
