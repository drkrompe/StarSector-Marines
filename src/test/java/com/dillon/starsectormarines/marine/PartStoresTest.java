package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stores hold spares only, on every machine's shelf: a fit takes one off and what comes off is stowed. */
class PartStoresTest {

    @Test
    void aTakeNeedsASpareAndARenameCarriesItsCountOver() {
        PartStores stores = new PartStores();
        assertFalse(stores.take("a"));
        stores.stow("a", 2);
        stores.stow("old");
        assertTrue(stores.take("a"));
        assertEquals(1, stores.count("a"));
        stores.rekey(id -> id.equals("old") ? "a" : id);
        assertEquals(2, stores.count("a"));
        assertEquals(2, stores.clear());
        assertEquals(0, stores.count("a"));
    }

    @Test
    void aStripStowsThePartAndARefitTakesItBack() {
        MechBay bay = MechBay.legacyStarterFixture();
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);
        MechInternal engine = mech.internal(MechInternal.Role.ENGINE);
        int spares = bay.availableInternal(engine.id);
        int owned = bay.ownedInternal(engine.id);

        assertTrue(bay.stripInternal(mech.id(), MechInternal.Role.ENGINE));
        assertEquals(spares + 1, bay.availableInternal(engine.id), "the engine that came off is a spare");
        assertEquals(owned, bay.ownedInternal(engine.id), "owned is the spares and the fitted together");
        assertTrue(bay.installInternal(mech.id(), engine));
        assertEquals(spares, bay.availableInternal(engine.id), "the refit took it off the shelf");
    }

    /** A save from before spares were kept apart counted fitted copies as owned; what is not fitted becomes a spare. */
    @Test
    void anOldSavesOwnedCountsSettleIntoSparesOnce() throws Exception {
        MechBay bay = MechBay.legacyStarterFixture();
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);
        String weapon = mech.weaponAt(MechMountSlot.LEFT_ARM).id;
        int fitted = bay.installedWeapon(weapon);
        bay.loseSpares();
        Map<String, Integer> owned = new HashMap<>();
        owned.put(weapon, fitted + 3);
        set(bay, "ownedWeapons", owned);

        resolve(bay);
        assertEquals(3, bay.availableWeapon(weapon));
        assertTrue(((Map<?, ?>) get(bay, "ownedWeapons")).isEmpty(), "nothing left to settle twice");
        resolve(bay);
        assertEquals(3, bay.availableWeapon(weapon));
    }

    private static void resolve(MechBay bay) throws Exception {
        Method resolve = MechBay.class.getDeclaredMethod("readResolve");
        resolve.setAccessible(true);
        resolve.invoke(bay);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
