package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A callsign is bounded display text on one campaign mech, frozen at deployment. */
class CampaignMechCallsignTest {

    private static CampaignMech hound() {
        return new CampaignMech("support_mech_07", "Hound 07", MechVariant.HOUND,
                MechRole.ASSAULT, MissileReplenisherComponent.STANDARD.id());
    }

    @Test
    void renameTrimsAndAcceptsUpToTheBound() {
        CampaignMech mech = hound();
        assertTrue(mech.rename("  The Hound \t"));
        assertEquals("The Hound", mech.displayName());

        String longest = "W".repeat(CampaignMech.CALLSIGN_MAX_LENGTH);
        assertTrue(mech.rename(" " + longest + " "));
        assertEquals(longest, mech.displayName());
    }

    @Test
    void refusedInputKeepsTheCurrentNameAndEverythingElse() {
        CampaignMech mech = hound();
        assertTrue(mech.rename("The Hound"));
        MechWeaponComponent arms = mech.arms();
        MechWeaponComponent left = mech.leftShoulder();
        MechWeaponComponent right = mech.rightShoulder();

        assertFalse(mech.rename(null));
        assertFalse(mech.rename(""));
        assertFalse(mech.rename(" \t \u0085"));
        assertFalse(mech.rename("W".repeat(CampaignMech.CALLSIGN_MAX_LENGTH + 1)));

        assertEquals("The Hound", mech.displayName());
        assertEquals("support_mech_07", mech.id());
        assertSame(MechVariant.HOUND, mech.variant());
        assertSame(MechRole.ASSAULT, mech.role());
        assertSame(arms, mech.arms());
        assertSame(left, mech.leftShoulder());
        assertSame(right, mech.rightShoulder());
    }

    @Test
    void renameChangesNeitherIdentityNorFit() {
        CampaignMech mech = hound();
        MechDeploymentSpec before = mech.freezeForDeployment();
        assertTrue(mech.rename("The Hound"));
        MechDeploymentSpec after = mech.freezeForDeployment();

        assertEquals(before.campaignId(), after.campaignId());
        assertEquals(before.variant(), after.variant());
        assertEquals(before.role(), after.role());
        assertEquals(before.arms(), after.arms());
        assertEquals(before.frame(), after.frame());
        assertEquals("The Hound", after.displayName());
    }

    @Test
    void deploymentFreezesTheCallsignAtCommitment() {
        CampaignMech mech = hound();
        assertTrue(mech.rename("The Hound"));
        MechDeploymentSpec committed = mech.freezeForDeployment();

        assertTrue(mech.rename("Second Thoughts"));

        assertEquals("The Hound", committed.displayName());
        assertEquals("support_mech_07", committed.campaignId());
    }

    @Test
    void renamedCallsignSurvivesTheSavePath() throws Exception {
        MechBay bay = MechBay.legacyStarterFixture();
        assertTrue(bay.mechById(MechBay.STARTER_MECH_ID).rename("Ironside"));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(bay);
        }
        MechBay loaded;
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            loaded = (MechBay) input.readObject();
        }

        CampaignMech mech = loaded.mechById(MechBay.STARTER_MECH_ID);
        assertEquals("Ironside", mech.displayName());
        assertEquals(MechBay.STARTER_MECH_ID, mech.id());
    }
}
