package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.infantry.InfantryUnitPrep;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The worn interceptor: switched on rather than set down, so it costs its carrier no halt, covers
 * wherever they stand, and runs dry without taking them with it.
 */
class CarriedPointDefenceTest {

    private static final String PACK_ID = SpecialEquipmentRegistry.POINT_DEFENCE_PACK_ID;
    private static final String ORDNANCE_WEAPON_ID = "weapon.field-mortar";

    @Test
    void thePackSwitchesOnWithoutAChannelOrAnEntityAndCoversWhereverItsCarrierStands() {
        BattleSimulation sim = openArena(40, 20);
        long carrier = carrier(sim, 10, 10);
        int units = sim.liveUnitCount();
        queueWarhead(sim, 14f, 10.5f, 10.5f, 10.5f, 1.0f);

        InfantryUnitPrep.tryOpportunitySpecial(carrier, sim);
        assertEquals(0f, sim.world().secondaryActionTimer(carrier), "no placement channel halts the carrier");
        assertEquals(pack().startingAmmo() - 1, sim.world().secondaryAmmo(carrier));
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(units, sim.liveUnitCount(), "a pack is worn, not a new thing on the field");
        List<PointDefenseService.EmplacementView> live = sim.pointDefense().activeEmplacements();
        assertEquals(1, live.size());
        assertEquals(carrier, live.get(0).entityId());

        sim.world().setPos(carrier, 25.5f, 12.5f);
        sim.advance(BattleSimulation.TICK_DT);
        PointDefenseService.EmplacementView moved = sim.pointDefense().activeEmplacements().get(0);
        assertEquals(sim.world().x(carrier), moved.x(), 1e-3f);
        assertEquals(sim.world().y(carrier), moved.y(), 1e-3f);
        assertTrue(sim.pointDefense().isCovered(Faction.MARINE, 25.5f, 12.5f));
        assertTrue(!sim.pointDefense().isCovered(Faction.MARINE, 10.5f, 10.5f), "the ground it left is bare");
    }

    @Test
    void anInterceptorIsAMissileInFlightAndDecidesTheRoundOnlyWhenItArrives() {
        BattleSimulation sim = openArena(40, 20);
        long carrier = carrier(sim, 10, 10);
        sim.pointDefense().queuePlacement(carrier, Faction.MARINE, 10, 10, pack().deployableEmplacementSpec());
        sim.advance(BattleSimulation.TICK_DT);
        Projectile round = queueWarhead(sim, 19f, 10.5f, 10.5f, 10.5f, 2.0f);

        sim.advance(BattleSimulation.TICK_DT);
        assertEquals(1, sim.telemetry().ordnanceEngaged(carrier), "the pack launches at the round");
        assertTrue(!round.intercepted, "launching decides nothing yet");
        Projectile missile = sim.getActiveProjectiles().stream()
                .filter(p -> p != round).findFirst().orElseThrow();
        assertEquals(TurretCatalogRegistry.requireStructure(pack().deployableEmplacementSpec().structureId())
                .mount.weapon.id, missile.sourceWeaponId);
        assertTrue(!missile.pointDefenseTarget, "an interceptor is not itself something to shoot down");

        float flight = missile.totalFlightTime;
        for (int t = 0; t < Math.round(flight / BattleSimulation.TICK_DT) + 2; t++) sim.advance(BattleSimulation.TICK_DT);
        assertEquals(sim.telemetry().ordnanceIntercepted(carrier) == 1, round.intercepted,
                "on arrival the roll made at launch is applied to the round");
    }

    @Test
    void anEmptyPackRetiresAndLeavesItsCarrierStanding() {
        BattleSimulation sim = openArena(40, 20);
        long carrier = carrier(sim, 10, 10);
        sim.pointDefense().queuePlacement(carrier, Faction.MARINE, 10, 10, pack().deployableEmplacementSpec());
        sim.advance(BattleSimulation.TICK_DT);

        StructureDef structure = TurretCatalogRegistry.requireStructure(pack().deployableEmplacementSpec().structureId());
        WeaponDef gun = structure.mount.weapon;
        float flight = gun.cooldown * 1.5f;
        for (int i = 0; i < structure.mount.ammoCapacity + 2; i++) {
            queueWarhead(sim, 12f, 10.5f, 10.5f, 10.5f, flight);
            for (int t = 0; t < Math.round(flight / BattleSimulation.TICK_DT) + 3; t++) {
                sim.advance(BattleSimulation.TICK_DT);
            }
        }

        assertEquals(structure.mount.ammoCapacity, sim.telemetry().ordnanceEngaged(carrier));
        assertTrue(sim.pointDefense().activeEmplacements().isEmpty(), "a dry pack stops covering");
        assertTrue(sim.world().isAlive(carrier), "running the magazine dry does not kill the wearer");
    }

    private static SpecialEquipmentDef pack() {
        return SpecialEquipmentRegistry.require(PACK_ID);
    }

    private static long carrier(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("carrier", Faction.MARINE, UnitType.MARINE, x, y)
                .role(UnitRole.STRUCTURE)
                .specialEquipment(pack(), pack().startingAmmo()));
    }

    private static BattleSimulation openArena(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(width, height));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /** A hostile round whose payload cannot hurt anyone: what is measured here is the mount, not the blast. */
    private static Projectile queueWarhead(BattleSimulation sim, float fromX, float fromY,
                                           float toX, float toY, float flightTime) {
        WeaponDef weapon = WeaponRegistry.require(ORDNANCE_WEAPON_ID);
        PendingDetonation payload = new PendingDetonation(0L, toX, toY, flightTime,
                0f, 0f, 0f, 0, Faction.DEFENDER, /*aerialDelivery*/ false);
        Projectile round = new Projectile(fromX, fromY, toX, toY, weapon.boostRamp, 0f, Faction.DEFENDER,
                false, flightTime, payload, weapon.id, weapon.pointDefenseTarget);
        sim.queueProjectile(round);
        return round;
    }
}
