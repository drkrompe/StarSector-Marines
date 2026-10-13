package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechWorkshopTest {

    @Test void restoredBerthsGateFabricationAtCommitWithoutSpendingOrDeletingOwnedMechs() {
        var bay = new MechBay();
        var resources = TestResources.stocked(2000);
        int[] berths = {0};
        var workshop = new MechWorkshop(bay, resources, () -> berths[0]);
        for (int count = 0; count <= 4; count++) {
            int supplies = resources.available(Commodities.SUPPLIES);
            assertEquals(MechWorkshop.Status.LANCE_FULL,
                    workshop.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND).status());
            assertEquals(supplies, resources.available(Commodities.SUPPLIES));
            assertEquals(count, bay.activeSquad().mechs().size());
            if (count < 4) {
                berths[0]++;
                assertTrue(workshop.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND).succeeded());
            }
        }
        berths[0] = 1;
        assertFalse(workshop.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND).succeeded());
        assertEquals(4, bay.activeSquad().mechs().size());
    }

    @Test
    void fabricatesAndInstallsAWeaponAsOneMaterialTransaction() {
        MechBay bay = MechBay.legacyStarterFixture();
        TestResources resources = TestResources.stocked(100);
        MechWorkshop workshop = new MechWorkshop(bay, resources);

        MechWorkshop.Result result = workshop.fitWeapon(MechBay.STARTER_MECH_ID,
                MechMountSlot.ARMS, MechWeaponComponent.DUAL_PULSE_LASERS);

        assertEquals(MechWorkshop.Status.FABRICATED_AND_INSTALLED, result.status());
        assertEquals(MechWeaponComponent.DUAL_PULSE_LASERS,
                bay.mechById(MechBay.STARTER_MECH_ID).arms());
        assertEquals(70, resources.available(Commodities.SUPPLIES));
        assertEquals(82, resources.available(Commodities.HEAVY_MACHINERY));
        assertEquals(74, resources.available(Commodities.METALS));
        assertEquals(88, resources.available(Commodities.RARE_METALS));
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.DUAL_CHAINGUNS.id));
    }

    @Test
    void insufficientMaterialsChangeNeitherCargoNorInstalledWeapon() {
        MechBay bay = MechBay.legacyStarterFixture();
        TestResources resources = TestResources.stocked(5);
        MechWorkshop workshop = new MechWorkshop(bay, resources);

        MechWorkshop.Result result = workshop.fitWeapon(MechBay.STARTER_MECH_ID,
                MechMountSlot.ARMS, MechWeaponComponent.DUAL_PULSE_LASERS);

        assertEquals(MechWorkshop.Status.INSUFFICIENT_MATERIALS, result.status());
        assertEquals(MechWeaponComponent.DUAL_CHAINGUNS,
                bay.mechById(MechBay.STARTER_MECH_ID).arms());
        assertEquals(5, resources.available(Commodities.SUPPLIES));
        assertEquals(0, bay.ownedWeapon(MechWeaponComponent.DUAL_PULSE_LASERS.id));
    }

    @Test
    void removedWeaponReturnsToStoresAndLeavesTheDeploymentMountEmpty() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechWorkshop workshop = new MechWorkshop(bay, TestResources.stocked(0));

        MechWorkshop.Result result = workshop.stripWeapon(MechBay.STARTER_MECH_ID,
                MechMountSlot.ARMS);

        assertEquals(MechWorkshop.Status.STRIPPED, result.status());
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.DUAL_CHAINGUNS.id));
        assertNull(bay.mechById(MechBay.STARTER_MECH_ID).arms());
        assertNull(bay.activeDeployment().get(0).arms());
    }

    @Test
    void installedWeaponMovesAtomicallyOnlyToACompatibleVacantMount() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechWorkshop workshop = new MechWorkshop(bay, TestResources.stocked(0));
        assertTrue(bay.stripWeapon(MechBay.STARTER_MECH_ID,
                MechMountSlot.RIGHT_SHOULDER));

        MechWorkshop.Result moved = workshop.moveWeapon(MechBay.STARTER_MECH_ID,
                MechMountSlot.LEFT_SHOULDER, MechMountSlot.RIGHT_SHOULDER);

        assertEquals(MechWorkshop.Status.MOVED, moved.status());
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);
        assertNull(mech.leftShoulder());
        assertEquals(MechWeaponComponent.SRM_15, mech.rightShoulder());

        MechWorkshop.Result occupied = workshop.moveWeapon(MechBay.STARTER_MECH_ID,
                MechMountSlot.ARMS, MechMountSlot.RIGHT_SHOULDER);
        assertEquals(MechWorkshop.Status.TARGET_OCCUPIED, occupied.status());
        assertEquals(MechWeaponComponent.DUAL_CHAINGUNS, mech.arms());
        assertEquals(MechWeaponComponent.SRM_15, mech.rightShoulder());
    }

    @Test
    void chassisFabricationBuildsTheLanceUntilAllFourGantriesAreOccupied() {
        MechBay bay = new MechBay();
        TestResources resources = TestResources.stocked(2_000);
        MechWorkshop workshop = new MechWorkshop(bay, resources);

        MechWorkshop.Result hound = workshop.fabricateChassis(
                MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        assertTrue(hound.succeeded());
        assertNotNull(hound.mech());
        assertEquals(MechVariant.HOUND, hound.mech().variant());
        assertEquals(MechWeaponComponent.NOSE_CHAINGUN, hound.mech().arms());
        assertTrue(workshop.fabricateChassis(MechBay.STARTER_SQUAD_ID,
                MechVariant.HOUND).succeeded());
        assertTrue(workshop.fabricateChassis(MechBay.STARTER_SQUAD_ID,
                MechVariant.HOUND).succeeded());
        assertTrue(workshop.fabricateChassis(MechBay.STARTER_SQUAD_ID,
                MechVariant.HOUND).succeeded());

        int supplies = resources.available(Commodities.SUPPLIES);
        MechWorkshop.Result full = workshop.fabricateChassis(
                MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        assertFalse(full.succeeded());
        assertEquals(MechWorkshop.Status.LANCE_FULL, full.status());
        assertEquals(supplies, resources.available(Commodities.SUPPLIES));
        assertEquals(4, bay.activeSquad().mechs().size());
    }

    static final class TestResources implements FabricationResources {
        private final Map<String, Integer> stock = new HashMap<>();

        static TestResources stocked(int quantity) {
            TestResources resources = new TestResources();
            resources.stock.put(Commodities.SUPPLIES, quantity);
            resources.stock.put(Commodities.HEAVY_MACHINERY, quantity);
            resources.stock.put(Commodities.METALS, quantity);
            resources.stock.put(Commodities.RARE_METALS, quantity);
            return resources;
        }

        @Override public int available(String commodityId) {
            return stock.getOrDefault(commodityId, 0);
        }

        @Override public String commodityName(String commodityId) {
            return commodityId.replace('_', ' ');
        }

        @Override public String commodityIcon(String commodityId) {
            return "graphics/icons/cargo/" + commodityId + ".png";
        }

        @Override public boolean spend(FabricationCost cost) {
            if (!canAfford(cost)) return false;
            for (FabricationCost.Line line : cost.lines()) {
                stock.merge(line.commodityId(), -line.quantity(), Integer::sum);
            }
            return true;
        }
    }
}
