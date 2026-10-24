package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The auxiliary compartment's rules: packing, role limits, readiness and save migration. */
class MechCompartmentTest {

    private static final MechInternal BANK = MechInternal.FACTORY_CAPACITOR_BANK;
    private static final MechInternal BIG_BANK = MechInternal.MILITARY_CAPACITOR_BANK;
    private static final MechInternal LOADER = MechInternal.STANDARD_LOADER;
    private static final MechInternal VENT = MechInternal.AUXILIARY_VENT_ARRAY;

    private static SocketDef grid(int columns, int rows) {
        return new SocketDef(SocketId.AUXILIARY, SocketType.AUXILIARY, columns, rows,
                0f, 0f, 0f, 1f, 1f, 1f, false);
    }

    @Test
    void partsPackWholeWithoutOverlapAndTurnToFit() {
        // A 2x2 with a 2x1 turned on end beside it fills a 3x2.
        List<MechCompartment.Placement> placed = MechCompartment.pack(grid(3, 2), List.of(LOADER, BIG_BANK));
        assertNotNull(placed);
        assertEquals(BIG_BANK, placed.get(1).part(), "placements come back in the order given");
        boolean[][] taken = new boolean[2][3];
        for (MechCompartment.Placement part : placed) {
            for (int r = part.row(); r < part.row() + part.rows(); r++) {
                for (int c = part.column(); c < part.column() + part.columns(); c++) {
                    assertFalse(taken[r][c], "overlap at " + c + "," + r);
                    taken[r][c] = true;
                }
            }
        }
        assertEquals(1, placed.get(0).columns(), "the loader turns to fit the last column");

        assertNotNull(MechCompartment.pack(grid(2, 2), List.of(BANK, LOADER)));
        assertNull(MechCompartment.pack(grid(2, 2), List.of(BIG_BANK, LOADER)), "five cells into four");
        // A 3x3 holds the big bank and two more parts, never three: the leftover L is five cells.
        assertNotNull(MechCompartment.pack(grid(3, 3), List.of(BIG_BANK, LOADER, VENT)));
        assertNull(MechCompartment.pack(grid(3, 3), List.of(BIG_BANK, LOADER, VENT, VENT)));
        assertNotNull(MechCompartment.pack(grid(3, 3), List.of(BANK, LOADER, VENT, VENT)), "four 2x1s pack a 3x3");
        assertEquals(List.of(), MechCompartment.pack(grid(2, 2), List.of()));
        assertNull(MechCompartment.pack(null, List.of(BANK)));
    }

    @Test
    void oneBankAndOneLoaderAtMostWhileVentsStack() {
        SocketDef big = grid(3, 3);
        assertFalse(MechCompartment.fits(big, List.of(BANK, MechInternal.DOUBLE_CAPACITOR_BANK)));
        assertFalse(MechCompartment.fits(big, List.of(BANK, LOADER, MechInternal.ACCELERATED_LOADER)));
        assertTrue(MechCompartment.fits(big, List.of(BANK, VENT, VENT)));
    }

    @Test
    void aBareFrameNeedsABankButNotALoaderToDrop() {
        CampaignMech mech = CampaignMech.frame("frame", MechVariant.HOUND);
        assertTrue(mech.compartment().isEmpty(), "a bought frame brings no loader");
        assertEquals(List.of(MechInternal.Role.ENGINE, MechInternal.Role.CAPACITOR_BANK), mech.missingInternals());
        assertEquals(MissileReplenisherComponent.NONE, mech.missileReplenisher());

        mech.installInternal(MechInternal.Role.ENGINE, MechInternal.INDUSTRIAL_ENGINE);
        mech.installInternal(MechInternal.Role.CAPACITOR_BANK, BIG_BANK);
        assertEquals(CampaignMech.Readiness.READY, mech.readiness(0f), "a loader is a choice, not a gap");
        assertEquals(MissileReplenisherComponent.NONE, mech.freezeForDeployment().missileReplenisher());
    }

    @Test
    void anOldSaveMigratesItsSocketsIntoTheCompartmentAndSparesWhatNoLongerPacks() throws Exception {
        // A Hound saved with a military bank, a faster loader and a vent: the bank alone fills 2x2.
        CampaignMech hound = legacy(MechVariant.HOUND, BIG_BANK.id, "accelerated_feed", VENT.id);
        assertEquals(List.of(BIG_BANK), hound.compartment());
        // The same parts fit a Bulwark's 3x3, in the old order: bank, loader, vent.
        CampaignMech bulwark = legacy(MechVariant.BULWARK, BIG_BANK.id, "accelerated_feed", VENT.id);
        assertEquals(List.of(BIG_BANK, MechInternal.ACCELERATED_LOADER, VENT), bulwark.compartment());
        assertEquals(MissileReplenisherComponent.ACCELERATED_FEED, bulwark.missileReplenisher());
        // A missing bank id was the stock bank; an emptied one stays empty.
        assertEquals(List.of(BANK, LOADER), legacy(MechVariant.WARDEN, null, "standard_replenisher", null).compartment());
        CampaignMech emptied = legacy(MechVariant.WARDEN, CampaignMech.EMPTY_COMPONENT_ID, "standard_replenisher", null);
        assertEquals(CampaignMech.Readiness.INCOMPLETE, emptied.readiness(0f));

        // The bay's replenisher stock becomes loader stock, so the Hound's dropped loader is a spare.
        MechBay bay = new MechBay();
        assertTrue(bay.addMech(MechBay.STARTER_SQUAD_ID, hound));
        Map<String, Integer> oldStock = new HashMap<>(Map.of("accelerated_feed", 1, "standard_replenisher", 2));
        set(bay, "ownedReplenishers", oldStock);
        MechBay restored = roundTrip(bay);
        assertEquals(1, restored.availableInternal(MechInternal.ACCELERATED_LOADER.id));
        assertEquals(2, restored.ownedInternal(LOADER.id));
    }

    /** A mech as a save from before the compartment holds it. */
    private static CampaignMech legacy(MechVariant variant, String bankId, String replenisherId, String ventId)
            throws Exception {
        CampaignMech mech = new CampaignMech("legacy-" + variant.id, "Legacy", variant, MechRole.ASSAULT, null);
        set(mech, "auxiliaryIds", null);
        set(mech, "capacitorBankId", bankId);
        set(mech, "missileReplenisherId", replenisherId);
        set(mech, "ventArrayId", ventId);
        return mech;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static MechBay roundTrip(MechBay bay) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(bay);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (MechBay) in.readObject();
        }
    }
}
