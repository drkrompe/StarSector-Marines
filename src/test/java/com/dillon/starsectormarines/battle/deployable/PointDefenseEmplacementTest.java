package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
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
import com.dillon.starsectormarines.marine.DeployableEmplacementSpec;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural coverage for the first deployable, driving a real
 * {@link BattleSimulation}. Every expected value is derived from the installed
 * registries rather than restated, so a balance edit retunes the emplacement
 * without touching this file.
 */
class PointDefenseEmplacementTest {

    private static final float EPS = 1e-3f;
    /** An LRM warhead is the canonical thing this exists to refuse. */
    private static final String ORDNANCE_WEAPON_ID = "weapon.mech-lrm-artillery";
    /** A chaingun round travels the same map and must be untouched by point defence. */
    private static final String DIRECT_FIRE_WEAPON_ID = "weapon.mech-chaingun";

    private static SpecialEquipmentDef pod() {
        return SpecialEquipmentRegistry.require(
                SpecialEquipmentRegistry.POINT_DEFENCE_EMPLACEMENT_ID);
    }

    private static DeployableEmplacementSpec podSpec() {
        return pod().deployableEmplacementSpec();
    }

    private static StructureDef podStructure() {
        return TurretCatalogRegistry.requireStructure(podSpec().structureId());
    }

