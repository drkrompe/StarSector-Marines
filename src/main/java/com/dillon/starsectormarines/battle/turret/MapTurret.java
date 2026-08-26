package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * Config + factory for a bolted-down static defense described by a
 * {@link StructureDef}
 * mounted on a single non-walkable map cell. {@link #create} returns a plain
 * {@code Entity} of type {@link UnitType#TURRET} so it slots into existing
 * code paths for free: target acquisition, line-of-sight, the firing pipeline,
 * the deaths-this-frame list, the renderer's unit pass. The differences from a
 * mobile combatant are narrow — immobile, custom firing arc, separate sprite
 * path — and isolated behind {@link UnitRole#TURRET} dispatch and the
 * {@code UnitType.isTurret()} classification gate in the renderer/scoring
 * type-tag sites.
 *
 * <p>Stats come from the installed structure definition at construction; the mount cell is
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
 * {@link EntitySpec#turretStructureId}. See
 * {@code ecs-nouns.md}.
 */
public final class MapTurret {

    private MapTurret() {}

    /**
     * Builds a fresh turret {@code Entity} of {@code structureId} at
     * {@code (cellX, cellY)}. Seeds the {@code Entity}'s Group-S stats from
     * its installed definition (rather than baking them into {@link UnitType#TURRET},
     * which stays a zero-base placeholder) plus {@link EntitySpec#turretStructureId}
     * (consumed by {@code UnitRosterService.adopt} into the
     * {@code TURRET_STATE} component iff {@code type.isTurret()}); the caller
     * still owns handing the result to {@code sim.spawn}/{@code queueSpawn}.
     */
    public static EntitySpec create(String id, Faction faction, String structureId, int cellX, int cellY) {
        StructureDef structure = TurretCatalogRegistry.requireStructure(structureId);
        var weapon = structure.mount.weapon;
        return new EntitySpec(id, faction, UnitType.TURRET, cellX, cellY)
                .health(structure.maxStructure)
                .armor(structure.armorPool, structure.armorRating)
                .attackDamage(weapon.damage)
                .attackRange(weapon.range)
                .attackCooldown(weapon.cooldown)
                .accuracy(weapon.accuracy)
                .moveSpeed(0f)
                .role(UnitRole.TURRET)
                .turretStructureId(structure.id);
    }
}
