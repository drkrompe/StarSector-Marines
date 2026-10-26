package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFrame;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechInternalsTest {

    @Test
    void stockFrameReproducesTheShippedNumbersExactly() {
        assertFrame(MechVariant.BULWARK, 710f, 1.05f);
        assertFrame(MechVariant.HOUND, 375f, 1.55f);
        assertFrame(MechVariant.WARDEN, 300f, 1.32f);
    }

    private static void assertFrame(MechVariant variant, float armor, float speed) {
        CampaignMech mech = new CampaignMech("m", "M", variant, null,
                MissileReplenisherComponent.STANDARD.id());
        MechFrame frame = mech.freezeForDeployment().frame();
        assertEquals(new MechFrame(armor, speed, 100f, 6f), frame, variant.name());
        assertEquals(MechFrame.stock(variant), frame);
        assertFalse(mech.fit().overBudget(), variant + " stock fit must fit its industrial engine");
    }

    @Test
    void aFullBulwarkRefitRestoresThePreCutFrameWithinItsBudget() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechWorkshop workshop = new MechWorkshop(bay, TestPurse.rich());
        learnAll(bay);
        String id = MechBay.STARTER_MECH_ID;
        for (MechInternal internal : new MechInternal[]{MechInternal.MILITARY_ENGINE,
                MechInternal.HEAVY_PLATING, MechInternal.MILITARY_CAPACITOR_BANK, MechInternal.AUXILIARY_VENT_ARRAY}) {
            assertEquals(MechWorkshop.Status.FABRICATED_AND_INSTALLED,
                    workshop.fitInternal(id, internal).status(), internal.name());
        }
        MechFrameBudget.Fit fit = bay.mechById(id).fit();
        assertEquals(950f, fit.armorCapacity(), 1e-4f);
        assertEquals(1.15f, fit.moveSpeed(), 1e-4f);
        assertEquals(120f, fit.fluxCapacity(), 1e-4f);
        assertEquals(8.5f, fit.dissipationPerSecond(), 1e-4f);
        assertFalse(fit.overBudget());
        assertEquals(fit.frame(), bay.mechById(id).freezeForDeployment().frame());
    }

    @Test
    void aLightChassisCannotCarryTheTopOfEveryLadder() {
        MechBay bay = houndBay();
        learnAll(bay);
        String id = bay.activeSquad().mechs().get(0).id();
        assertFalse(bay.canInstallInternal(id, MechInternal.MILITARY_CAPACITOR_BANK),
                "a 2x2 capacitor bank does not fit the Hound's 2x1 reserve");
        assertTrue(bay.canInstallInternal(id, MechInternal.DOUBLE_CAPACITOR_BANK));
        assertFalse(bay.canInstallInternal(id, MechInternal.AUXILIARY_VENT_ARRAY),
                "the Hound has no utility socket");
    }

    @Test
    void anOverBudgetFitIsRefusedWithoutSpendingOrChangingTheMech() {
        MechBay bay = houndBay();
        learnAll(bay);
        TestPurse resources = TestPurse.rich();
        MechWorkshop workshop = new MechWorkshop(bay, resources);
        String id = bay.activeSquad().mechs().get(0).id();
        long credits = resources.credits;

        MechWorkshop.Result refused = workshop.fitInternal(id, MechInternal.HEAVY_PLATING);

        assertEquals(MechWorkshop.Status.OVER_BUDGET, refused.status());
        assertFalse(refused.succeeded());
        assertEquals(credits, resources.credits);
        assertEquals(MechInternal.FACTORY_PLATING, bay.mechById(id).internal(MechInternal.Role.PLATING));
        assertEquals(0, bay.ownedInternal(MechInternal.HEAVY_PLATING.id));

        // The engine buys the headroom: then the same plate fits.
        assertTrue(workshop.fitInternal(id, MechInternal.MILITARY_ENGINE).succeeded());
        assertTrue(workshop.fitInternal(id, MechInternal.HEAVY_PLATING).succeeded());
        MechFrameBudget.Fit fit = bay.mechById(id).fit();
        assertEquals(500f, fit.armorCapacity(), 1e-4f);
        assertEquals(1.70f, fit.moveSpeed(), 1e-4f);
        // The industrial engine went back to stores, and is now too small for this fit.
        assertEquals(1, bay.availableInternal(MechInternal.INDUSTRIAL_ENGINE.id));
        assertEquals(MechWorkshop.Status.OVER_BUDGET,
                workshop.fitInternal(id, MechInternal.INDUSTRIAL_ENGINE).status());
    }

    @Test
    void aMechAlreadyOverBudgetCanStillBeStrippedDown() {
        MechBay bay = MechBay.legacyStarterFixture();
        String id = MechBay.STARTER_MECH_ID;
        CampaignMech mech = bay.mechById(id);
        // A pre-budget fit: heavy plate on the industrial engine.
        mech.installInternal(MechInternal.Role.PLATING, MechInternal.HEAVY_PLATING);
        bay.addInternal(MechInternal.HEAVY_PLATING.id, 1);
        assertTrue(mech.fit().overBudget());
        bay.addWeapon(MechWeaponComponent.SRM_5.id, 1);
        assertTrue(bay.installWeapon(id, MechMountSlot.LEFT_SHOULDER, MechWeaponComponent.SRM_5.id),
                "a lighter rack is allowed even while the fit is still over budget");
        assertTrue(mech.fit().overBudget());
        assertFalse(bay.installWeapon(id, MechMountSlot.LEFT_SHOULDER, MechWeaponComponent.SRM_15.id),
                "going heavier again is refused");
        assertEquals(MechWeaponComponent.SRM_5, mech.leftShoulder());
    }

    @Test
    void lockedPatternsAreNotFabricatedAndStockPartsAreKnown() {
        MechBay bay = houndBay();
        MechWorkshop workshop = new MechWorkshop(bay, TestPurse.rich());
        String id = bay.activeSquad().mechs().get(0).id();
        assertEquals(MechWorkshop.Status.PATTERN_LOCKED,
                workshop.fitInternal(id, MechInternal.UPRATED_ENGINE).status());
        for (MechInternal.Role role : MechInternal.Role.values()) {
            if (role.stock() != null) assertTrue(bay.canFabricate(role.stock()), role.name());
        }
        assertTrue(bay.learnPattern(MechPattern.of(MechInternal.UPRATED_ENGINE).id()));
        assertEquals(MechWorkshop.Status.FABRICATED_AND_INSTALLED,
                workshop.fitInternal(id, MechInternal.UPRATED_ENGINE).status());
        assertEquals(1.625f, bay.mechById(id).fit().moveSpeed(), 1e-4f);
    }

    @Test
    void compartmentPartsReplaceOrStackWhileTheyPack() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechWorkshop workshop = new MechWorkshop(bay, TestPurse.rich());
        String id = MechBay.STARTER_MECH_ID;
        // A loader replaces the one fitted; the standard one goes back to stores.
        assertTrue(bay.installInternal(id, MechInternal.ACCELERATED_LOADER));
        assertEquals(1, bay.availableInternal(MechInternal.STANDARD_LOADER.id));
        // Vents stack: bank, loader and two vents are eight of the Bulwark's nine cells.
        bay.addInternal(MechInternal.AUXILIARY_VENT_ARRAY.id, 3);
        assertTrue(bay.installInternal(id, MechInternal.AUXILIARY_VENT_ARRAY));
        assertTrue(bay.installInternal(id, MechInternal.AUXILIARY_VENT_ARRAY));
        assertFalse(bay.canInstallInternal(id, MechInternal.AUXILIARY_VENT_ARRAY), "a third would need ten cells");
        assertEquals(MechWorkshop.Status.STRIPPED, workshop.stripVentArray(id).status());
        assertEquals(1, bay.mechById(id).compartment().stream()
                .filter(part -> part == MechInternal.AUXILIARY_VENT_ARRAY).count());
        assertEquals(2, bay.availableInternal(MechInternal.AUXILIARY_VENT_ARRAY.id));
    }

    @Test
    void lostSparesTakeFreeInternalsButNeverInstalledOnes() {
        MechBay bay = MechBay.legacyStarterFixture();
        bay.loseSpares();
        bay.addInternal(MechInternal.DOUBLE_CAPACITOR_BANK.id, 2);
        assertEquals(2, bay.loseSpares());
        assertEquals(0, bay.ownedInternal(MechInternal.DOUBLE_CAPACITOR_BANK.id));
        assertEquals(1, bay.ownedInternal(MechInternal.INDUSTRIAL_ENGINE.id));
    }

    @Test
    void anExistingSaveMigratesOntoTheStockFrame() throws Exception {
        MechBay bay = MechBay.legacyStarterFixture();
        // A save from before internals: no internal stores and no internal patterns.
        set(bay, "ownedInternals", null);
        Set<String> known = new LinkedHashSet<>();
        for (String patternId : bay.knownPatternIds()) {
            if (!patternId.contains(":internal:")) known.add(patternId);
        }
        set(bay, "knownPatternIds", known);

        MechBay loaded = roundTrip(bay);

        CampaignMech mech = loaded.mechById(MechBay.STARTER_MECH_ID);
        assertEquals(MechInternal.INDUSTRIAL_ENGINE, mech.internal(MechInternal.Role.ENGINE));
        assertEquals(MechFrame.stock(MechVariant.BULWARK), mech.freezeForDeployment().frame());
        assertEquals(1, loaded.ownedInternal(MechInternal.FACTORY_PLATING.id));
        assertEquals(0, loaded.availableInternal(MechInternal.FACTORY_PLATING.id));
        assertTrue(loaded.canFabricate(MechInternal.FACTORY_CAPACITOR_BANK));
        assertFalse(loaded.canFabricate(MechInternal.MILITARY_ENGINE));
    }

    private static MechBay houndBay() {
        MechBay bay = new MechBay();
        bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        return bay;
    }

    private static void learnAll(MechBay bay) {
        for (MechPattern pattern : MechPattern.all()) bay.learnPattern(pattern.id());
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = MechBay.class.getDeclaredField(field);
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


