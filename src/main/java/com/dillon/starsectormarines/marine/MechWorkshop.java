package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;

/** Atomic campaign authority for Mech Lab fabrication and fitting. */
public final class MechWorkshop {

    private final MechBay bay;
    private final FabricationResources resources;

    public MechWorkshop(MechBay bay, FabricationResources resources) {
        if (bay == null || resources == null) {
            throw new IllegalArgumentException("mech bay and fabrication resources are required");
        }
        this.bay = bay;
        this.resources = resources;
    }

    public Result fitWeapon(String mechId, MechMountSlot slot,
                            MechWeaponComponent component) {
        CampaignMech mech = bay.mechById(mechId);
        if (mech == null || slot == null || component == null) return Result.of(Status.INVALID);
        if (mech.weaponAt(slot) == component) return Result.of(Status.ALREADY_INSTALLED);
        if (!bay.canInstallWeapon(mechId, slot, component.id)) {
            return Result.of(Status.INCOMPATIBLE);
        }
        if (bay.availableWeapon(component.id) > 0) {
            return bay.installWeapon(mechId, slot, component.id)
                    ? Result.of(Status.INSTALLED_FROM_STORES) : Result.of(Status.INVALID);
        }
        MechFabricationCatalog.Recipe recipe = MechFabricationCatalog.weapon(component);
        if (recipe == null || !resources.spend(recipe.cost())) {
            return Result.of(Status.INSUFFICIENT_MATERIALS);
        }
        bay.addWeapon(component.id, 1);
        if (!bay.installWeapon(mechId, slot, component.id)) {
            throw new IllegalStateException("validated fabricated component could not be installed");
        }
        return Result.of(Status.FABRICATED_AND_INSTALLED);
    }

    public Result fabricateChassis(String squadId, MechVariant variant) {
        if (variant == null || bay.squadById(squadId) == null) return Result.of(Status.INVALID);
        if (!bay.canAddMech(squadId)) return Result.of(Status.LANCE_FULL);
        MechFabricationCatalog.Recipe recipe = MechFabricationCatalog.chassis(variant);
        if (recipe == null || !resources.spend(recipe.cost())) {
            return Result.of(Status.INSUFFICIENT_MATERIALS);
        }
        CampaignMech mech = bay.fabricateChassis(squadId, variant);
        if (mech == null) {
            throw new IllegalStateException("validated chassis could not enter its vacant gantry");
        }
        return new Result(Status.CHASSIS_FABRICATED, mech);
    }

    public enum Status {
        ALREADY_INSTALLED,
        INSTALLED_FROM_STORES,
        FABRICATED_AND_INSTALLED,
        CHASSIS_FABRICATED,
        INCOMPATIBLE,
        INSUFFICIENT_MATERIALS,
        LANCE_FULL,
        INVALID
    }

    public record Result(Status status, CampaignMech mech) {
        private static Result of(Status status) { return new Result(status, null); }
        public boolean succeeded() {
            return status == Status.ALREADY_INSTALLED
                    || status == Status.INSTALLED_FROM_STORES
                    || status == Status.FABRICATED_AND_INSTALLED
                    || status == Status.CHASSIS_FABRICATED;
        }
    }
}
