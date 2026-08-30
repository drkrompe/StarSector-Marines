package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link TurretBehavior}'s ferry between a turret's
 * {@code TURRET_STATE} component and the shared {@link TurretAim.State}
 * carrier — the behavior-relocation half of slice B2 (the {@link MapTurret}
 * dissolution). Both cases are behavior-preserving reads/writes that used to
 * live on the dissolved subclass's fields.
 */
public class TurretBehaviorTest {

    private static BattleSimulation openArena(int w, int h) {
        return openArena(w, h, BattleSimulation.DEFAULT_SEED);
    }

    private static BattleSimulation openArena(int w, int h, long seed) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, topology, seed);
    }

    @Test
    public void recoilTimerAgesEachUpdateWhenNoTargetInRange() {
        BattleSimulation sim = openArena(20, 20);
        long turret = sim.spawn(MapTurret.create("t0", Faction.DEFENDER,
                TurretCatalogRegistry.VULCAN_STRUCTURE_ID, 10, 10));

        TurretBehavior.INSTANCE.update(turret, sim);

        // Seeded to 1f (past the renderer's recoil window); one update ages it
        // by exactly one TICK_DT since nothing fired to reset it to 0.
        assertEquals(1f + BattleSimulation.TICK_DT, sim.turretState().recoilTimer(turret), 1e-4f,
                "no target in range → recoil timer just ages, doesn't reset");
    }

    @Test
    public void burstKindLatchesRemainingRoundsIntoTurretStateOnFire() {
        BattleSimulation sim = openArena(40, 40, 12345L);
        long turret = sim.spawn(MapTurret.create("t0", Faction.DEFENDER,
                TurretCatalogRegistry.VULCAN_STRUCTURE_ID, 10, 10));
        // Due north of the turret (same cellX): bearing-to-target is exactly 0°,
        // matching the turret's zero-init facingDegrees, so the fire-arc gate
        // passes on the very first update with no slew needed. Well within
        // VULCAN's 22-cell range.
        long enemy = sim.spawn(new EntitySpec("m0", Faction.MARINE, UnitType.MARINE, 10, 20));

        TurretBehavior.INSTANCE.update(turret, sim);

        long id = turret;
        var weapon = sim.turretState().weapon(id);
        assertEquals(weapon.burstCount - 1, sim.turretState().burstRemaining(id),
                "the trigger pull fires round 1; the burst pump latches the remaining rounds");
        assertEquals(weapon.burstSpacing, sim.turretState().burstTimer(id), 1e-4f);
        assertEquals(enemy, sim.turretState().burstTargetId(id),
                "the burst locks onto the acquired target's id");
        assertEquals(0f, sim.turretState().recoilTimer(id), 1e-4f,
                "firing resets the recoil timer so the renderer's slide restarts");
        assertEquals(0f, sim.turretState().facingDegrees(id), 1e-4f,
                "already aligned with the due-north bearing — no slew needed to fire");
    }

    @Test
    public void vulcanBurstAlternatesAcrossItsPosedBarrels() {
        BattleSimulation sim = openArena(40, 40, 12345L);
        long turret = sim.spawn(MapTurret.create("t0", Faction.DEFENDER,
                TurretCatalogRegistry.VULCAN_STRUCTURE_ID, 10, 10));
        sim.spawn(new EntitySpec("m0", Faction.MARINE, UnitType.MARINE, 10, 20));

        TurretBehavior.INSTANCE.update(turret, sim);
        ShotEvent first = sim.getActiveShots().get(0);
        sim.turretState().setBurstTimer(turret, 0f);
        TurretBehavior.INSTANCE.update(turret, sim);
        ShotEvent second = sim.getActiveShots().get(1);

        TurretMountDef mount = sim.turretState().structure(turret).mount;
        assertEquals(sim.world().x(turret) - mount.muzzleLateralOffsetCells,
                first.fromX, 1e-4f);
        assertEquals(sim.world().x(turret) + mount.muzzleLateralOffsetCells,
                second.fromX, 1e-4f);
        assertEquals(sim.world().y(turret) + mount.muzzleOffsetCells,
                first.fromY, 1e-4f);
        assertEquals(first.fromY, second.fromY, 1e-4f);
    }

    @Test
    public void hephaestusIsAuthoredAsTheSlowDirectHitAntiArmorCannon() {
        var cannon = TurretCatalogRegistry.requireStructure(
                TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID).mount.weapon;

        assertEquals("Hephaestus Heavy Cannon", cannon.displayName());
        assertEquals(32f, cannon.range(), 0f);
        assertEquals(117f, cannon.contactDamage, 0f);
        assertEquals(24f, cannon.contactPenetration, 0f);
        assertEquals(45f, cannon.damage(), 0f, "area payload damage");
        assertEquals(4f, cannon.penetration(), 0f, "area payload penetration");
        assertEquals(4.5f, cannon.cooldown(), 0f);
        assertEquals(0.65f, cannon.accuracy(), 0f);
        assertEquals(1.6f, cannon.aoeRadius, 0f);
        assertEquals(30, cannon.wallDamage);
        assertEquals(1.25f, cannon.wallDamageRadius, 0f);
        assertTrue(cannon.fx.hasHeavyImpact());
        assertEquals(1, cannon.burstCount());
    }

    @Test
    public void hephaestusBehaviorFiresOneHeavyCannonRoundAndEntersTheFullCooldown() {
        BattleSimulation sim = openArena(50, 50, 12345L);
        long turret = sim.spawn(MapTurret.create(
                "hephaestus", Faction.DEFENDER,
                TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID, 10, 10));
        long enemy = sim.spawn(new EntitySpec(
                "m0", Faction.MARINE, UnitType.MARINE, 10, 20));
        sim.spawn(new EntitySpec(
                "out-of-range-mech", Faction.MARINE, UnitType.HEAVY_MECH, 10, 45));

        TurretBehavior.INSTANCE.update(turret, sim);

        assertEquals(1, sim.getShotsThisFrame().size());
        ShotEvent shot = sim.getShotsThisFrame().get(0);
        assertSame(TurretCatalogRegistry.requireStructure(
                TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID), shot.turretStructureDef);
        assertTrue(shot.weaponDef().fx.hasHeavyImpact());
        assertEquals(1, sim.getInflightDetonations().size(),
                "single-shot cannon must queue its timed area payload through the turret fire path");
        PendingDetonation blast = sim.getInflightDetonations().get(0);
        assertEquals(enemy, blast.directTargetId,
                "an out-of-range armored target cannot starve the in-range contact");
        assertEquals(117f, blast.directDamage, 0f);
        assertEquals(24f, blast.directPenetration, 0f);
        assertEquals(45f, blast.damage, 0f);
        assertEquals(4f, blast.penetration, 0f);
        assertEquals(0, sim.turretState().burstRemaining(turret));
        assertEquals(4.5f, sim.world().cooldownTimer(turret), 1e-4f,
                "the cannon fires once and pays its deliberately slow cycle");
    }
}
