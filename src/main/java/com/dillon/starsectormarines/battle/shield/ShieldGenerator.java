package com.dillon.starsectormarines.battle.shield;

import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * Spawn-spec factory for a shield generator: a multi-cell structure that
 * projects a {@link DomeSpec dome} and does nothing else.
 *
 * <p>Sibling to {@code MapTurret} and {@code DeployedEmplacement}: it mints a
 * {@link UnitType#TURRET} entity from a {@link StructureDef}, so it is targeted,
 * damaged, drawn as a pad, and demolished through the ordinary paths. The role is
 * {@link UnitRole#STRUCTURE}, so it has no aim loop and fires nothing; the
 * mount in its definition exists only because every structure names one.
 *
 * <p>The dome is registered with {@link ShieldDomeService} when the entity
 * spawns ({@code BattleSimulation.spawn}), not here, so a generator spawned any
 * other way gets its dome too.
 */
public final class ShieldGenerator {

    public static final String STRUCTURE_ID = "structure.shield-generator";

    private ShieldGenerator() {}

    /**
     * The spawn spec for a generator of {@code holder}'s side anchored at the
     * cell {@code (cellX, cellY)}, the centre of its footprint.
     */
    public static EntitySpec create(String name, Faction holder, int cellX, int cellY) {
        StructureDef structure = TurretCatalogRegistry.requireStructure(STRUCTURE_ID);
        return new EntitySpec(name, holder, UnitType.TURRET, cellX, cellY)
                .health(structure.maxStructure)
                .armor(structure.armorCapacity, structure.armorRating)
                .attackDamage(0f)
                .attackRange(0f)
                .accuracy(0f)
                .moveSpeed(0f)
                .visionRange(0f)
                .role(UnitRole.STRUCTURE)
                .turretStructureId(structure.id);
    }
}
