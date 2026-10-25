package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Section damage and the repair clock persist; a save from before them reads as whole. */
class CampaignMechDamageSaveTest {

    @Test void aMechSavedBeforeSectionDamageLoadsWholeAndReady() throws Exception {
        CampaignMech old = roundTrip(new CampaignMech("old", "Old Faithful", MechVariant.BULWARK,
                MechRole.ARMORED_SUPPORT, MissileReplenisherComponent.STANDARD.id()), true);

        assertNull(old.damageOn(100f));
        assertEquals(0f, old.repairUntilDay());
        assertEquals(CampaignMech.Readiness.READY, old.readiness(100f));
    }

    @Test void aRepairPaidForBeforeRepairsWereFreeDropsWhole() throws Exception {
        CampaignMech paid = new CampaignMech("p", "Paid", MechVariant.HOUND, null,
                MissileReplenisherComponent.STANDARD.id());
        setField(paid, "repairUntilDay", 42.5f);

        assertEquals(CampaignMech.Readiness.READY, roundTrip(paid, false).readiness(40f));
    }

    @Test void damageAndTheRepairClockSurviveASave() throws Exception {
        CampaignMech repairing = new CampaignMech("r", "Repairing", MechVariant.HOUND, null,
                MissileReplenisherComponent.STANDARD.id());
        repairing.startRepair(MechDamage.of(MechDamageTest.ReportFixture.whole()
                .section(MechSection.LEGS, 0.25f, 0f).build()), 40f, 42f);

        CampaignMech loaded = roundTrip(repairing, false);

        assertEquals(0.75f, loaded.damageOn(40f).structureLost(MechSection.LEGS), 1e-6f);
        assertEquals(0.5f, loaded.damageOn(41f).armorLost(MechSection.LEGS), 1e-6f);
        assertEquals(42f, loaded.repairUntilDay(), 1e-6f);
        assertEquals(CampaignMech.Readiness.UNDER_REPAIR, loaded.readiness(41f));
    }

    /**
     * Serializes {@code mech} and reads it back. With {@code asOldSave} the damage fields are
     * first set to what a save written before they existed deserializes them as (Java
     * serialization and XStream both leave an absent field at its default).
     */
    private static CampaignMech roundTrip(CampaignMech mech, boolean asOldSave) throws Exception {
        if (asOldSave) {
            setField(mech, "damage", null);
            setField(mech, "repairUntilDay", 0f);
            setField(mech, "repairStartDay", 0f);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(mech);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (CampaignMech) in.readObject();
        }
    }

    static void setField(CampaignMech mech, String field, Object value) {
        try {
            Field f = CampaignMech.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(mech, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
