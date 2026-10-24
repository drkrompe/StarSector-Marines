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

    private static List<SocketDef> grid(int columns, int rows) {
        return List.of(bay(SocketId.AUXILIARY, columns, rows));
    }

    private static SocketDef bay(SocketId id, int columns, int rows) {
        return new SocketDef(id, SocketType.AUXILIARY, columns, rows, 0f, 0f, 0f, 1f, 1f, 1f, false);
    }

    /** The Bulwark's shape: two 2x2 flank bays that pool. */
    private static final List<SocketDef> DUAL = List.of(bay(SocketId.AUXILIARY, 2, 2), bay(SocketId.AUXILIARY_2, 2, 2));

    @Test
    void partsPackWholeWithoutOverlapAndNeverTurn() {
        List<MechCompartment.Placement> placed = MechCompartment.pack(grid(2, 3), List.of(LOADER, BIG_BANK));
        assertNotNull(placed);
        assertEquals(BIG_BANK, placed.get(1).part(), "placements come back in the order given");
        boolean[][] taken = new boolean[3][2];
        for (MechCompartment.Placement part : placed) {
            assertEquals(part.part().footprintColumns, part.columns(), "a part stands as authored");
            for (int r = part.row(); r < part.row() + part.rows(); r++) {
                for (int c = part.column(); c < part.column() + part.columns(); c++) {
                    assertFalse(taken[r][c], "overlap at " + c + "," + r);
                    taken[r][c] = true;
                }
            }
        }
        // A 2x1 part never turns on end, so a one-column strip holds nothing.
        assertNull(MechCompartment.pack(grid(1, 3), List.of(LOADER)));

        assertNotNull(MechCompartment.pack(grid(2, 2), List.of(BANK, LOADER)));
        assertNull(MechCompartment.pack(grid(2, 2), List.of(BIG_BANK, LOADER)), "six cells into four");
        assertNotNull(MechCompartment.pack(grid(2, 3), List.of(BANK, LOADER, VENT)));
        assertEquals(List.of(), MechCompartment.pack(grid(2, 2), List.of()));
        assertNull(MechCompartment.pack(List.of(), List.of(BANK)));
    }

    @Test
    void twoBaysPoolButAPartLandsWholeInOne() {
        // The big bank fills one bay, two small parts the other; a third small part has no room.
        List<MechCompartment.Placement> placed = MechCompartment.pack(DUAL, List.of(BIG_BANK, LOADER, VENT));
        assertNotNull(placed);
        assertEquals(List.of(BIG_BANK), MechCompartment.in(placed, SocketId.AUXILIARY).stream()
                .map(MechCompartment.Placement::part).toList());
        assertEquals(2, MechCompartment.in(placed, SocketId.AUXILIARY_2).size());
        assertNull(MechCompartment.pack(DUAL, List.of(BIG_BANK, LOADER, VENT, VENT)));
        assertNotNull(MechCompartment.pack(DUAL, List.of(BANK, LOADER, VENT, VENT)), "four small parts, two a bay");
        assertEquals(8, MechCompartment.capacity(DUAL));
    }

    @Test
    void oneBankAndOneLoaderAtMostWhileVentsStack() {
        assertFalse(MechCompartment.fits(DUAL, List.of(BANK, MechInternal.DOUBLE_CAPACITOR_BANK)));
        assertFalse(MechCompartment.fits(DUAL, List.of(BANK, LOADER, MechInternal.ACCELERATED_LOADER)));
        assertTrue(MechCompartment.fits(DUAL, List.of(BANK, VENT, VENT)));
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
        // The same parts fit a Bulwark's two bays, in the old order: bank, loader, vent.
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
