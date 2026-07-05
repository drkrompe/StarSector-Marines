package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * Config + factory for a bolted-down static defense — a {@link TurretKind}
 * mounted on a single non-walkable map cell. {@link #create} returns a plain
 * {@code Entity} of type {@link UnitType#TURRET} so it slots into existing
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
 * <p>A plain config/factory class, <b>not</b> an {@code Entity} subclass — the
 * turret's live per-instance state ({@code facingDegrees}/{@code recoilTimer}/
 * {@code kind}/{@code burstRemaining}/{@code burstTimer}/{@code burstTargetId})
 * lives in the world {@code TURRET_STATE} component (data owner
 * {@code battle.sim.TurretStateService}); {@link #create} seeds it via
 * {@link EntitySpec#turretKind}. See
 * {@code roadmap/ecs-migration/stories/identity-collapse.md} (slice B2).
 */
public final class MapTurret {

    private MapTurret() {}

    /**
     * Builds a fresh turret {@code Entity} of {@code kind} at
     * {@code (cellX, cellY)}. Seeds the {@code Entity}'s Group-S stats from
     * {@code kind} (rather than baking them into {@link UnitType#TURRET},
     * which stays a zero-base placeholder) plus {@link EntitySpec#turretKind}
     * (consumed by {@code UnitRosterService.adopt} into the
     * {@code TURRET_STATE} component iff {@code type.isTurret()}); the caller
     * still owns handing the result to {@code sim.spawn}/{@code queueSpawn}.
     */
    public static EntitySpec create(String id, Faction faction, TurretKind kind, int cellX, int cellY) {
        // TurretKind stats override the UnitType.TURRET zero-base. Doing it here
        // (rather than in UnitType) keeps the per-kind balance in one place.
        return new EntitySpec(id, faction, UnitType.TURRET, cellX, cellY)
                .health(kind.maxHp)
                .attackDamage(kind.damage)
                .attackRange(kind.range)
                .attackCooldown(kind.cooldown)
                .accuracy(kind.accuracy)
                .moveSpeed(0f)
                .role(UnitRole.TURRET)
                .turretKind(kind);
    }
}
