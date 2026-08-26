package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.SatchelChargeSpec;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatchelChargeTest {

    private static final float EPS = 1e-4f;

    private static BattleSimulation openArena(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static long carrier(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("satchel-" + sim.liveUnitCount(),
                Faction.MARINE, UnitType.MARINE, x, y)
                .specialEquipment(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID), 0));
    }

    private static long turret(BattleSimulation sim, int x, int y) {
        return sim.spawn(MapTurret.create("turret-" + sim.liveUnitCount(),
                Faction.DEFENDER, TurretCatalogRegistry.VULCAN_STRUCTURE_ID, x, y));
    }

    @Test
    void reusableKitExistsAtZeroAmmoAndNeverCreatesAnApproach() {
        BattleSimulation sim = openArena(20, 12);
        long marine = carrier(sim, 3, 5);
        turret(sim, 7, 5);

        assertTrue(sim.world().hasSecondaryWeapon(marine));
        assertEquals(0, sim.world().secondaryAmmo(marine));
        assertTrue(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID).hasAvailableUse(0));
        assertFalse(InfantryUnitPrep.tryOpportunitySpecial(marine, sim));
        assertEquals(0, sim.world().path(marine).length,
                "an out-of-contact satchel target must not create a pursuit path");
    }

    @Test
    void deploymentLoadoutRetainsAReusableZeroAmmoKit() {
        BattleSimulation sim = openArena(12, 12);
        EntitySpec spec = new EntitySpec("deployed", Faction.MARINE,
                UnitType.MARINE, 4, 4);
        new MarineLoadout(UnitRole.COMBATANT, null, WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID),
                SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID), 0).seedInto(spec);

        long marine = sim.spawn(spec);
        assertTrue(sim.world().hasSecondaryWeapon(marine));
        assertEquals(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID),
                sim.world().specialEquipment(marine));
        assertEquals(0, sim.world().secondaryAmmo(marine));
    }

    @Test
    void infantryIsNotAContactDemolitionTarget() {
        BattleSimulation sim = openArena(12, 12);
        long marine = carrier(sim, 4, 5);
        sim.spawn(new EntitySpec("soft", Faction.DEFENDER, UnitType.MARINE, 5, 5));

        assertFalse(InfantryUnitPrep.tryOpportunitySpecial(marine, sim));
    }

    @Test
    void interruptedPlantCostsNoCooldownAndReleasesItsReservation() {
        BattleSimulation sim = openArena(20, 12);
        long marine = carrier(sim, 4, 5);
        long target = turret(sim, 5, 5);
        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(marine, sim));

        sim.world().setCellPos(target, 12, 5);
        int ticks = (int) Math.ceil(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID).aimDuration()
                / BattleSimulation.TICK_DT) + 1;
        for (int i = 0; i < ticks; i++) {
            InfantryUnitPrep.tickAimAndShortCircuit(marine, sim);
        }

        assertTrue(sim.satchelCharges().activeCharges().isEmpty());
        assertEquals(0f, sim.world().secondaryCooldownTimer(marine), EPS);
        sim.world().setCellPos(target, 5, 5);
        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(marine, sim),
                "the failed plant must release its target reservation");
    }

    @Test
    void oneTargetAcceptsOnlyOnePlanterReservation() {
        BattleSimulation sim = openArena(12, 12);
        long first = carrier(sim, 4, 5);
        long second = carrier(sim, 5, 4);
        long target = turret(sim, 5, 5);

        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(first, sim));
        assertFalse(InfantryUnitPrep.tryOpportunitySpecial(second, sim));
        assertEquals(target, sim.world().secondaryAimTargetId(first));
    }

    @Test
    void successfulPlantStartsCooldownFollowsTargetAndUsesFriendlyFire() {
        BattleSimulation sim = openArena(20, 12);
        long marine = carrier(sim, 4, 5);
        long target = turret(sim, 5, 5);
        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(marine, sim));

        int ticks = (int) Math.ceil(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID).aimDuration()
                / BattleSimulation.TICK_DT) + 1;
        for (int i = 0; i < ticks; i++) {
            InfantryUnitPrep.tickAimAndShortCircuit(marine, sim);
        }

        assertEquals(1, sim.satchelCharges().activeCharges().size());
        assertEquals(0, sim.world().secondaryAmmo(marine));
        SatchelChargeSpec spec = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID).satchelChargeSpec();
        assertEquals(spec.cooldownSeconds(),
                sim.world().secondaryCooldownTimer(marine), EPS);

        sim.world().setCellPos(target, 10, 5);
        long friendly = sim.spawn(new EntitySpec("friendly", Faction.MARINE,
                UnitType.MARINE, 10, 6));
        sim.satchelCharges().tick(BattleSimulation.TICK_DT, sim);
        assertEquals(sim.world().x(target),
                sim.satchelCharges().activeCharges().get(0).x(), EPS);
        assertEquals(sim.world().y(target),
                sim.satchelCharges().activeCharges().get(0).y(), EPS);

        sim.satchelCharges().tick(spec.fuseSeconds(), sim);
        assertTrue(sim.satchelCharges().activeCharges().isEmpty());
        assertFalse(sim.world().isAlive(target), "the compact blast should destroy a light turret");
        assertFalse(sim.world().isAlive(friendly), "the satchel footprint deliberately has friendly fire");

        int cooldownTicks = (int) Math.ceil(spec.cooldownSeconds()
                / BattleSimulation.TICK_DT) + 1;
        for (int i = 0; i < cooldownTicks; i++) {
            InfantryUnitPrep.tickCooldowns(marine, sim.world());
        }
        long nextTarget = turret(sim, 5, 5);
        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(marine, sim),
                "the same zero-ammo kit becomes usable again after cooldown");
        assertEquals(nextTarget, sim.world().secondaryAimTargetId(marine));
    }

    @Test
    void friendlyUnitsRouteOutOfAKnownChargeFootprint() {
        BattleSimulation sim = openArena(20, 20);
        long planter = carrier(sim, 8, 8);
        long target = turret(sim, 9, 8);
        long friendly = sim.spawn(new EntitySpec("friendly", Faction.MARINE,
                UnitType.MARINE, 8, 9));
        SatchelChargeSpec spec = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID).satchelChargeSpec();
        assertTrue(sim.satchelCharges().tryReserve(planter, target));
        assertTrue(sim.satchelCharges().plant(planter, target, Faction.MARINE,
                sim.world().x(target), sim.world().y(target), spec));

        assertTrue(SatchelTactics.evadeFriendlyCharge(friendly, sim));
        assertTrue(sim.world().path(friendly).length > 0,
                "a friendly inside the danger ring receives an escape path");
    }

    @Test
    void generatedSatchelAssetsHaveRealTransparency() throws Exception {
        for (Path path : new Path[]{
                Path.of("mod/graphics/ui/armory/special-satchel-charge.png"),
                Path.of("mod/graphics/battle/fx/satchel-charge-armed.png")}) {
            BufferedImage image = ImageIO.read(path.toFile());
            assertNotNull(image, path + " must decode");
            assertTrue(image.getColorModel().hasAlpha(), path + " must carry alpha");
            boolean transparent = false;
            boolean visible = false;
            for (int y = 0; y < image.getHeight(); y += 8) {
                for (int x = 0; x < image.getWidth(); x += 8) {
                    int alpha = image.getRGB(x, y) >>> 24;
                    transparent |= alpha == 0;
                    visible |= alpha > 0;
                }
            }
            assertTrue(transparent, path + " must include transparent background pixels");
            assertTrue(visible, path + " must include visible equipment pixels");
        }
    }
}
