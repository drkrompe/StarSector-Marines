package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * Spawn-spec factory for a carrier-placed emplacement. Sibling to
 * {@code MapTurret}: both mint a {@link UnitType#TURRET} entity from a
 * {@link StructureDef}, so both inherit target acquisition <em>against</em>
 * them, the durability pipeline, the whole-sprite render pass, the durability
 * bar, and the death cascade for free.
 *
 * <p>The one difference from a bolted-down map turret is the role. A map turret
 * takes {@link UnitRole#TURRET} and runs the aim/fire loop against actors; a
 * placed emplacement takes {@link UnitRole#STRUCTURE}, whose per-tick dispatch
 * is a no-op. That is the mechanical expression of the standing rule that an
 * emplacement is not a second soldier: with no behaviour dispatch it cannot
 * acquire an infantry target, cannot reposition, and cannot be pulled into a
 * squad plan. Everything it actually does happens in {@link PointDefenseService},
 * which walks in-flight ordnance rather than the roster.
 *
 * <p>Vision is zeroed for the same reason: a placed pod refuses ordnance, it
 * does not spot for the side that placed it.
 *
 * <p>The mount cell is deliberately <em>not</em> sealed. Map turrets are stamped
 * onto non-walkable cells before the sim exists; a marine sets this down on the
 * floor they are standing on, and sealing that cell mid-battle would both trap
 * the carrier and close a navigation edge under existing paths.
 */
public final class DeployedEmplacement {

    private DeployedEmplacement() {}

    /**
     * Builds the spawn spec for one placement of {@code structureId} at cell
     * {@code (cellX, cellY)}. The caller owns handing the result to
     * {@code sim.spawn}.
     */
    public static EntitySpec create(String name, Faction faction, String structureId,
                                    int cellX, int cellY) {
        StructureDef structure = requireBounded(structureId);
        return new EntitySpec(name, faction, UnitType.TURRET, cellX, cellY)
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

    /**
     * Resolves the structure and refuses one whose mount declares an unlimited
     * feed. A bolted-down map turret may draw from the compound's magazine
     * forever; a pod a marine carried in has exactly the rounds that came with
     * it, and an emplacement that can never run dry is a standing ban on a
     * weapon class rather than a capability. Fails loud at placement rather
     * than silently granting infinite engagements.
     */
    public static StructureDef requireBounded(String structureId) {
        StructureDef structure = TurretCatalogRegistry.requireStructure(structureId);
        if (structure.mount.ammoCapacity <= 0) {
            throw new IllegalStateException("Deployable structure '" + structure.id
                    + "' uses mount '" + structure.mount.id
                    + "', which declares an unlimited static feed; a carried emplacement"
                    + " must declare a positive mount ammoCapacity");
        }
        return structure;
    }
}
