package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * Spawn-spec factory for the core of a fort aspect: a multi-cell structure that does nothing but exist, so it can be
 * shot, captured around and destroyed. Sibling to {@code ShieldGenerator}: a {@link UnitType#TURRET} entity with the
 * {@link UnitRole#STRUCTURE} role, so it has no aim loop and fires nothing, and it dies and opens as rubble through
 * the ordinary demolition path.
 */
public final class FortAspectCore {

    private FortAspectCore() {}

    /** The spawn spec for a core of {@code structureId} held by {@code holder}, anchored at its footprint centre. */
    public static EntitySpec create(String name, Faction holder, String structureId, int cellX, int cellY) {
        StructureDef structure = TurretCatalogRegistry.requireStructure(structureId);
        if (!structure.isFortAspectCore()) {
            throw new IllegalArgumentException("structure '" + structureId + "' is not a fort aspect core");
        }
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
