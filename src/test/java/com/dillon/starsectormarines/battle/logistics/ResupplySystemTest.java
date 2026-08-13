package com.dillon.starsectormarines.battle.logistics;

import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResupplySystemTest {

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        return new BattleSimulation(grid, new CellTopology(20, 20));
    }

    @Test
    public void finiteCacheRestocksRealInfantryAndMechAmmoPools() {
        BattleSimulation sim = openSim();
        long marine = sim.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 10, 10)
                .secondary(MarineSecondary.ROCKET_LAUNCHER, 0));
        long mech = sim.spawn(new EntitySpec("mech", Faction.MARINE, UnitType.HEAVY_MECH, 11, 10));
        MechLoadoutComponent loadout = MechLoadoutComponent.defaultLoadout(MechRole.ARMORED_SUPPORT);
        loadout.srmAmmoSalvos = 0;
        loadout.lrmAmmoSalvos = 0;
        sim.world().attachMechLoadout(mech, loadout);

        ResupplyService service = new ResupplyService();
        ResupplyCache cache = new ResupplyCache(10, 10, Faction.MARINE, 12);
        service.add(cache);
        ResupplySystem system = new ResupplySystem(service, sim.getRoster());

        for (int i = 0; i < 12; i++) {
            system.tick(ResupplySystem.TRANSFER_INTERVAL_SECONDS);
        }

        assertEquals(MarineSecondary.ROCKET_LAUNCHER.startingAmmo,
                sim.world().secondaryAmmo(marine));
        assertEquals(MechLoadoutComponent.DEFAULT_SRM_AMMO_SALVOS, loadout.srmAmmoSalvos);
        assertEquals(MechLoadoutComponent.DEFAULT_LRM_AMMO_SALVOS, loadout.lrmAmmoSalvos);
        assertTrue(cache.depleted());
    }

    @Test
    public void nearbyEnemyContestsAndPausesTransfers() {
        BattleSimulation sim = openSim();
        long marine = sim.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 10, 10)
                .secondary(MarineSecondary.ROCKET_LAUNCHER, 0));
        long enemy = sim.spawn(new EntitySpec("enemy", Faction.DEFENDER, UnitType.MILITIA, 11, 10));
        ResupplyService service = new ResupplyService();
        ResupplyCache cache = new ResupplyCache(10, 10, Faction.MARINE, 3);
        service.add(cache);
        ResupplySystem system = new ResupplySystem(service, sim.getRoster());

        system.tick(ResupplySystem.TRANSFER_INTERVAL_SECONDS);
        assertTrue(cache.contested);
        assertEquals(0, sim.world().secondaryAmmo(marine));
        assertEquals(3, cache.stock);

        sim.releaseFromRegistry(enemy);
        system.tick(ResupplySystem.TRANSFER_INTERVAL_SECONDS);
        assertFalse(cache.contested);
        assertEquals(1, sim.world().secondaryAmmo(marine));
        assertEquals(2, cache.stock);
    }
}
