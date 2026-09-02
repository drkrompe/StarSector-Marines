package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The small table behind what the company has released to its own polity. */
class CampaignStateReleasedKitColumnsTest {

    @Test
    void recordsOneReleaseAndFindsItAgain() {
        CampaignState state = new CampaignState();
        int slot = state.equipmentTemplateRegistry.intern("equipment-template:some_rifle:milspec");

        assertEquals(0, state.recordReleasedKit(slot));
        assertEquals(1, state.releasedKitCount);
        assertEquals(0, state.releasedKitRow(slot));
        assertEquals(slot, state.releasedKitTemplateId[0]);
    }

    @Test
    void theSameCardIsRefusedRatherThanListedTwice() {
        CampaignState state = new CampaignState();
        int slot = state.equipmentTemplateRegistry.intern("equipment-template:some_rifle:milspec");
        state.recordReleasedKit(slot);

        assertEquals(-1, state.recordReleasedKit(slot));
        assertEquals(-1, state.recordReleasedKit(-1), "an unnameable card is not a release");
        assertEquals(1, state.releasedKitCount);
    }

    @Test
    void theTableGrowsPastItsInitialCapacity() {
        CampaignState state = new CampaignState();
        for (int i = 0; i < 40; i++) {
            state.recordReleasedKit(state.equipmentTemplateRegistry.intern("card-" + i));
        }

        assertEquals(40, state.releasedKitCount);
        assertEquals(39, state.releasedKitRow(
                state.equipmentTemplateRegistry.intern("card-39")));
    }

    /** The registry and the column are one fact, so both have to survive a save. */
    @Test
    void aReleaseSurvivesSerialization() throws Exception {
        CampaignState state = new CampaignState();
        String cardId = "equipment-template:some_rifle:milspec";
        state.recordReleasedKit(state.equipmentTemplateRegistry.intern(cardId));

        CampaignState restored = roundTrip(state);

        assertEquals(1, restored.releasedKitCount);
        assertEquals(cardId,
                restored.equipmentTemplateRegistry.get(restored.releasedKitTemplateId[0]));
    }

    /**
     * A save from before the polity could be armed carries neither the registry nor the
     * column; it has to load as "nothing released", which is the Common floor alone.
     */
    @Test
    void legacySaveBackfillsTheReleasedKitColumn() throws Exception {
        CampaignState state = new CampaignState();
        state.equipmentTemplateRegistry = null;
        state.releasedKitTemplateId = null;
        state.releasedKitCount = 9;

        readResolve(state);

        assertNotNull(state.equipmentTemplateRegistry);
        assertNotNull(state.releasedKitTemplateId);
        assertEquals(0, state.releasedKitCount, "a count cannot outrun its arrays");
        assertEquals(-1, state.releasedKitTemplateId[0]);

        // Non-null is not the same as usable: the table has to work after the load.
        int slot = state.equipmentTemplateRegistry.intern("card");
        assertEquals(0, state.recordReleasedKit(slot));
        assertTrue(state.releasedKitRow(slot) >= 0);
    }

    /** A legacy save has no doctrine ints either, and reads as nothing spent. */
    @Test
    void legacySaveSpendsNoDoctrinePoints() {
        CampaignState state = new CampaignState();

        assertEquals(0, state.polityDoctrineQuality);
        assertEquals(0, state.polityDoctrineNumbers);
        assertEquals(0, state.polityDoctrineHeavySupport);
    }

    private static CampaignState roundTrip(CampaignState state) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(state);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (CampaignState) input.readObject();
        }
    }

    private static void readResolve(CampaignState state) throws Exception {
        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);
    }
}
