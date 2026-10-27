package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.mech.MechRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A landed support mech is named by its frozen callsign, or by the prior fallback without one. */
class MechSupportLandedNameTest {

    private static MechDeploymentSpec named(String callsign) {
        MechVariant variant = MechVariant.HOUND;
        return new MechDeploymentSpec(variant, MechRole.ASSAULT,
                MissileReplenisherComponent.STANDARD, variant.arms,
                variant.leftShoulder, variant.rightShoulder, callsign);
    }

    @Test
    void frozenCallsignNamesTheLandedMech() {
        assertEquals("The Hound", MechSupportPayload.landedName(named("The Hound"), "support-12"));
    }

    @Test
    void deploymentWithoutCallsignKeepsTheFallback() {
        assertEquals("support-12", MechSupportPayload.landedName(
                MechDeploymentSpec.standard(MechVariant.BULWARK), "support-12"));
        assertEquals("support-12", MechSupportPayload.landedName(named(null), "support-12"));
    }

    @Test
    void blankCallsignIsNoCallsign() {
        MechDeploymentSpec blank = named("   ");
        assertNull(blank.displayName());
        assertEquals("support-12", MechSupportPayload.landedName(blank, "support-12"));
        assertEquals("The Hound", named("  The Hound ").displayName());
    }
}
