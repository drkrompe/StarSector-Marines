package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * Config + factory for a bolted-down static defense — a {@link TurretKind}
 * mounted on a single non-walkable map cell. {@link #create} returns a plain
 * {@link Entity} of type {@link UnitType#TURRET} so it slots into existing
 * code paths for free: target acquisition, line-of-sight, the firing pipeline,
 * the deaths-this-frame list, the renderer's unit pass. The differences from a
 * mobile combatant are narrow — immobile, custom firing arc, separate sprite
 * path — and isolated behind {@link UnitRole#TURRET} dispatch and the
 * {@code UnitType.isTurret()} classification gate in the renderer/scoring
 * type-tag sites.
 *
 * <p>Stats come from {@link TurretKind} at construction; the mount cell is
 * flagged non-walkable on the {@link com.dillon.starsectormarines.battle.nav.NavigationGrid}
 * by {@link BattleSetup} before the sim is built (same pattern vehicles use).
 * On death, {@link BattleSimulation} flips the cell to walkable + rubble so
 * a destroyed turret stops blocking pathing and LOS.
 *
 * <p>A plain config/factory class, <b>not</b> an {@link Entity} subclass — the
 * turret's live per-instance state ({@code facingDegrees}/{@code recoilTimer}/
 * {@code kind}/{@code burstRemaining}/{@code burstTimer}/{@code burstTargetId})
 * lives in the world {@code TURRET_STATE} component (data owner
 * {@code battle.sim.TurretStateService}); {@link #create} seeds it via
 * {@link Entity#seedTurretKind}. See
 * {@code roadmap/ecs-migration/stories/identity-collapse.md} (slice B2).
 */
public final class MapTurret {

    private MapTurret() {}

    /**
     * Builds a fresh turret {@link Entity} of {@code kind} at
     * {@code (cellX, cellY)}. Seeds the {@code Entity}'s Group-S stats from
     * {@code kind} (rather than baking them into {@link UnitType#TURRET},
     * which stays a zero-base placeholder) plus {@link Entity#seedTurretKind}
     * (consumed by {@code UnitRosterService.allocate} into the
     * {@code TURRET_STATE} component iff {@code type.isTurret()}); the caller
     * still owns handing the result to {@code sim.addUnit}/{@code queueSpawn}.
     */
    public static Entity create(String id, Faction faction, TurretKind kind, int cellX, int cellY) {
        Entity turret = new Entity(id, faction, UnitType.TURRET, cellX, cellY);
        // TurretKind stats override the UnitType.TURRET zero-base. Doing it here
        // (rather than in UnitType) keeps the per-kind balance in one place.
        // Pre-allocate construction seed: write the seed* fields directly
        // (registry is still null, so the accessors can't route yet).
        // UnitRosterService.allocate later copies these into the SoA arrays.
        turret.seedMaxHp = kind.maxHp;
        turret.seedHp = kind.maxHp;
        turret.seedAttackDamage = kind.damage;
        turret.seedAttackRange = kind.range;
        turret.seedAttackCooldown = kind.cooldown;
        turret.seedAccuracy = kind.accuracy;
        turret.seedMoveSpeed = 0f;
        turret.seedRole = UnitRole.TURRET;
        turret.seedTurretKind = kind;
        return turret;
    }
}
