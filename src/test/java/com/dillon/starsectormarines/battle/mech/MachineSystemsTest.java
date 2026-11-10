package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a machine's compartment systems do at the drop: a liner's structure, a ready rack's rounds. */
class MachineSystemsTest {

    private static final MachineSystems READY_RACK = new MachineSystems(1f, 1.5f);

    @Test
    void aReadyRackDeepensAFiniteRackAndStartsItFull() {
        MechWeaponComponent srm = MechWeaponComponent.SRM_5;
        assertTrue(srm.ammoCapacity > 0, "the SRM rack is finite");
        MechWeaponMount mount = new MechWeaponMount(MechMountSlot.LEFT_SHOULDER, srm);
        mount.fitReadyRack(READY_RACK);
        int deeper = Math.max(1, Math.round(srm.ammoCapacity * 1.5f));
        assertEquals(deeper, mount.capacity());
        assertEquals(deeper, mount.ammo, "it drops full");
        assertTrue(mount.full());
    }

    @Test
    void aReadyRackLengthensARotaryGunsBeltAndLeavesAnUnlimitedMountUnlimited() {
        MechWeaponMount gun = new MechWeaponMount(MechMountSlot.ARMS, MechWeaponComponent.CHAINGUN);
        int belt = gun.rotary.beltRounds();
        int capacity = gun.capacity();
        gun.fitReadyRack(READY_RACK);
        assertEquals(Math.round(belt * 1.5f), gun.rotary.beltRounds());
        assertEquals(capacity < 0 ? capacity : Math.round(capacity * 1.5f), gun.capacity(),
                "a mount with no limit keeps none");
    }

    @Test
    void aSpallLinerGivesTheMechAFifthMoreStructure() {
        MechVariant variant = MechVariant.WARDEN;
        MechFrame stock = MechFrame.stock(variant);
        MechFrame lined = new MechFrame(stock.armorCapacity(), stock.moveSpeed(), stock.fluxCapacity(),
                stock.dissipationPerSecond(), new MachineSystems(1.2f, 1f));
        EntitySpec bare = deploy(variant, stock);
        EntitySpec spec = deploy(variant, lined);
        assertEquals(bare.maxHp * 1.2f, spec.maxHp, 1e-3f);
        assertEquals(spec.maxHp, spec.hp, 1e-3f, "it drops whole");
    }

    private static EntitySpec deploy(MechVariant variant, MechFrame frame) {
        MechDeploymentSpec deployment = new MechDeploymentSpec(variant, null, null, null, null, null, null, null,
                null, frame, null, null, null);
        return deployment.applyTo(new EntitySpec("mech", Faction.MARINE, UnitType.HEAVY_MECH, 0, 0));
    }

    @Test
    void aCrawlersStockFrameCarriesItsShieldEmitterAndTheHostsEnrolIt() {
        MachineSystems crawler = MechFrame.stock(MechVariant.CRAWLER).systems();
        assertTrue(crawler.shieldEmitter(), "the crawler's compartment brings its shield as built");
        assertFalse(MechFrame.stock(MechVariant.HOUND).systems().shieldEmitter());
        assertTrue(MechFrame.builtIn(MechVariant.CRAWLER, READY_RACK).shieldEmitter(), "a refit keeps it");
        MachineHosts hosts = new MachineHosts();
        hosts.register(7L, crawler);
        hosts.register(8L, MechFrame.stock(MechVariant.HOUND).systems());
        assertTrue(hosts.systems(7L).shieldEmitter(), "a shield alone is a system the field hosts");
        assertEquals(MachineSystems.NONE, hosts.systems(8L));
    }
}
