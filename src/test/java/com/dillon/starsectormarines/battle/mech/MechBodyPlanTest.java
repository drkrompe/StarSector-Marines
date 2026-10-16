package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.BodyPart;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.MechHardpointGeometry.LocalPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which section a round lands on: height bands, the arms split, and the shoulder side. */
class MechBodyPlanTest {

    private static final MechBodyPlan HOUND = MechBodyPlan.HOUND;

    private static boolean[] fitted(boolean arms, boolean left, boolean right) {
        boolean[] fitted = new boolean[MechSection.VALUES.length];
        fitted[MechSection.LEGS.ordinal()] = true;
        fitted[MechSection.TORSO.ordinal()] = true;
        fitted[MechSection.HEAD.ordinal()] = true;
        fitted[MechSection.ARMS.ordinal()] = arms;
        fitted[MechSection.LEFT_SHOULDER.ordinal()] = left;
        fitted[MechSection.RIGHT_SHOULDER.ordinal()] = right;
        return fitted;
    }

    private static BodyPart band(MechBodyPlan plan, float height) {
        return plan.bodyPlan().atHeight(height);
    }

    @Test
    void heightsStackLegsTorsoShouldersHeadFromTheFeetUp() {
        // Hound bands: legs 0.35, torso 0.40, shoulders 0.17, head 0.08.
        String legs = band(HOUND, 0f).name();
        assertEquals(legs, band(HOUND, 0.34f).name());
        assertEquals("TORSO", band(HOUND, 0.36f).name());
        assertEquals("TORSO", band(HOUND, 0.74f).name());
        assertEquals("SHOULDERS", band(HOUND, 0.76f).name());
        assertEquals("SHOULDERS", band(HOUND, 0.91f).name());
        assertEquals("HEAD", band(HOUND, 0.93f).name());
        assertEquals("HEAD", band(HOUND, 1f).name());
        assertEquals("LEGS", legs);
    }

    @Test
    void uniformHeightsLandOnEachBandInProportionToItsShare() {
        int n = 20_000;
        int[] counts = new int[HOUND.bodyPlan().size()];
        for (int i = 0; i < n; i++) counts[band(HOUND, (i + 0.5f) / n).index()]++;
        float[] expected = {0.08f, 0.17f, 0.40f, 0.35f};   // head, shoulders, torso, legs: part index order
        for (BodyPart part : HOUND.bodyPlan().parts()) {
            assertEquals(part.share, counts[part.index()] / (float) n, 2e-3f, part.name());
            assertEquals(expected[part.index()], part.share, 1e-6f, part.name());
        }
    }

    @Test
    void legsAndHeadAlwaysMapToTheirOwnSection() {
        boolean[] all = fitted(true, true, true);
        assertEquals(MechSection.LEGS, HOUND.section(band(HOUND, 0.1f), true, 0.5f, all));
        assertEquals(MechSection.HEAD, HOUND.section(band(HOUND, 0.99f), false, 0.5f, all));
    }

    @Test
    void torsoBandGoesToArmsOnlyBelowTheArmShareAndWhenArmsAreFitted() {
        BodyPart torsoBand = band(HOUND, 0.5f);
        float armShare = HOUND.armShare();
        boolean[] withArms = fitted(true, true, false);
        boolean[] withoutArms = fitted(false, true, false);

        assertEquals(MechSection.ARMS, HOUND.section(torsoBand, true, armShare - 0.01f, withArms));
        assertEquals(MechSection.TORSO, HOUND.section(torsoBand, true, armShare + 0.01f, withArms));
        assertEquals(MechSection.TORSO, HOUND.section(torsoBand, true, 0f, withoutArms));
    }

    @Test
    void shoulderBandPicksTheSideTheRoundCameFrom() {
        BodyPart shoulders = band(MechBodyPlan.BULWARK, 0.8f);
        boolean[] both = fitted(true, true, true);

        assertEquals(MechSection.LEFT_SHOULDER, MechBodyPlan.BULWARK.section(shoulders, true, 0.5f, both));
        assertEquals(MechSection.RIGHT_SHOULDER, MechBodyPlan.BULWARK.section(shoulders, false, 0.5f, both));
    }

    @Test
    void aLonePodOnAHoundIsCentredSoEitherSideHitsIt() {
        // The Hound's real fit: nose gun and one SRM pod on the left shoulder, the right empty.
        boolean[] houndFit = fitted(true, true, false);
        BodyPart shoulders = band(HOUND, 0.8f);

        assertTrue(HOUND.centresLonePod());
        assertEquals(MechSection.LEFT_SHOULDER, HOUND.section(shoulders, true, 0.5f, houndFit));
        assertEquals(MechSection.LEFT_SHOULDER, HOUND.section(shoulders, false, 0.5f, houndFit),
                "a right-side round meets the centred left pod, there being nothing on the right");
    }

    @Test
    void anEmptyShoulderOnABulwarkIsTheTorsosPlate() {
        MechBodyPlan bulwark = MechBodyPlan.BULWARK;
        assertFalse(bulwark.centresLonePod());
        BodyPart shoulders = band(bulwark, 0.8f);
        boolean[] leftOnly = fitted(true, true, false);

        assertEquals(MechSection.LEFT_SHOULDER, bulwark.section(shoulders, true, 0.5f, leftOnly));
        assertEquals(MechSection.TORSO, bulwark.section(shoulders, false, 0.5f, leftOnly));
    }

    @Test
    void fromLeftReadsAgainstTheTorsoFacing() {
        // Facing 0: forward is +y, local -x is left.
        assertTrue(MechSections.fromLeft(0f, -5f, 0f));
        assertFalse(MechSections.fromLeft(0f, 5f, 0f));
        // Facing 90: forward is world -x, so left is world -y.
        assertTrue(MechSections.fromLeft(90f, 0f, -5f));
        assertFalse(MechSections.fromLeft(90f, 0f, 5f));
    }

    @Test
    void fromLeftAgreesWithWhereTheLeftAndRightShouldersAreDrawn() {
        // A two-pod chassis's pods in its own frame, turned by the torso the way the muzzle is.
        LocalPoint left = MechHardpointGeometry.podMuzzle(LayeredMechAppearance.CHASSIS_CLEAN,
                LayeredMechAppearance.POD_SRM, LayeredMechAppearance.POD_SRM, true, LayeredMechAppearance.POD_SRM);
        LocalPoint right = MechHardpointGeometry.podMuzzle(LayeredMechAppearance.CHASSIS_CLEAN,
                LayeredMechAppearance.POD_SRM, LayeredMechAppearance.POD_SRM, false, LayeredMechAppearance.POD_SRM);
        for (float facing : new float[] {0f, 90f, 215f, 330f}) {
            float[] l = turned(left, facing);
            float[] r = turned(right, facing);
            assertTrue(MechSections.fromLeft(facing, l[0], l[1]), "left pod at facing " + facing);
            assertFalse(MechSections.fromLeft(facing, r[0], r[1]), "right pod at facing " + facing);
        }
    }

    /** The rotation {@link MechHardpointGeometry#muzzle} applies to a local point. */
    private static float[] turned(LocalPoint p, float facingDegrees) {
        double radians = Math.toRadians(facingDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new float[] {p.xHullWidths() * cos - p.yHullWidths() * sin,
                p.xHullWidths() * sin + p.yHullWidths() * cos};
    }
}
