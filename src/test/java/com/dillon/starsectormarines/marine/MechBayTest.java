package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechBayTest {

    @Test
    void starterSquadOwnsInstalledStandardAndOneUpgrade() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);

        assertEquals(1, bay.activeSquad().mechs().size());
        assertEquals(MissileReplenisherComponent.STANDARD.id(),
                mech.missileReplenisherId());
        assertEquals(1, bay.ownedReplenisher(
                MissileReplenisherComponent.STANDARD.id()));
        assertEquals(0, bay.availableReplenisher(
                MissileReplenisherComponent.STANDARD.id()));
        assertEquals(1, bay.availableReplenisher(
                MissileReplenisherComponent.ACCELERATED_FEED.id()));
    }

    @Test
    void refitReturnsCurrentItemAndCannotOverAssignFiniteUpgrade() {
        MechBay bay = new MechBay();
        CampaignMech second = new CampaignMech(
                "support_mech_02", "Hound 02", MechVariant.HOUND,
                MechRole.ASSAULT, MissileReplenisherComponent.STANDARD.id());
        assertTrue(bay.addMech(MechBay.STARTER_SQUAD_ID, second));

        assertTrue(bay.installReplenisher(MechBay.STARTER_MECH_ID,
                MissileReplenisherComponent.ACCELERATED_FEED.id()));
        assertEquals(1, bay.availableReplenisher(
                MissileReplenisherComponent.STANDARD.id()),
                "the replaced standard unit should return to stores");
        assertFalse(bay.installReplenisher(second.id(),
                MissileReplenisherComponent.ACCELERATED_FEED.id()));
        assertEquals(MissileReplenisherComponent.STANDARD.id(),
                second.missileReplenisherId(),
                "failed atomic refit must preserve the prior component");
    }

    @Test
    void activeSquadFreezesValuesAndPersistsInstalledInventory() throws Exception {
        MechBay bay = new MechBay();
        assertTrue(bay.installReplenisher(MechBay.STARTER_MECH_ID,
                MissileReplenisherComponent.ACCELERATED_FEED.id()));

        MechBay restored = roundTrip(bay);
        List<MechDeploymentSpec> deployment = restored.activeDeployment();

        assertEquals(1, deployment.size());
        assertEquals(MechVariant.BULWARK, deployment.get(0).variant());
        assertEquals(MechRole.ARMORED_SUPPORT, deployment.get(0).role());
        assertEquals(MissileReplenisherComponent.ACCELERATED_FEED,
                deployment.get(0).missileReplenisher());
        assertEquals(0, restored.availableReplenisher(
                MissileReplenisherComponent.ACCELERATED_FEED.id()));
        assertEquals(1, restored.availableReplenisher(
                MissileReplenisherComponent.STANDARD.id()));
    }

    @Test
    void legacyRosterBackfillsTheMechBay() throws Exception {
        MarineRoster roster = new MarineRoster();
        Field mechBay = MarineRoster.class.getDeclaredField("mechBay");
        mechBay.setAccessible(true);
        mechBay.set(roster, null);
        Method readResolve = MarineRoster.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);

        readResolve.invoke(roster);

        assertEquals(1, roster.mechBay().activeDeployment().size());
        assertEquals(MechVariant.BULWARK,
                roster.mechBay().activeDeployment().get(0).variant());
    }

    private static MechBay roundTrip(MechBay bay) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(bay);
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (MechBay) in.readObject();
        }
    }
}