    private static BattleSimulation openArena(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(width, height));
        // No mission terminal evaluation: an arena with one side in it would
        // otherwise be won on tick one and stop advancing.
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /**
     * The marine who carries and places the pod. Given the inert structure role
     * so the measurement is about the emplacement rather than about whatever
     * the carrier's AI decided to do this second.
     */
    private static long marine(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("marine-" + sim.liveUnitCount(),
                Faction.MARINE, UnitType.MARINE, x, y)
                .role(UnitRole.STRUCTURE)
                .specialEquipment(pod(), pod().startingAmmo()));
    }

    /** A stationary body that stays exactly where the warhead was aimed. */
    private static long standingTarget(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("target-" + sim.liveUnitCount(),
                Faction.MARINE, UnitType.RANGE_TARGET, x, y)
                .role(UnitRole.STRUCTURE));
    }

    /** Places the pod directly, bypassing the carrier's opportunity gate. */
    private static void placePod(BattleSimulation sim, long carrier, int cellX, int cellY) {
        sim.pointDefense().queuePlacement(carrier, sim.identity().faction(carrier),
                cellX, cellY, podSpec());
        sim.advance(BattleSimulation.TICK_DT);
    }

    /**
     * Queues one hostile warhead already in flight from {@code (fromX, fromY)}
     * to {@code (toX, toY)}, carrying the real detonation payload the authoring
     * weapon would deliver.
     */
    private static Projectile queueWarhead(BattleSimulation sim, String weaponId,
                                           float fromX, float fromY,
                                           float toX, float toY, float flightTime) {
        WeaponDef weapon = WeaponRegistry.require(weaponId);
        PendingDetonation payload = new PendingDetonation(0L, toX, toY, flightTime,
                weapon.aoeRadius, weapon.damage, weapon.penetration, weapon.wallDamage,
                Faction.DEFENDER, /*aerialDelivery*/ false);
        Projectile p = new Projectile(fromX, fromY, toX, toY,
                weapon.boostRamp, /*arcHeight*/ 0f, Faction.DEFENDER,
                /*aerialDelivery*/ false, flightTime, payload, weapon.id,
                weapon.pointDefenseTarget);
        sim.queueProjectile(p);
        return p;
    }

    private static void advanceSeconds(BattleSimulation sim, float seconds) {
        int ticks = Math.max(1, Math.round(seconds / BattleSimulation.TICK_DT));
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    @Test
    void authoredOrdnanceDeclaresItselfEngageableAndDirectFireDoesNot() {
        assertTrue(WeaponRegistry.require(ORDNANCE_WEAPON_ID).pointDefenseTarget,
                ORDNANCE_WEAPON_ID + " must opt in to point defence in its own data");
        assertFalse(WeaponRegistry.require(DIRECT_FIRE_WEAPON_ID).pointDefenseTarget,
                "a direct-fire gun is not ordnance and must not be engageable");
    }

    @Test
    void warheadInsideTheRadiusIsRemovedWithoutDetonatingOrDealingDamage() {
        BattleSimulation sim = openArena(40, 20);
        long carrier = marine(sim, 10, 10);
        long victim = standingTarget(sim, 12, 10);
        placePod(sim, carrier, 10, 10);

        float victimHpBefore = sim.world().hp(victim);
        // Launch from just inside the radius so the round is engageable on the
        // very first pass, and aim it squarely at the victim.
        queueWarhead(sim, ORDNANCE_WEAPON_ID, 13f, 10.5f, 12.5f, 10.5f, 0.5f);
        advanceSeconds(sim, 1.0f);

        assertTrue(sim.getActiveProjectiles().isEmpty(), "the engaged round must be gone");
        assertEquals(victimHpBefore, sim.world().hp(victim), EPS,
                "an intercepted round detonates on nobody");
        assertEquals(1, sim.telemetry().ordnanceIntercepted(carrier),
                "the interception is credited to the marine who placed the pod");
        assertEquals(0f, sim.telemetry().damageDealt(carrier), EPS,
                "interception is not damage and must not be credited as any");
    }

    @Test
    void warheadOutsideTheRadiusLandsNormally() {
        BattleSimulation sim = openArena(60, 20);
        long carrier = marine(sim, 5, 10);
        placePod(sim, carrier, 5, 10);

        float radius = podStructure().mount.weapon.range;
        // Whole flight stays well clear of the bubble.
        float lane = 5.5f + radius * 3f;
        long victim = standingTarget(sim, (int) lane, 10);
        float victimHpBefore = sim.world().hp(victim);
        queueWarhead(sim, ORDNANCE_WEAPON_ID, lane + 6f, 10.5f, lane + 0.5f, 10.5f, 0.5f);
        advanceSeconds(sim, 1.0f);

        assertEquals(0, sim.telemetry().ordnanceIntercepted(carrier),
                "a round that never entered the radius was never engageable");
        assertTrue(sim.world().hp(victim) < victimHpBefore,
                "an unengaged warhead still detonates");
    }

    @Test
    void aBurstTighterThanTheEngagementRatePartiallyGetsThrough() {
        BattleSimulation sim = openArena(40, 20);
        long carrier = marine(sim, 10, 10);
        placePod(sim, carrier, 10, 10);

        WeaponDef gun = podStructure().mount.weapon;
        // One salvo, every round already inside the bubble, all arriving well
        // inside a single engagement interval.
        int salvo = 4;
        float flightTime = gun.cooldown * 0.5f;
        for (int i = 0; i < salvo; i++) {
            queueWarhead(sim, ORDNANCE_WEAPON_ID, 12f + i * 0.1f, 10.5f,
                    10.5f, 10.5f, flightTime);
        }
        advanceSeconds(sim, flightTime + BattleSimulation.TICK_DT * 3f);

        int intercepted = sim.telemetry().ordnanceIntercepted(carrier);
        assertTrue(intercepted >= 1, "the mount engages what it can reach");
        assertTrue(intercepted < salvo,
                "a salvo tighter than the engagement interval must saturate the mount");
    }

    @Test
    void theMagazineIsFiniteAndTheMountRetiresWhenItRunsDry() {
        BattleSimulation sim = openArena(40, 20);
        long carrier = marine(sim, 10, 10);
        placePod(sim, carrier, 10, 10);

        WeaponDef gun = podStructure().mount.weapon;
        int capacity = podStructure().mount.ammoCapacity;
        // Feed rounds in one at a time, each spaced past the engagement
        // interval, for more shots than the magazine holds.
        float flightTime = gun.cooldown * 1.5f;
        for (int i = 0; i < capacity + 3; i++) {
            queueWarhead(sim, ORDNANCE_WEAPON_ID, 12f, 10.5f, 10.5f, 10.5f, flightTime);
            advanceSeconds(sim, flightTime + BattleSimulation.TICK_DT * 3f);
        }

        assertEquals(capacity, sim.telemetry().ordnanceIntercepted(carrier),
                "the mount engages exactly its authored magazine and no more");
        assertTrue(sim.pointDefense().activeEmplacements().isEmpty(),
                "a dry mount retires rather than lingering as an inert obstacle");
    }

    @Test
    void theEmplacementExpiresAndStopsIntercepting() {
        BattleSimulation sim = openArena(40, 20);
        long carrier = marine(sim, 10, 10);
        placePod(sim, carrier, 10, 10);
        assertEquals(1, sim.pointDefense().activeEmplacements().size());

        advanceSeconds(sim, podSpec().lifetimeSeconds() + 0.5f);
        assertTrue(sim.pointDefense().activeEmplacements().isEmpty(),
                "the pod must run out of time on its own");

        long victim = standingTarget(sim, 10, 10);
        float victimHpBefore = sim.world().hp(victim);
        queueWarhead(sim, ORDNANCE_WEAPON_ID, 13f, 10.5f, 10.5f, 10.5f, 0.4f);
        advanceSeconds(sim, 1.0f);

        assertEquals(0, sim.telemetry().ordnanceIntercepted(carrier),
                "an expired emplacement engages nothing");
        assertTrue(sim.world().hp(victim) < victimHpBefore,
                "with the pod gone the warhead lands");
    }

    @Test
    void directFireRoundsAreUnaffected() {
        BattleSimulation sim = openArena(40, 20);
        long carrier = marine(sim, 10, 10);
        long victim = standingTarget(sim, 11, 10);
        placePod(sim, carrier, 10, 10);

        float victimHpBefore = sim.world().hp(victim);
        Projectile bullet = queueWarhead(sim, DIRECT_FIRE_WEAPON_ID,
                12f, 10.5f, 11.5f, 10.5f, 0.3f);
        assertFalse(bullet.pointDefenseTarget);
        advanceSeconds(sim, 1.0f);

        assertEquals(0, sim.telemetry().ordnanceIntercepted(carrier),
                "point defence engages ordnance, never bullets");
        assertTrue(sim.world().hp(victim) < victimHpBefore,
                "the direct-fire round lands exactly as it would have");
    }

    @Test
    void aMarineWithNoEmplacementGetsNoInterception() {
        BattleSimulation sim = openArena(40, 20);
        long carrier = marine(sim, 10, 10);
        long victim = standingTarget(sim, 11, 10);

        float victimHpBefore = sim.world().hp(victim);
        queueWarhead(sim, ORDNANCE_WEAPON_ID, 13f, 10.5f, 11.5f, 10.5f, 0.4f);
        advanceSeconds(sim, 1.0f);

        assertEquals(0, sim.telemetry().ordnanceIntercepted(carrier));
        assertTrue(sim.world().hp(victim) < victimHpBefore,
                "carrying the kit is not the same as having placed it");
    }

    @Test
    void thePlacedEmplacementIsAVisibleDamageableEntityThatIsNotACombatant() {
        BattleSimulation sim = openArena(40, 20);
        long carrier = marine(sim, 10, 10);
        placePod(sim, carrier, 10, 10);

        PointDefenseService.EmplacementView view =
                sim.pointDefense().activeEmplacements().get(0);
        long id = sim.resolveUnit(view.entityId());
        assertNotNull(sim.turretState().structure(id),
                "it reuses the shipped emplacement substrate, so it renders and takes fire");
        assertEquals(podStructure().maxStructure, sim.world().maxHp(id), EPS,
                "durability comes from the structure definition, not from the carried item");
        assertEquals(0f, sim.vision().visionRange(id), EPS,
                "an emplacement refuses ordnance; it does not spot for its side");
        assertEquals(0f, sim.world().attackRange(id), EPS,
                "it has no reach against actors at all");
        assertTrue(sim.getGrid().isWalkable(10, 10),
                "placing a pod must not seal the cell the carrier is standing on");
    }

    @Test
    void shootingTheEmplacementStopsIt() {
        BattleSimulation sim = openArena(40, 20);
        long carrier = marine(sim, 10, 10);
        placePod(sim, carrier, 10, 10);
        long id = sim.resolveUnit(
                sim.pointDefense().activeEmplacements().get(0).entityId());

        sim.applyExternalDamage(id, podStructure().maxStructure
                + podStructure().armorCapacity + 1f, Float.MAX_VALUE);
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.pointDefense().activeEmplacements().isEmpty());

        long victim = standingTarget(sim, 10, 10);
        float victimHpBefore = sim.world().hp(victim);
        queueWarhead(sim, ORDNANCE_WEAPON_ID, 13f, 10.5f, 10.5f, 10.5f, 0.4f);
        advanceSeconds(sim, 1.0f);

        assertEquals(0, sim.telemetry().ordnanceIntercepted(carrier),
                "a destroyed emplacement engages nothing");
        assertTrue(sim.world().hp(victim) < victimHpBefore);
    }
}
